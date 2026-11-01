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
     * Jak w starym BK Playerze (1.8.x), który z listami dostawców działał: ten sam nagłówek przy pobieraniu list
     * i API oraz osobny przy odtwarzaniu. Panele IPTV potrafią odrzucać (404) nieznane nagłówki.
     */
    const val DEFAULT_USER_AGENT = "BK-player-Next/1.0"
    const val PLAYER_USER_AGENT = "BK-player-Next/1.7.2 AndroidXMedia3/1.11.1"

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
                if (req.header("User-Agent") ==
                    null
                ) {
                    req.newBuilder().header("User-Agent", userAgent).build()
                } else {
                    req
                },
            )
        }

    private const val CONNECT_TIMEOUT_S = 20L
    private const val READ_TIMEOUT_S = 60L
}
