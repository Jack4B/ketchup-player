package pl.ketchupplayer.tv.core.network

/** Błąd pobierania: kod HTTP, IO albo zbyt duża odpowiedź. Komunikaty nie zawierają URL-i (sekrety). */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Odpowiedź z błędem; [detail] to skrócona, oczyszczona treść/nagłówki serwera (pomaga rozpoznać blokadę). */
    class Http(
        val code: Int,
        val detail: String? = null,
    ) : NetworkException("HTTP $code" + (detail?.let { " · $it" } ?: ""))

    class Io(
        cause: Throwable,
    ) : NetworkException("Błąd sieci: ${cause.javaClass.simpleName}", cause)

    class TooLarge(
        val limitBytes: Long,
    ) : NetworkException("Odpowiedź większa niż $limitBytes B")
}

/** Pobiera tekst spod adresu (listy M3U, JSON API). Implementacja: [OkHttpTextFetcher]. */
fun interface TextFetcher {
    suspend fun fetch(url: String): Result<String>

    /**
     * Czyta odpowiedź linia po linii bez trzymania całości w pamięci (duże listy M3U).
     * [block] dostaje leniwą sekwencję, ważną tylko w jego trakcie.
     * Domyślnie korzysta z [fetch]; [OkHttpTextFetcher] strumieniuje naprawdę.
     */
    suspend fun <T> fetchLines(
        url: String,
        block: (Sequence<String>) -> T,
    ): Result<T> = fetch(url).map { block(it.lineSequence()) }

    /**
     * Czyta odpowiedź jako strumień bajtów (rozpakowany gzip), bez trzymania całości w pamięci (duże katalogi JSON).
     * Strumień jest ważny tylko w trakcie [block]. Domyślnie korzysta z [fetch]; [OkHttpTextFetcher] strumieniuje.
     */
    suspend fun <T> fetchStream(
        url: String,
        block: (java.io.InputStream) -> T,
    ): Result<T> = fetch(url).map { block(it.byteInputStream()) }

    /** Kopia pobierająca z podanym User-Agent (np. wybór użytkownika). Domyślnie bez zmian. */
    fun withUserAgent(userAgent: String): TextFetcher = this
}
