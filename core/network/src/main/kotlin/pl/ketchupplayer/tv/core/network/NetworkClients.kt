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
     * Domyślny User-Agent importu (listy, API Xtream, EPG): taki sam jak u IPTV Extreme, któremu panel za Cloudflare
     * odpowiada 200, gdy nagłówek aplikacji OTT Navigator dostaje 520. Można go zmienić dla każdej listy (w kreatorze
     * i w Ustawieniach → Sieć), także na własny.
     */
    const val DEFAULT_USER_AGENT = "okhttp/4.10.0"

    /** Dawny domyślny nagłówek, teraz tylko jeden z gotowych profili. */
    const val OTT_USER_AGENT = "OTT Navigator/1.7.2.1 (Linux;Android 12) ExoPlayerLib/2.19.1"

    /** Domyślny User-Agent odtwarzacza strumieni (VLC, jak w przechwyceniu odtwarzania IPTV Extreme). */
    const val PLAYER_USER_AGENT = "VLC/3.4.3 LibVLC/3.4.31"

    /**
     * Klient list i API: na drucie tylko `User-Agent`, `Host`, `Connection: Keep-Alive` i `Accept-Encoding: gzip`
     * (w tej kolejności; dwa ostatnie i `Host` dokłada OkHttp). Nie dodajemy `Accept` ani niczego więcej.
     */
    fun stream(userAgent: String = DEFAULT_USER_AGENT): OkHttpClient =
        base(userAgent).connectionSpecs(IPTV_SPECS).build()

    /**
     * Klient odtwarzacza (Media3): User-Agent VLC, `Accept` (dowolny typ) i `Accept-Language: en_US` jak w
     * przechwyceniu odtwarzania. Przekierowania (302 na serwer strumieni) OkHttp obsługuje sam i zachowuje nagłówki
     * tożsamości (User-Agent, Range), a [CrossHostGuard] nie przenosi na inny host niczego poza nimi.
     */
    fun player(userAgent: String = PLAYER_USER_AGENT): OkHttpClient =
        base(userAgent)
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()
                if (req.header("Accept") == null) builder.header("Accept", "*/*")
                if (req.header("Accept-Language") == null) builder.header("Accept-Language", ACCEPT_LANGUAGE)
                chain.proceed(builder.build())
            }.connectionSpecs(IPTV_SPECS)
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
            .addNetworkInterceptor(CrossHostGuard)

    private fun userAgentInterceptor(userAgent: String) =
        Interceptor { chain ->
            val req = chain.request()
            chain.proceed(
                req
                    .newBuilder()
                    .apply {
                        if (req.header("User-Agent") == null) header("User-Agent", userAgent)
                    }.build(),
            )
        }

    private val IPTV_SPECS =
        listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)

    private const val ACCEPT_LANGUAGE = "en_US"
    private const val CONNECT_TIMEOUT_S = 20L
    private const val READ_TIMEOUT_S = 60L
}
