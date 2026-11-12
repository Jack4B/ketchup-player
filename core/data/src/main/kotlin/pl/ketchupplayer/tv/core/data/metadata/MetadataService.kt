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
    /** Reżyser i sposób dopasowania z TMDB (do cech personalizacji z pochodzeniem); null bez TMDB. */
    val director: String? = null,
    val tmdbMatch: TmdbMatchKind? = null,
)

/** Źródła metadanych; te bez klucza użytkownika są pomijane. */
class MetadataSources(
    val tmdb: TmdbSource,
    val omdb: OmdbSource,
    val trakt: TraktSource? = null,
    val mdblist: MdbListSource? = null,
    val wikipedia: WikipediaSource? = null,
    /** Wbudowany indeks: identyfikatory bez zapytań (TMDB, IMDb, strona plwiki). */
    val index: FilmIndex = NoFilmIndex,
)

/**
 * Łączy źródła: TMDB (opis PL, ocena, obsada, grafiki, id IMDb), Trakt (ocena, opis PL), OMDb (IMDb, Rotten Tomatoes,
 * Metacritic), MDBList (te same oceny zbiorczo) i, jako opis bez klucza, Wikipedię. Pierwszy dostępny wynik wygrywa,
 * opisy polskie przed angielskimi. Brak trafienia to null (nie ponawiamy w kółko, patrz [MetadataRepository]).
 */
class MetadataService(
    private val sources: MetadataSources,
    private val budget: DailyBudget = DailyBudget(),
) {
    constructor(
        tmdb: TmdbSource,
        omdb: OmdbSource,
        wikipedia: WikipediaSource? = null,
    ) : this(MetadataSources(tmdb, omdb, wikipedia = wikipedia))

    /**
     * [deep] = ekran szczegółów jednego filmu: wolno użyć też MDBList i Wikipedii oraz większej części limitu OMDb.
     * Karty w siatce (deep = false) biorą tylko tanie źródła (TMDB, Trakt) i ograniczoną pulę OMDb, żeby limit 1000
     * zapytań dziennie wystarczył na filmy, które naprawdę otwierasz.
     */
    suspend fun fetch(
        item: VodItem,
        keys: ApiKeys,
        deep: Boolean = false,
    ): MetaResult? {
        val series = item.kind == VodKind.SERIES
        val ref = sources.index.find(item.title, item.year, series)
        val t = keyed(keys.tmdb) { sources.tmdb.lookup(it, item.title, item.year, series, ref?.tmdb) }
        val tr = keyed(keys.trakt) { sources.trakt?.lookup(it, item.title, item.year, series) }
        val imdbId = t?.imdbId ?: tr?.imdbId ?: ref?.imdb
        val omdbCap = if (deep) DEEP_CAP else CARD_OMDB_CAP
        val o =
            metered("omdb", 1, omdbCap, keys.omdb) { sources.omdb.lookup(it, imdbId, item.title, item.year, series) }
        val m = if (deep) mdblist(item, keys, imdbId, series) else null
        val polish = t?.overview ?: tr?.overview
        // Wikipedia tylko dla ekranu szczegółów i tylko wtedy, gdy żadne źródło nie dało polskiego opisu.
        val wiki = if (deep && polish == null) wikipedia(item, series, ref) else null
        if (listOf(t, tr, o, m, wiki, ref?.imdbRating).all { it == null }) return null
        return MetaResult(
            ratings = mergeRatings(t, tr, o, m, ref?.imdbRating),
            overview = polish ?: wiki ?: tr?.overviewEn ?: m?.description ?: o?.plot,
            cast = t?.cast,
            runtimeMin = t?.runtimeMin ?: tr?.runtimeMin,
            genre = t?.genre,
            poster = t?.poster,
            backdrop = t?.backdrop,
            director = t?.director,
            tmdbMatch = t?.match,
        )
    }

    private suspend fun mdblist(
        item: VodItem,
        keys: ApiKeys,
        imdbId: String?,
        series: Boolean,
    ): MdbListMeta? =
        metered("mdblist", if (imdbId != null) 1 else 2, DEEP_CAP, keys.mdblist) {
            sources.mdblist?.lookup(it, imdbId, item.title, item.year, series)
        }

    private suspend fun wikipedia(
        item: VodItem,
        series: Boolean,
        ref: FilmRef?,
    ): String? = sources.wikipedia?.lookup(item.title, item.year, series, ref?.plWikiTitle)

    /** Źródło bez klucza (puste pole) jest pomijane. */
    private suspend fun <T> keyed(
        key: String,
        block: suspend (String) -> T?,
    ): T? = if (key.isBlank()) null else block(key)

    /** Jak [keyed], ale zajmuje [n] zapytań z dziennego budżetu źródła; po wyczerpaniu źródło jest pomijane. */
    private suspend fun <T> metered(
        source: String,
        n: Int,
        cap: Int,
        key: String,
        block: suspend (String) -> T?,
    ): T? = if (key.isBlank() || !budget.take(source, n, cap)) null else block(key)

    /** Pierwsze dostępne źródło wygrywa; MDBList uzupełnia to, czego nie dały OMDb, Trakt i TMDB. */
    private fun mergeRatings(
        t: TmdbMeta?,
        tr: TraktMeta?,
        o: OmdbMeta?,
        m: MdbListMeta?,
        indexImdb: Double?,
    ) = Ratings(
        tmdb = t?.rating ?: m?.tmdb,
        // Ocena z wbudowanego indeksu (publiczne dane IMDb) to rezerwa: świeższe odpowiedzi API mają pierwszeństwo.
        imdb = o?.imdbRating ?: m?.imdb ?: indexImdb,
        rtPercent = o?.rtPercent ?: m?.rtPercent,
        metacritic = o?.metacritic ?: m?.metacritic,
        trakt = tr?.rating ?: m?.trakt,
    )

    private companion object {
        /** Darmowe klucze OMDb i MDBList: 1000 zapytań dziennie; zostawiamy zapas. */
        const val DEEP_CAP = 950
        const val CARD_OMDB_CAP = 500
    }
}
