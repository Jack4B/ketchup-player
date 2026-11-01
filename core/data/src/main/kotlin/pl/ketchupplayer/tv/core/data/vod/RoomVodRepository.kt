package pl.ketchupplayer.tv.core.data.vod

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
import javax.inject.Inject

class RoomVodRepository
    @Inject
    constructor(
        private val dao: VodDao,
        private val providerDao: ProviderDao,
        private val factory: ProviderFactory,
        private val clock: Clock,
    ) : VodRepository {
        private fun entries(items: Flow<List<VodItemEntity>>): Flow<List<VodEntry>> =
            combine(items, dao.observeState()) { list, states ->
                val byId = states.associateBy { it.itemId }
                list.map { e ->
                    val s = byId[e.id]
                    VodEntry(e.toModel(), s?.myList == true, s?.positionMs ?: 0, s?.durationMs ?: 0)
                }
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
                        .filter { it.positionMs > 0 && it.durationMs > 0 && it.positionMs < it.durationMs * DONE }
                        .sortedByDescending { it.updatedMs ?: 0L }
                val byId = movies.associateBy { it.id }
                started
                    .mapNotNull { s ->
                        byId[s.itemId]?.let { VodEntry(it.toModel(), s.myList, s.positionMs, s.durationMs) }
                    }.take(limit)
            }

        override fun entry(id: String): Flow<VodEntry?> =
            dao.observeState().map {
                val e = dao.get(id) ?: return@map null
                val s = dao.state(id)
                VodEntry(e.toModel(), s?.myList == true, s?.positionMs ?: 0, s?.durationMs ?: 0)
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
            const val DONE = 0.9
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
    )

internal fun EpisodeEntity.toModel() =
    Episode(id, seriesId, season, number, title, ref, plot, durationMin, poster, addedMs)

internal fun Episode.toEntity() =
    EpisodeEntity(id, seriesId, season, number, title, ref, plot, durationMin, poster, addedMs)
