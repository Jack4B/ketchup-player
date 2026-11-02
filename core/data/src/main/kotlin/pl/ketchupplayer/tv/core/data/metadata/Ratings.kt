package pl.ketchupplayer.tv.core.data.metadata

/** Oceny filmu z zewnętrznych źródeł (skala 0-10, Rotten Tomatoes w procentach). */
data class Ratings(
    val tmdb: Double? = null,
    val imdb: Double? = null,
    val rtPercent: Int? = null,
) {
    /** Ocena do plakietki na karcie: IMDb, a bez niej TMDB. */
    val best: Double? get() = imdb ?: tmdb

    val isEmpty: Boolean get() = tmdb == null && imdb == null && rtPercent == null

    companion object {
        val NONE = Ratings()
    }
}
