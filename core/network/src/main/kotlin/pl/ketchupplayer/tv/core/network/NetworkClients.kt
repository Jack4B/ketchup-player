package pl.ketchupplayer.tv.core.network

import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Dwa klienty HTTP (KONSPEKT §8.1):
 * - [stream]: strumienie i API dostawców IPTV — dopuszcza HTTP (listy użytkownika często bez TLS),
 * - [metadata]: TMDB, EPG, napisy — wyłącznie TLS.
 */
object NetworkClients {
    /**
     * Domyślny nagłówek: OTT Navigator, popularny odtwarzacz IPTV, który panele przepuszczają. Można go zmienić dla
     * każdej listy (w kreatorze przed pierwszym pobraniem i w Ustawieniach → Sieć), także na własny.
     */
    const val DEFAULT_USER_AGENT = "OTT Navigator/1.7.2.1 (Linux;Android 12) ExoPlayerLib/2.19.1"
    const val PLAYER_USER_AGENT = DEFAULT_USER_AGENT

    fun stream(userAgent: String = DEFAULT_USER_AGENT): OkHttpClient =
        base(
            userAgent,
        ).connectionSpecs(
            listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT),
        ).build()

    fun metadata(userAgent: String = DEFAULT_USER_AGENT): OkHttpClient =
        base(userAgent).connectionSpecs(listOf(ConnectionSpec.MODERN_TLS)).build()

    private fun base(userAgent: String) =
        OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(userAgentInterceptor(userAgent))

    private fun userAgentInterceptor(userAgent: String) =
        Interceptor { chain ->
            val req = chain.request()
            chain.proceed(
                req
                    .newBuilder()
                    .apply {
                        if (req.header("User-Agent") == null) header("User-Agent", userAgent)
                        if (req.header("Accept") == null) header("Accept", "*/*")
                    }.build(),
            )
        }

    private const val CONNECT_TIMEOUT_S = 20L
    private const val READ_TIMEOUT_S = 60L
}
