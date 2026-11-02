package pl.ketchupplayer.tv.core.data.metadata

/** Oceny filmu z zewnętrznych źródeł (skala 0-10, Rotten Tomatoes w procentach). */
data class Ratings(
    val tmdb: Double? = null,
    val imdb: Double? = null,
    val rtPercent: Int? = null,
    /** Metacritic (0-100) i Trakt (0-10): z OMDb, MDBList i Traktu. */
    val metacritic: Int? = null,
    val trakt: Double? = null,
) {
    /** Ocena do plakietki na karcie: IMDb, a bez niej TMDB. */
    val best: Double? get() = imdb ?: tmdb

    /** Źródło oceny z [best]: do podpisu na plakietce. */
    val bestSource: String? get() =
        if (imdb != null) {
            "IMDb"
        } else if (tmdb != null) {
            "TMDB"
        } else {
            null
        }

    val isEmpty: Boolean get() =
        tmdb == null && imdb == null && rtPercent == null && metacritic == null &&
            trakt == null

    companion object {
        val NONE = Ratings()
    }
}
