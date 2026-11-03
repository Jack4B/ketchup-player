package pl.ketchupplayer.tv.core.data.metadata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.vod.toEntity
import pl.ketchupplayer.tv.core.data.vod.toModel
import pl.ketchupplayer.tv.core.database.VodDao
import pl.ketchupplayer.tv.core.database.VodMetaEntity
import pl.ketchupplayer.tv.core.model.VodItem
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dociąga oceny i opisy dla pozycji katalogu. Wynik (także „nie znaleziono”) trafia do bazy, więc każdy film jest
 * sprawdzany raz; brak trafienia ponawiamy dopiero po [RETRY_AFTER_MS]. Zapytania z interfejsu ([request], np. dla kart
 * widocznych na ekranie) idą kolejką po kilka naraz, żeby nie zalać API przy szybkim przewijaniu.
 */
@Singleton
class MetadataRepository
    @Inject
    constructor(
        private val dao: VodDao,
        private val keys: MetadataKeys,
        private val service: MetadataService,
        private val clock: Clock,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val queued = ConcurrentHashMap.newKeySet<String>()
        private val gate = Semaphore(MAX_PARALLEL)

        /** Kolejkuje pobranie (bez czekania). Wielokrotne wywołania dla tego samego id są scalane. */
        fun request(itemId: String) {
            if (!queued.add(itemId)) return
            scope.launch {
                try {
                    gate.withPermit { ensure(itemId) }
                } finally {
                    queued.remove(itemId)
                }
            }
        }

        /**
         * Pobiera i zapisuje metadane, jeśli ich jeszcze nie ma; true, gdy pozycja ma dane z zewnętrznych źródeł.
         * [deep] = ekran szczegółów (patrz [MetadataService.fetch]): raz na film dociąga też OMDb/MDBList i opis.
         */
        suspend fun ensure(
            itemId: String,
            deep: Boolean = false,
        ): Boolean {
            val now = clock.nowMs()
            val existing = dao.meta(itemId)
            val apiKeys = keys.current()
            if (existing != null && alreadyDone(existing, apiKeys, now, deep)) return existing.found
            val item = dao.get(itemId)?.toModel() ?: return false
            val deepMs = if (deep) now else existing?.deepMs
            val result = fetchSafely(item, apiKeys, deep)
            if (result == null) {
                // Pudło bez kluczy (tylko Wikipedia) nie blokuje późniejszego pobrania po wpisaniu kluczy.
                if (apiKeys.any) {
                    dao.upsertMeta(
                        VodMetaEntity(itemId, null, null, null, now, found = false, deepMs = deepMs),
                    )
                }
                return false
            }
            val r = result.ratings
            dao.upsertMeta(
                VodMetaEntity(itemId, r.tmdb, r.imdb, r.rtPercent, now, found = true, r.metacritic, r.trakt, deepMs),
            )
            val merged = MetadataMerge.fill(item, result)
            if (merged != item) dao.update(merged.toEntity())
            return true
        }

        /**
         * Pozycja jest załatwiona, gdy znaleziono dane albo pudło jest świeże. Wyjątki: sam opis (z Wikipedii)
         * uzupełniamy ocenami, gdy użytkownik wpisze klucze, a ekran szczegółów robi pobranie „głębokie” raz na film.
         */
        private fun alreadyDone(
            m: VodMetaEntity,
            apiKeys: ApiKeys,
            now: Long,
            deep: Boolean,
        ): Boolean {
            val noRatings =
                m.tmdbRating == null &&
                    m.imdbRating == null &&
                    m.rtPercent == null &&
                    m.metacritic == null &&
                    m.traktRating == null
            if (m.found && noRatings && apiKeys.any) return false
            if (deep && m.deepMs == null) return false
            return m.found || now - m.fetchedMs < RETRY_AFTER_MS
        }

        /** Błąd sieci lub parsowania to „brak danych” (wynik zapisujemy z krótkim oknem ponowienia), nie awaria. */
        private suspend fun fetchSafely(
            item: VodItem,
            apiKeys: ApiKeys,
            deep: Boolean,
        ): MetaResult? =
            runCatching { service.fetch(item, apiKeys, deep) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()

        private companion object {
            const val MAX_PARALLEL = 3
            const val RETRY_AFTER_MS = 6L * 60 * 60 * 1000
        }
    }
