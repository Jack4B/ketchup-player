package pl.ketchupplayer.tv.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Przy przekierowaniu na INNY host przekazujemy dalej tylko nagłówki tożsamości klienta i zakresu (User-Agent, Accept*,
 * Range, Connection, Icy-MetaData). Wszystko inne, czyli `Authorization`, `Cookie`, klucze API w nagłówkach i inne
 * dane uwierzytelniające, zostaje na hoście, do którego je wysłano. Dane w adresie przekierowania (`Location`) wybiera
 * serwer, my niczego do nich nie dopisujemy.
 */
internal object CrossHostGuard : Interceptor {
    private val forwarded =
        setOf(
            "user-agent",
            "accept",
            "accept-encoding",
            "accept-language",
            "range",
            "connection",
            "host",
            "icy-metadata",
        )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host ==
            chain
                .call()
                .request()
                .url.host
        ) {
            return chain.proceed(request)
        }
        val builder = request.newBuilder()
        request.headers
            .names()
            .filter { it.lowercase() !in forwarded }
            .forEach { builder.removeHeader(it) }
        return chain.proceed(builder.build())
    }
}
