package pl.ketchupplayer.tv.player.engine

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Decyzja, czy błąd odpowiedzi HTTP warto ponawiać. Błędy klienta (4xx: 404 brak kanału, 401/403 odmowa) nie miną
 * po sekundzie, więc ponawianie tylko przetrzymuje użytkownika na czarnym ekranie; wyjątki: 408 i 429 (chwilowe).
 */
object FailFastLoadPolicy {
    private const val CLIENT_ERROR_MIN = 400
    private const val CLIENT_ERROR_MAX = 499
    private const val REQUEST_TIMEOUT = 408
    private const val TOO_MANY_REQUESTS = 429

    fun isRetryableHttp(code: Int): Boolean =
        code !in CLIENT_ERROR_MIN..CLIENT_ERROR_MAX || code == REQUEST_TIMEOUT || code == TOO_MANY_REQUESTS
}

/** Polityka ExoPlayera, w której 4xx kończy ładowanie od razu; o przełączeniu źródła decyduje failover. */
@UnstableApi
class FailFastLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        if (error is HttpDataSource.InvalidResponseCodeException &&
            !FailFastLoadPolicy.isRetryableHttp(error.responseCode)
        ) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }
}
