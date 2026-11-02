package pl.ketchupplayer.tv.core.data.metadata

import pl.ketchupplayer.tv.core.network.TextFetcher
import java.net.URLEncoder

/** Oceny z OMDb: IMDb i Rotten Tomatoes (OMDb nie ma polskich opisów, więc opis bierzemy z TMDB). */
data class OmdbMeta(
    val imdbRating: Double?,
    val rtPercent: Int?,
    val plot: String?,
    val metacritic: Int? = null,
)

/** Pyta OMDb (https://www.omdbapi.com) po id IMDb (z TMDB) albo po tytule i roku. */
class OmdbSource(
    private val fetcher: TextFetcher,
    private val endpoints: MetadataEndpoints,
) {
    suspend fun lookup(
        apiKey: String,
        imdbId: String?,
        title: String,
        year: Int?,
        series: Boolean,
    ): OmdbMeta? {
        val query =
            if (imdbId != null) {
                "i=$imdbId"
            } else {
                val y = year?.let { "&y=$it" }.orEmpty()
                "t=${URLEncoder.encode(title, "UTF-8")}$y&type=${if (series) "series" else "movie"}"
            }
        val body =
            fetcher.fetch("${endpoints.omdb}/?apikey=$apiKey&plot=short&$query").getOrNull()?.let(MetadataJson::parse)
                ?: return null
        if (body.str("Response") == "False") return null
        val rt =
            body["Ratings"]
                .arr()
                ?.mapNotNull { it.obj() }
                ?.firstOrNull { it.str("Source") == "Rotten Tomatoes" }
                ?.str("Value")
                ?.trimEnd('%')
                ?.toIntOrNull()
        val meta =
            body["Ratings"]
                .arr()
                ?.mapNotNull { it.obj() }
                ?.firstOrNull { it.str("Source") == "Metacritic" }
                ?.str("Value")
                ?.substringBefore('/')
                ?.toIntOrNull()
        return OmdbMeta(body.str("imdbRating")?.toDoubleOrNull(), rt, body.str("Plot"), meta)
    }
}
