package pl.ketchupplayer.tv.core.data.metadata

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pl.ketchupplayer.tv.core.network.TextFetcher
import java.net.URLEncoder

/** Dane z TMDB: opis po polsku, ocena, obsada, czas, gatunek, grafiki i id IMDb (do zapytania OMDb). */
data class TmdbMeta(
    val rating: Double?,
    val overview: String?,
    val cast: String?,
    val runtimeMin: Int?,
    val genre: String?,
    val poster: String?,
    val backdrop: String?,
    val imdbId: String?,
)

/** Wyszukuje film lub serial w TMDB (developer.themoviedb.org). Błąd sieci lub brak trafienia daje null. */
class TmdbSource(
    private val fetcher: TextFetcher,
    private val endpoints: MetadataEndpoints,
) {
    suspend fun lookup(
        apiKey: String,
        title: String,
        year: Int?,
        series: Boolean,
        /** Id TMDB z wbudowanego indeksu: pomija wyszukiwanie (jedno zapytanie mniej, trafienie pewne). */
        knownId: Long? = null,
    ): TmdbMeta? {
        val kind = if (series) "tv" else "movie"
        val id = knownId ?: search(apiKey, kind, title, year, series) ?: return null
        val url =
            "${endpoints.tmdb}/$kind/$id?${TmdbAuth.param(
                apiKey,
            )}language=pl-PL&append_to_response=credits,external_ids"
        val body =
            fetcher.fetchWithHeaders(url, TmdbAuth.headers(apiKey)).getOrNull()?.let(MetadataJson::parse)
                ?: return null
        return TmdbMeta(
            rating = body.num("vote_average")?.takeIf { it > 0.0 },
            overview = body.str("overview"),
            cast = castNames(body),
            runtimeMin = body.int("runtime") ?: episodeRuntime(body),
            genre = genres(body),
            poster = body.str("poster_path")?.let { IMAGE_BASE + "w500" + it },
            backdrop = body.str("backdrop_path")?.let { IMAGE_BASE + "w1280" + it },
            imdbId = body.str("imdb_id") ?: body["external_ids"].obj()?.str("imdb_id"),
        )
    }

    private suspend fun search(
        apiKey: String,
        kind: String,
        title: String,
        year: Int?,
        series: Boolean,
    ): Long? {
        val q = URLEncoder.encode(title, "UTF-8")
        val yearParam = year?.let { if (series) "&first_air_date_year=$it" else "&year=$it" }.orEmpty()
        val base = "${endpoints.tmdb}/search/$kind?${TmdbAuth.param(apiKey)}language=pl-PL&query=$q"
        val headers = TmdbAuth.headers(apiKey)
        // Z rokiem trafniej, ale rok w nazwie bywa błędny: bez trafienia próbujemy jeszcze bez roku.
        for (url in listOfNotNull(base + yearParam, base.takeIf { year != null })) {
            val first =
                fetcher
                    .fetchWithHeaders(url, headers)
                    .getOrNull()
                    ?.let(MetadataJson::parse)
                    ?.get("results")
                    .arr()
                    ?.firstOrNull()
            val id = first.obj()?.num("id")?.toLong()
            if (id != null) return id
        }
        return null
    }

    /** Seriale mają czas odcinka w `episode_run_time` (lista minut). */
    private fun episodeRuntime(body: JsonObject): Int? =
        (body["episode_run_time"].arr()?.firstOrNull() as? JsonPrimitive)?.content?.toIntOrNull()

    private fun genres(body: JsonObject): String? =
        body["genres"]
            .arr()
            ?.mapNotNull { it.obj()?.str("name") }
            ?.take(MAX_GENRES)
            ?.joinToString(", ")
            ?.ifEmpty { null }

    private fun castNames(body: JsonObject): String? =
        body["credits"]
            .obj()
            ?.get("cast")
            .arr()
            ?.mapNotNull { it.obj()?.str("name") }
            ?.take(MAX_CAST)
            ?.joinToString(", ")
            ?.ifEmpty { null }

    private companion object {
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
        const val MAX_CAST = 6
        const val MAX_GENRES = 3
    }
}

/**
 * TMDB ma dwa dane dostępowe: krótki „klucz API” (v3, parametr `api_key`) i długi „API Read Access Token” (JWT,
 * nagłówek Bearer). Użytkownicy często wklejają ten drugi, więc obsługujemy oba.
 */
internal object TmdbAuth {
    fun isToken(key: String) = key.startsWith("eyJ") && key.length > TOKEN_MIN_LENGTH

    /** Fragment zapytania z kluczem (z końcowym `&`) albo pusty, gdy użyto tokenu w nagłówku. */
    fun param(key: String) = if (isToken(key)) "" else "api_key=$key&"

    fun headers(key: String): Map<String, String> =
        if (isToken(key)) mapOf("Authorization" to "Bearer $key") else emptyMap()

    private const val TOKEN_MIN_LENGTH = 60
}
