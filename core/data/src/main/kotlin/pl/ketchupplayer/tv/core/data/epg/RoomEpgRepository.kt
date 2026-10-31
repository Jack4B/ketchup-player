package pl.ketchupplayer.tv.core.data.epg

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.ChannelDao
import pl.ketchupplayer.tv.core.database.EpgDao
import pl.ketchupplayer.tv.core.database.EpgProgrammeEntity
import pl.ketchupplayer.tv.core.database.EpgStagingEntity
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.core.network.StreamFetcher
import pl.ketchupplayer.tv.epg.xmltv.XmlTvParser
import javax.inject.Inject

class RoomEpgRepository
    @Inject
    constructor(
        private val epg: EpgDao,
        private val channels: ChannelDao,
        private val urlProvider: EpgUrlProvider,
        private val streams: StreamFetcher,
        private val clock: Clock,
        private val prefs: DataStore<Preferences>,
    ) : EpgRepository {
        private val running = Mutex()

        override fun onAir(nowMs: Long): Flow<Map<String, EpgProgramme>> =
            epg.observeOnAir(nowMs).map { list -> list.associate { it.channelId to it.toModel() } }

        override suspend fun range(
            channelId: String,
            fromMs: Long,
            toMs: Long,
        ): List<EpgProgramme> = epg.range(channelId, fromMs, toMs).map { it.toModel() }

        override suspend fun refreshIfStale(maxAgeMs: Long): Result<EpgRefreshReport>? {
            val last = prefs.data.first()[LAST_REFRESH]
            val stale = last == null || clock.nowMs() - last > maxAgeMs
            return if (stale) refresh() else null
        }

        override suspend fun refresh(): Result<EpgRefreshReport> {
            if (!running.tryLock()) return Result.failure(IllegalStateException("Import EPG już trwa"))
            try {
                return import()
            } finally {
                running.unlock()
            }
        }

        private suspend fun import(): Result<EpgRefreshReport> {
            val urls = urlProvider.urls()
            if (urls.isEmpty()) return Result.failure(IllegalStateException("Brak włączonych źródeł EPG"))
            val matcher = EpgMatcher(channels.liveEpgKeys())
            val now = clock.nowMs()
            epg.clearStaging()
            var imported = 0
            var failed = 0
            val matched = HashSet<String>()
            val context = currentCoroutineContext()
            for (url in urls) {
                val result =
                    streams.open(
                        url,
                    ) { input -> importStream(input, matcher, now, matched) { context.isActive } }
                result.onSuccess { imported += it }.onFailure { failed++ }
            }
            if (failed == urls.size) {
                epg.clearStaging()
                return Result.failure(IllegalStateException("Nie udało się pobrać żadnego źródła EPG"))
            }
            epg.swapInStaging()
            prefs.edit { it[LAST_REFRESH] = clock.nowMs() }
            return Result.success(EpgRefreshReport(urls.size, failed, imported, matched.size))
        }

        private fun importStream(
            input: java.io.InputStream,
            matcher: EpgMatcher,
            now: Long,
            matched: MutableSet<String>,
            isActive: () -> Boolean,
        ): Int {
            val map = HashMap<String, Set<String>>()
            val batch = ArrayList<EpgStagingEntity>(BATCH)
            var count = 0
            val from = now - PAST_MS
            val to = now + HORIZON_MS
            XmlTvParser().parse(
                input,
                onChannel = { c -> matcher.match(c).takeIf { it.isNotEmpty() }?.let { map[c.id] = it } },
                onProgramme = { p ->
                    val ids = map[p.channelId]
                    if (ids != null && p.endMs > from && p.startMs < to) {
                        ids.forEach { id ->
                            batch += EpgStagingEntity(0, id, p.startMs, p.endMs, p.title, p.description, p.category)
                            matched += id
                            count++
                        }
                        if (batch.size >= BATCH) {
                            epg.insertStagingBlocking(batch.toList())
                            batch.clear()
                        }
                    }
                },
                shouldContinue = isActive,
            )
            if (batch.isNotEmpty()) epg.insertStagingBlocking(batch.toList())
            return count
        }

        private fun EpgProgrammeEntity.toModel() = EpgProgramme(channelId, startMs, endMs, title, description, category)

        private companion object {
            val LAST_REFRESH = longPreferencesKey("epg_last_refresh_ms")
            const val BATCH = 2_000
            const val PAST_MS = 3L * 60 * 60 * 1000
            const val HORIZON_MS = 72L * 60 * 60 * 1000
        }
    }
