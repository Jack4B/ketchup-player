package pl.ketchupplayer.tv.provider.m3u

import pl.ketchupplayer.tv.core.network.FormBody

/** Żądanie listy: adres i (przy POST) ciało form-urlencoded; ciało ma dane dostępu i nie jest logowane. */
class M3uRequest(
    val url: String,
    val body: FormBody?,
) {
    companion object {
        /** Parametry panelu Xtream (`get.php`), które przy POST idą do ciała zamiast do adresu. */
        private val moved = setOf("username", "password", "type", "output")

        /**
         * POST dla [url]: dla `get.php` oraz dla każdego adresu z `username` i `password` w zapytaniu parametry
         * `username`, `password`, `type` i `output` trafiają do ciała (wartości bez zmian, tak jak w adresie), a adres
         * zostaje bez nich; pozostałe parametry zostają w adresie. Dla innych adresów POST ma puste ciało, a adres
         * zostaje bez zmian. Fragment (`#...`) jest odrzucany, bo nie jest wysyłany do serwera.
         */
        fun post(url: String): M3uRequest {
            val noFragment = url.substringBefore('#')
            val base = noFragment.substringBefore('?')
            val query = if ('?' in noFragment) noFragment.substringAfter('?') else ""
            val pairs = query.split('&').filter { it.isNotEmpty() }

            fun key(pair: String) = pair.substringBefore('=').lowercase()
            val hasCredentials = pairs.any { key(it) == "username" } && pairs.any { key(it) == "password" }
            val isGetPhp = base.substringAfter("://", base).substringAfter('/', "").endsWith("get.php")
            if (!hasCredentials && !isGetPhp) return M3uRequest(noFragment, FormBody(""))
            val (inBody, inUrl) = pairs.partition { key(it) in moved }
            val cleanUrl = if (inUrl.isEmpty()) base else base + "?" + inUrl.joinToString("&")
            return M3uRequest(cleanUrl, FormBody(inBody.joinToString("&")))
        }
    }
}
