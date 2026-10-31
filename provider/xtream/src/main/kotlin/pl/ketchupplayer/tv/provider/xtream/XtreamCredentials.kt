package pl.ketchupplayer.tv.provider.xtream

import java.net.URI
import java.net.URLEncoder

data class XtreamCredentials(
    val server: String,
    val username: String,
    val password: String,
) {
    /** Zawiera hasło — nie logować ani nie zapisywać poza `SecretStore`. */
    override fun toString() = "XtreamCredentials(server=$server, username=***, password=***)"

    fun apiUrl(
        action: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val params =
            buildList {
                add("username" to username)
                add("password" to password)
                if (action != null) add("action" to action)
                extra.forEach { (k, v) -> add(k to v) }
            }
        return "$server/player_api.php?" + params.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    fun xmltvUrl(): String = "$server/xmltv.php?username=${enc(username)}&password=${enc(password)}"

    fun liveUrl(
        streamId: String,
        ext: String,
    ): String = "$server/live/${enc(username)}/${enc(password)}/$streamId.$ext"

    private fun enc(v: String) = URLEncoder.encode(v, Charsets.UTF_8).replace("+", "%20")

    companion object {
        private val knownSuffixes = listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php", "/c", "/")

        /**
         * Normalizuje adres serwera wklejony przez użytkownika: `http://host:8080`, `http://host:8080/c/`,
         * `http://host/player_api.php?...` -> `http://host[:port][/prefiks]`. Zwraca null dla adresów spoza http(s).
         */
        fun normalizeServer(raw: String): String? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) return null
            var path = uri.path.orEmpty()
            var changed = true
            while (changed) {
                changed = false
                for (s in knownSuffixes) {
                    if (path.endsWith(s) && path.isNotEmpty()) {
                        path = path.removeSuffix(s)
                        changed = true
                    }
                }
            }
            val port = if (uri.port > 0) ":${uri.port}" else ""
            return "$scheme://${uri.host}$port$path"
        }
    }
}
