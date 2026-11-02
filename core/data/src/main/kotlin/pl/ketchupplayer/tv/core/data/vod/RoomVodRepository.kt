package pl.ketchupplayer.tv.core.data.vod

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.catalog.ProviderFactory
import pl.ketchupplayer.tv.core.database.EpisodeEntity
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.database.VodDao
import pl.ketchupplayer.tv.core.database.VodItemEntity
import pl.ketchupplayer.tv.core.database.VodStateEntity
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind
import pl.ketchupplayer.tv.core.policy.ResumeRules
import javax.inject.Inject

class RoomVodRepository
    @Inject
    constructor(
        private val dao: VodDao,
        private val providerDao: ProviderDao,
        private val factory: ProviderFactory,
        private val clock: Clock,
        private val metadata: pl.ketchupplayer.tv.core.data.metadata.MetadataRepository,
    ) : VodRepository {
        private fun entries(items: Flow<List<VodItemEntity>>): Flow<List<VodEntry>> =
            combine(
                items,
                dao.observeState(),
                dao.observeNewSeriesIds(newSince()),
                ratings(),
            ) { list, states, fresh, rated ->
                val byId = states.associateBy { it.itemId }
                val newIds = fresh.toHashSet()
                list.map { e ->
                    val s = byId[e.id]
                    VodEntry(
                        e.toModel(),
                        s?.myList == true,
                        s?.positionMs ?: 0,
                        s?.durationMs ?: 0,
                        e.id in newIds,
                        rated[e.id] ?: pl.ketchupplayer.tv.core.data.metadata.Ratings.NONE,
                    )
                }
            }

        /** Oceny po id; zmiany spływają partiami (debounce), bo przy przewijaniu dochodzą co chwilę. */
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        private fun ratings(): Flow<Map<String, pl.ketchupplayer.tv.core.data.metadata.Ratings>> =
            flow {
                var first = true // pierwszy stan bez zwłoki, kolejne zmiany scalane
                emitAll(dao.observeMeta().debounce { if (first) 0L.also { first = false } else RATINGS_DEBOUNCE_MS })
            }.map { rows ->
                rows.filter { it.found }.associate {
                    it.itemId to
                        pl.ketchupplayer.tv.core.data.metadata
                            .Ratings(it.tmdbRating, it.imdbRating, it.rtPercent)
                }
            }

        override fun anyNewEpisodes(): Flow<Boolean> = dao.observeNewSeriesIds(newSince()).map { it.isNotEmpty() }

        private fun newSince() = clock.nowMs() - NEW_EPISODES_WINDOW_MS

        override suspend fun markSeen(seriesId: String) = dao.markSeen(seriesId)

        override fun requestMeta(itemId: String) = metadata.request(itemId)

        override suspend fun ensureMeta(itemId: String): Boolean = metadata.ensure(itemId, keyless = true)

        override suspend fun refreshInfo(itemId: String) {
            val item = dao.get(itemId)?.toModel() ?: return
            if (!item.plot.isNullOrBlank() && item.cast != null && item.backdrop != null) return
            val provider = providerDao.get(item.providerId.value)?.let { factory.create(it) } ?: return
            provider.loadVodInfo(item).onSuccess { info -> dao.insertAll(listOf(item.withInfo(info).toEntity())) }
        }

        override suspend fun enrichDurations(limit: Int): Int {
            var done = 0
            var failures = 0
            for (entity in dao.withoutDuration(limit)) {
                val item = entity.toModel()
                val provider = providerDao.get(item.providerId.value)?.let { factory.create(it) } ?: continue
                val result = provider.loadVodInfo(item)
                val info = result.getOrNull()
                when {
                    info != null -> {
                        failures = 0
                        // 0 = sprawdzono i dostawca nie podaje: nie pytamy o ten film w kółko.
                        val withDuration = item.withInfo(info).let { it.copy(durationMin = it.durationMin ?: 0) }
                        dao.insertAll(listOf(withDuration.toEntity()))
                        if ((withDuration.durationMin ?: 0) > 0) done++
                    }
                    ++failures >= MAX_FAILURES -> return done
                }
                kotlinx.coroutines.delay(ENRICH_PAUSE_MS)
            }
            return done
        }

        override fun timedMovies(limit: Int): Flow<List<VodEntry>> =
            dao.observeTimed(limit).map { list -> list.map { VodEntry(it.toModel()) } }

        override suspend fun sourceCount(itemId: String): Int {
            val item = dao.get(itemId) ?: return 1
            return dao.sourceCount(item.kind, item.normTitle, item.year).coerceAtLeast(1)
        }

        override fun entries(kind: VodKind): Flow<List<VodEntry>> = entries(dao.observeByKind(kind.name))

        override fun myList(): Flow<List<VodEntry>> =
            combine(dao.observeByKind(VodKind.MOVIE.name), dao.observeByKind(VodKind.SERIES.name), dao.observeState()) {
                movies,
                series,
                states,
                ->
                val flagged = states.filter { it.myList }.associateBy { it.itemId }
                (movies + series).filter { it.id in flagged }.map { e ->
                    val s = flagged.getValue(e.id)
                    VodEntry(e.toModel(), true, s.positionMs, s.durationMs)
                }
            }

        override fun continueWatching(limit: Int): Flow<List<VodEntry>> =
            combine(dao.observeByKind(VodKind.MOVIE.name), dao.observeState()) { movies, states ->
                val started =
                    states
                        .filter { it.durationMs > 0 && ResumeRules.inProgress(it.positionMs, it.durationMs) }
                        .sortedByDescending { it.updatedMs ?: 0L }
                val byId = movies.associateBy { it.id }
                started
                    .mapNotNull { s ->
                        byId[s.itemId]?.let { VodEntry(it.toModel(), s.myList, s.positionMs, s.durationMs) }
                    }.take(limit)
            }

        override fun entry(id: String): Flow<VodEntry?> =
            combine(dao.observeItem(id), dao.observeState(), dao.observeMetaOf(id)) { e, _, meta ->
                if (e == null) return@combine null
                val s = dao.state(id)
                val ratings =
                    meta
                        ?.takeIf { it.found }
                        ?.let {
                            pl.ketchupplayer.tv.core.data.metadata.Ratings(
                                it.tmdbRating,
                                it.imdbRating,
                                it.rtPercent,
                            )
                        }
                VodEntry(
                    e.toModel(),
                    s?.myList == true,
                    s?.positionMs ?: 0,
                    s?.durationMs ?: 0,
                    ratings = ratings ?: pl.ketchupplayer.tv.core.data.metadata.Ratings.NONE,
                )
            }

        override fun episodes(seriesId: String): Flow<List<EpisodeEntry>> =
            combine(dao.observeEpisodes(seriesId), dao.observeState()) { list, states ->
                val byId = states.associateBy { it.itemId }
                list.map { e -> EpisodeEntry(e.toModel(), byId[e.id]?.positionMs ?: 0, byId[e.id]?.durationMs ?: 0) }
            }

        override suspend fun refreshEpisodes(seriesId: String): Result<Int> {
            val series = dao.get(seriesId)?.toModel() ?: return Result.failure(IllegalStateException("Nie ma serialu"))
            // Odcinki dostarczone z listą (M3U) są już w bazie: nic do pobrania.
            dao
                .episodeCount(
                    seriesId,
                ).takeIf { it > 0 && series.ref == M3U_SERIES_REF }
                ?.let { return Result.success(it) }
            val provider =
                providerDao.get(series.providerId.value)?.let { factory.create(it) }
                    ?: return Result.failure(IllegalStateException("Brak dostawcy"))
            return provider.loadEpisodes(series).map { list ->
                dao.replaceEpisodes(seriesId, list.map { it.toEntity() })
                list.size
            }
        }

        override suspend fun setMyList(
            itemId: String,
            value: Boolean,
        ) {
            val s = dao.state(itemId) ?: VodStateEntity(itemId)
            dao.upsertState(s.copy(myList = value))
        }

        override suspend fun saveProgress(
            itemId: String,
            positionMs: Long,
            durationMs: Long,
        ) {
            val s = dao.state(itemId) ?: VodStateEntity(itemId)
            dao.upsertState(s.copy(positionMs = positionMs, durationMs = durationMs, updatedMs = clock.nowMs()))
        }

        override suspend fun similar(
            id: String,
            limit: Int,
        ): List<VodItem> {
            val base = dao.get(id) ?: return emptyList()
            val all = dao.byKind(base.kind)
            val tags =
                base.genre
                    ?.split(',', '/')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            return all
                .filter { it.id != id }
                .sortedByDescending { c ->
                    (if (c.category != null && c.category == base.category) 2 else 0) +
                        tags.count { c.genre?.contains(it, ignoreCase = true) == true }
                }.take(limit)
                .map { it.toModel() }
        }

        override suspend fun resolve(itemId: String): Result<StreamSpec> {
            val (providerId, ref) =
                dao.get(itemId)?.let { it.providerId to it.ref }
                    ?: dao.episode(itemId)?.let { e ->
                        dao.get(e.seriesId)?.let { it.providerId to e.ref }
                    }
                    ?: return Result.failure(IllegalStateException("Nie ma pozycji"))
            val provider =
                providerDao.get(providerId)?.let { factory.create(it) }
                    ?: return Result.failure(IllegalStateException("Brak dostawcy"))
            return provider.resolveVod(ref)
        }

        override suspend fun playback(itemId: String): VodPlayback? {
            val state = dao.state(itemId)
            val title =
                dao.get(itemId)?.title
                    ?: dao.episode(itemId)?.let { e ->
                        val series = dao.get(e.seriesId)?.title
                        listOfNotNull(series, "S${e.season} E${e.number}", e.title).joinToString(" · ")
                    }
                    ?: return null
            return VodPlayback(title, state?.positionMs ?: 0, state?.durationMs ?: 0)
        }

        private companion object {
            const val M3U_SERIES_REF = "m3u:series"
        }
    }

internal fun VodItemEntity.toModel() =
    VodItem(
        id,
        ProviderId(providerId),
        VodKind.valueOf(kind),
        title,
        ref,
        poster,
        backdrop,
        year,
        rating,
        genre,
        plot,
        durationMin,
        category,
        addedMs,
        cast,
    )

internal fun VodItem.toEntity() =
    VodItemEntity(
        id,
        providerId.value,
        kind.name,
        title,
        normTitle,
        ref,
        poster,
        backdrop,
        year,
        rating,
        genre,
        plot,
        durationMin,
        category,
        addedMs,
        cast,
    )

internal fun EpisodeEntity.toModel() =
    Episode(id, seriesId, season, number, title, ref, plot, durationMin, poster, addedMs)

internal fun Episode.toEntity() =
    EpisodeEntity(id, seriesId, season, number, title, ref, plot, durationMin, poster, addedMs)

/** Dane dostawcy uzupełniają tylko to, czego pozycja jeszcze nie ma (lista nie jest nadpisywana). */
internal fun VodItem.withInfo(info: pl.ketchupplayer.tv.core.model.VodInfo): VodItem =
    copy(
        plot = plot?.takeIf { it.isNotBlank() } ?: info.plot,
        cast = info.cast ?: cast ?: "",
        genre = genre ?: info.genre,
        backdrop = info.backdrop ?: backdrop,
        rating = rating ?: info.rating,
        durationMin = durationMin?.takeIf { it > 0 } ?: info.durationMin,
        year = year ?: info.year,
    )

private const val ENRICH_PAUSE_MS = 150L
private const val RATINGS_DEBOUNCE_MS = 800L
private const val MAX_FAILURES = 3
