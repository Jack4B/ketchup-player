package pl.ketchupplayer.tv.core.data.metadata

import pl.ketchupplayer.tv.core.data.catalog.safeErrorText
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.core.network.TextFetcher
import javax.inject.Inject

/** Wynik próby użycia klucza jednego serwisu. */
enum class KeyStatus { OK, BAD_KEY, LIMIT, ERROR, NO_KEY }

data class KeyCheck(
    val source: String,
    val status: KeyStatus,
    /** Krótki opis błędu (kod HTTP albo przyczyna sieciowa); puste przy OK i braku klucza. */
    val detail: String = "",
)

/**
 * Sprawdza każdy wpisany klucz jednym, znanym zapytaniem (film „Matrix”), żeby użytkownik widział, który serwis
 * odpowiada, a który odrzuca klucz. Bez tego zły klucz kończył się po prostu brakiem opisów i ocen.
 */
class KeyProbe
    @Inject
    constructor(
        private val fetcher: TextFetcher,
        private val endpoints: MetadataEndpoints,
    ) {
        suspend fun check(keys: ApiKeys): List<KeyCheck> =
            listOf(
                probe("TMDB", keys.tmdb) { k ->
                    val url = "${endpoints.tmdb}/movie/$MATRIX_TMDB?${TmdbAuth.param(k)}language=pl-PL"
                    fetcher.fetchWithHeaders(url, TmdbAuth.headers(k))
                },
                probe("OMDb", keys.omdb) { k -> fetcher.fetch("${endpoints.omdb}/?apikey=$k&i=$MATRIX_IMDB") },
                probe("MDBList", keys.mdblist) { k ->
                    fetcher.fetch("${endpoints.mdblist}/imdb/movie/$MATRIX_IMDB?apikey=$k")
                },
                probe("Trakt", keys.trakt) { k ->
                    fetcher.fetchWithHeaders(
                        "${endpoints.trakt}/movies/the-matrix-1999",
                        mapOf("trakt-api-key" to k, "trakt-api-version" to "2", "Content-Type" to "application/json"),
                    )
                },
            )

        private suspend fun probe(
            source: String,
            key: String,
            request: suspend (String) -> Result<String>,
        ): KeyCheck {
            if (key.isBlank()) return KeyCheck(source, KeyStatus.NO_KEY)
            val result = request(key.trim())
            result.getOrNull()?.let { body ->
                // OMDb odpowiada 200 z treścią {"Response":"False","Error":"Invalid API key!"} przy złym kluczu.
                val invalid = body.contains("Invalid API key", ignoreCase = true)
                return if (invalid) {
                    KeyCheck(
                        source,
                        KeyStatus.BAD_KEY,
                        "Invalid API key",
                    )
                } else {
                    KeyCheck(source, KeyStatus.OK)
                }
            }
            return when (val e = result.exceptionOrNull()) {
                is NetworkException.Http ->
                    when (e.code) {
                        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> KeyCheck(source, KeyStatus.BAD_KEY, "HTTP ${e.code}")
                        HTTP_TOO_MANY -> KeyCheck(source, KeyStatus.LIMIT, "HTTP ${e.code}")
                        else -> KeyCheck(source, KeyStatus.ERROR, "HTTP ${e.code}")
                    }
                else ->
                    KeyCheck(
                        source,
                        KeyStatus.ERROR,
                        e?.let { safeErrorText(it, "błąd").take(MAX_DETAIL) }.orEmpty(),
                    )
            }
        }

        private companion object {
            const val MATRIX_TMDB = 603
            const val MATRIX_IMDB = "tt0133093"
            const val HTTP_UNAUTHORIZED = 401
            const val HTTP_FORBIDDEN = 403
            const val HTTP_TOO_MANY = 429
            const val MAX_DETAIL = 80
        }
    }
