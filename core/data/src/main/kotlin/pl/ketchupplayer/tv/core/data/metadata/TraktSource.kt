package pl.ketchupplayer.tv.core.data.metadata

import kotlinx.serialization.json.JsonObject
import pl.ketchupplayer.tv.core.network.TextFetcher
import java.net.URLEncoder

/** Dane z Traktu: ocena użytkowników (0-10), opis i id IMDb. */
data class TraktMeta(
    val rating: Double?,
    /** Opis po polsku (null, gdy Trakt nie ma tłumaczenia) i angielski z podsumowania. */
    val overview: String?,
    val overviewEn: String?,
    val imdbId: String?,
    val runtimeMin: Int?,
)

/**
 * Trakt (api.trakt.tv): publiczne dane filmów i seriali wymagają tylko Client ID własnej aplikacji (trakt.tv/oauth/
 * applications), bez logowania. Błąd sieci lub brak trafienia daje null.
 */
class TraktSource(
    private val fetcher: TextFetcher,
    private val endpoints: MetadataEndpoints,
) {
    suspend fun lookup(
        clientId: String,
        title: String,
        year: Int?,
        series: Boolean,
    ): TraktMeta? {
        val kind = if (series) "shows" else "movies"
        val headers =
            mapOf(
                "trakt-api-key" to clientId,
                "trakt-api-version" to "2",
                "Content-Type" to "application/json",
            )
        val ids = search(headers, kind, title, year) ?: return null
        val slug = ids.str("slug") ?: ids.str("trakt") ?: return null
        val summary =
            fetcher
                .fetchWithHeaders("${endpoints.trakt}/$kind/$slug?extended=full", headers)
                .getOrNull()
                ?.let(MetadataJson::parse) ?: return null
        val polish =
            fetcher
                .fetchWithHeaders("${endpoints.trakt}/$kind/$slug/translations/pl", headers)
                .getOrNull()
                ?.let { MetadataJson.parseArray(it) }
                ?.firstNotNullOfOrNull { it.obj()?.str("overview") }
        return TraktMeta(
            rating = summary.num("rating")?.takeIf { it > 0.0 },
            overview = polish,
            overviewEn = summary.str("overview"),
            imdbId = summary["ids"].obj()?.str("imdb") ?: ids.str("imdb"),
            runtimeMin = summary.int("runtime"),
        )
    }

    /** Pierwszy wynik wyszukiwania (z rokiem trafniej; bez trafienia próbujemy jeszcze bez roku). */
    private suspend fun search(
        headers: Map<String, String>,
        kind: String,
        title: String,
        year: Int?,
    ): JsonObject? {
        val type = if (kind == "shows") "show" else "movie"
        val q = URLEncoder.encode(title, "UTF-8")
        val base = "${endpoints.trakt}/search/$type?query=$q"
        for (url in listOfNotNull(year?.let { "$base&years=$it" }, base)) {
            val first =
                fetcher
                    .fetchWithHeaders(url, headers)
                    .getOrNull()
                    ?.let { MetadataJson.parseArray(it) }
                    ?.firstOrNull()
                    .obj()
                    ?.get(type)
                    .obj()
                    ?.get("ids")
                    .obj()
            if (first != null) return first
        }
        return null
    }
}
