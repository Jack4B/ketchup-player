package pl.ketchupplayer.tv.core.data.metadata

import pl.ketchupplayer.tv.core.network.TextFetcher
import java.net.URLEncoder

/** Zbiorcze oceny z MDBList: IMDb, Rotten Tomatoes, Metacritic, Trakt i TMDB w jednym zapytaniu. */
data class MdbListMeta(
    val imdb: Double?,
    val rtPercent: Int?,
    val metacritic: Int?,
    val trakt: Double?,
    val tmdb: Double?,
    val description: String?,
)

/**
 * MDBList (mdblist.com): jeden darmowy klucz daje oceny z kilku serwisów naraz (to jedyna legalna droga do ocen
 * Rotten Tomatoes i Metacritic poza OMDb). Odpowiedź czytamy tolerancyjnie: nieznany kształt to null, nie awaria.
 */
class MdbListSource(
    private val fetcher: TextFetcher,
    private val endpoints: MetadataEndpoints,
) {
    suspend fun lookup(
        apiKey: String,
        imdbId: String?,
        title: String,
        year: Int?,
        series: Boolean,
    ): MdbListMeta? {
        val media = if (series) "show" else "movie"
        val id = imdbId ?: searchImdbId(apiKey, media, title, year) ?: return null
        val body =
            fetcher
                .fetch("${endpoints.mdblist}/imdb/$media/$id?apikey=$apiKey")
                .getOrNull()
                ?.let(MetadataJson::parse) ?: return null
        val ratings = body["ratings"].arr()?.mapNotNull { it.obj() }.orEmpty()

        fun value(source: String): Double? = ratings.firstOrNull { it.str("source") == source }?.num("value")
        return MdbListMeta(
            imdb = value("imdb"),
            rtPercent = value("tomatoes")?.toInt(),
            metacritic = value("metacritic")?.toInt(),
            trakt = value("trakt")?.let { if (it > TEN) it / PERCENT_TO_TEN else it },
            tmdb = value("tmdb")?.let { if (it > TEN) it / PERCENT_TO_TEN else it },
            description = body.str("description"),
        )
    }

    private suspend fun searchImdbId(
        apiKey: String,
        media: String,
        title: String,
        year: Int?,
    ): String? {
        val q = URLEncoder.encode(title, "UTF-8")
        val y = year?.let { "&year=$it" }.orEmpty()
        val body =
            fetcher
                .fetch("${endpoints.mdblist}/search/$media?apikey=$apiKey&query=$q$y")
                .getOrNull()
                ?.let(MetadataJson::parse) ?: return null
        return body["search"]
            .arr()
            ?.firstOrNull()
            .obj()
            ?.let { it.str("imdbid") ?: it.str("imdb_id") }
    }

    private companion object {
        const val TEN = 10.0
        const val PERCENT_TO_TEN = 10.0
    }
}
