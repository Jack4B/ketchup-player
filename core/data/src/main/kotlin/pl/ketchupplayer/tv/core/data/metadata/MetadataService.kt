package pl.ketchupplayer.tv.core.data.metadata

import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind

/** Co udało się ustalić o filmie z zewnętrznych źródeł (pola mogą być puste). */
data class MetaResult(
    val ratings: Ratings,
    val overview: String?,
    val cast: String?,
    val runtimeMin: Int?,
    val genre: String?,
    val poster: String?,
    val backdrop: String?,
)

/**
 * Łączy TMDB (opis PL, ocena, obsada, grafiki, id IMDb) z OMDb (ocena IMDb i Rotten Tomatoes). Źródło bez klucza jest
 * pomijane; brak trafienia w obu to null (nie ponawiamy w kółko, patrz [MetadataRepository]).
 */
class MetadataService(
    private val tmdb: TmdbSource,
    private val omdb: OmdbSource,
) {
    suspend fun fetch(
        item: VodItem,
        keys: ApiKeys,
    ): MetaResult? {
        val series = item.kind == VodKind.SERIES
        val t = if (keys.tmdb.isNotBlank()) tmdb.lookup(keys.tmdb, item.title, item.year, series) else null
        val o =
            if (keys.omdb.isNotBlank()) omdb.lookup(keys.omdb, t?.imdbId, item.title, item.year, series) else null
        if (t == null && o == null) return null
        return MetaResult(
            ratings = Ratings(tmdb = t?.rating, imdb = o?.imdbRating, rtPercent = o?.rtPercent),
            overview = t?.overview ?: o?.plot,
            cast = t?.cast,
            runtimeMin = t?.runtimeMin,
            genre = t?.genre,
            poster = t?.poster,
            backdrop = t?.backdrop,
        )
    }
}
