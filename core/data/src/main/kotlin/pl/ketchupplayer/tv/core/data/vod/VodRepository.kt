package pl.ketchupplayer.tv.core.data.vod

import kotlinx.coroutines.flow.Flow
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind

/** Pozycja katalogu wraz ze stanem użytkownika (Moja lista, postęp). */
data class VodEntry(
    val item: VodItem,
    val myList: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

data class EpisodeEntry(
    val episode: Episode,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val watched: Boolean get() = durationMs > 0 && positionMs >= durationMs * WATCHED_RATIO

    private companion object {
        const val WATCHED_RATIO = 0.9f
    }
}

/** Katalog filmów i seriali (offline-first, jak [pl.ketchupplayer.tv.core.data.catalog.CatalogRepository]). */
interface VodRepository {
    fun entries(kind: VodKind): Flow<List<VodEntry>>

    /** Pozycje z „Mojej listy” (filmy i seriale). */
    fun myList(): Flow<List<VodEntry>>

    /** Filmy i odcinki do wznowienia (rozpoczęte, nieukończone), najnowsze pierwsze. */
    fun continueWatching(limit: Int): Flow<List<VodEntry>>

    fun entry(id: String): Flow<VodEntry?>

    fun episodes(seriesId: String): Flow<List<EpisodeEntry>>

    /** Pobiera odcinki serialu od dostawcy i zapisuje je w bazie. */
    suspend fun refreshEpisodes(seriesId: String): Result<Int>

    suspend fun setMyList(
        itemId: String,
        value: Boolean,
    )

    suspend fun saveProgress(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
    )

    /** Podobne pozycje: ten sam rodzaj i gatunek lub kategoria. */
    suspend fun similar(
        id: String,
        limit: Int,
    ): List<VodItem>

    suspend fun resolve(itemId: String): Result<StreamSpec>
}
