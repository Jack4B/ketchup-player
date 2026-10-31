package pl.ketchupplayer.tv.core.network

import java.io.InputStream

/**
 * Otwiera strumień odpowiedzi bez ładowania go w całości do pamięci (duże pliki XMLTV).
 * [block] wykonuje się na wątku IO, a strumień jest zamykany po jego zakończeniu;
 * gzip jest rozpakowywany automatycznie.
 */
interface StreamFetcher {
    suspend fun <T> open(
        url: String,
        block: (InputStream) -> T,
    ): Result<T>
}
