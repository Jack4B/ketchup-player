package pl.ketchupplayer.tv.core.network

import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * Dwa klienty HTTP (KONSPEKT §8.1):
 * - [stream]: strumienie i API dostawców IPTV — dopuszcza HTTP (listy użytkownika często bez TLS),
 * - [metadata]: TMDB, EPG, napisy — wyłącznie TLS.
 */
object NetworkClients {
    /**
     * Nagłówek w stylu odtwarzacza: panele IPTV często odrzucają (404/403) nieznane aplikacje i przeglądarki,
     * a przepuszczają VLC. Nie podszywamy się pod konkretną aplikację dostawcy; wartość zastępuje `#EXTVLCOPT`.
     */
    const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    fun stream(userAgent: String = DEFAULT_USER_AGENT): OkHttpClient =
        base(
            userAgent,
        ).connectionSpecs(
            listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT),
        )
            // Panele IPTV odpowiadają na handshake HTTP/2 błędem protokołu; HTTP/1.1 działa wszędzie.
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()

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

    private const val CONNECT_TIMEOUT_S = 15L
    private const val READ_TIMEOUT_S = 30L
}
