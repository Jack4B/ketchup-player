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

/** Źródła metadanych; te bez klucza użytkownika są pomijane. */
class MetadataSources(
    val tmdb: TmdbSource,
    val omdb: OmdbSource,
    val trakt: TraktSource? = null,
    val mdblist: MdbListSource? = null,
    val wikipedia: WikipediaSource? = null,
)

/**
 * Łączy źródła: TMDB (opis PL, ocena, obsada, grafiki, id IMDb), Trakt (ocena, opis PL), OMDb (IMDb, Rotten Tomatoes,
 * Metacritic), MDBList (te same oceny zbiorczo) i, jako opis bez klucza, Wikipedię. Pierwszy dostępny wynik wygrywa,
 * opisy polskie przed angielskimi. Brak trafienia to null (nie ponawiamy w kółko, patrz [MetadataRepository]).
 */
class MetadataService(
    private val sources: MetadataSources,
) {
    constructor(
        tmdb: TmdbSource,
        omdb: OmdbSource,
        wikipedia: WikipediaSource? = null,
    ) : this(MetadataSources(tmdb, omdb, wikipedia = wikipedia))

    suspend fun fetch(
        item: VodItem,
        keys: ApiKeys,
        keyless: Boolean = false,
    ): MetaResult? {
        val series = item.kind == VodKind.SERIES
        val t = keyed(keys.tmdb) { sources.tmdb.lookup(it, item.title, item.year, series) }
        val tr = keyed(keys.trakt) { sources.trakt?.lookup(it, item.title, item.year, series) }
        val imdbId = t?.imdbId ?: tr?.imdbId
        val o = keyed(keys.omdb) { sources.omdb.lookup(it, imdbId, item.title, item.year, series) }
        val m = keyed(keys.mdblist) { sources.mdblist?.lookup(it, imdbId, item.title, item.year, series) }
        val polish = t?.overview ?: tr?.overview
        // Wikipedia tylko dla ekranu szczegółów i tylko wtedy, gdy żadne źródło nie dało polskiego opisu.
        val wiki = if (keyless && polish == null) sources.wikipedia?.lookup(item.title, item.year, series) else null
        if (listOf(t, tr, o, m, wiki).all { it == null }) return null
        return MetaResult(
            ratings = mergeRatings(t, tr, o, m),
            overview = polish ?: wiki ?: tr?.overviewEn ?: m?.description ?: o?.plot,
            cast = t?.cast,
            runtimeMin = t?.runtimeMin ?: tr?.runtimeMin,
            genre = t?.genre,
            poster = t?.poster,
            backdrop = t?.backdrop,
        )
    }

    /** Źródło bez klucza (puste pole) jest pomijane. */
    private suspend fun <T> keyed(
        key: String,
        block: suspend (String) -> T?,
    ): T? = if (key.isBlank()) null else block(key)

    /** Pierwsze dostępne źródło wygrywa; MDBList uzupełnia to, czego nie dały OMDb, Trakt i TMDB. */
    private fun mergeRatings(
        t: TmdbMeta?,
        tr: TraktMeta?,
        o: OmdbMeta?,
        m: MdbListMeta?,
    ) = Ratings(
        tmdb = t?.rating ?: m?.tmdb,
        imdb = o?.imdbRating ?: m?.imdb,
        rtPercent = o?.rtPercent ?: m?.rtPercent,
        metacritic = o?.metacritic ?: m?.metacritic,
        trakt = tr?.rating ?: m?.trakt,
    )
}
