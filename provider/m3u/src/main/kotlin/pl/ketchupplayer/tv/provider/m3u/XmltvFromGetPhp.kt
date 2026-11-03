package pl.ketchupplayer.tv.provider.m3u

import java.net.URI
import java.net.URLDecoder

/**
 * Lista z panelu typu Xtream (`.../get.php?username=U&password=P&type=m3u_plus`) ma przewodnik pod `xmltv.php`
 * z tymi samymi danymi. Inne odtwarzacze wyliczają ten adres, gdy sama lista nie podaje `url-tvg`.
 */
internal object XmltvFromGetPhp {
    fun derive(listUrl: String): String? {
        val uri = runCatching { URI(listUrl) }.getOrNull() ?: return null
        val path = uri.rawPath.orEmpty()
        if (!path.endsWith("/get.php") || uri.host == null) return null
        val query =
            uri.rawQuery
                .orEmpty()
                .split('&')
                .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (k, v) -> k to v }
        val user = query["username"]?.takeIf { it.isNotBlank() } ?: return null
        val pass = query["password"]?.takeIf { it.isNotBlank() } ?: return null
        val base = path.removeSuffix("get.php")
        val port = if (uri.port > 0) ":${uri.port}" else ""
        // Wartości zostają w postaci z adresu (już zakodowane), żeby specjalne znaki w haśle przeszły bez zmian.
        check(URLDecoder.decode(user, "UTF-8").isNotEmpty())
        return "${uri.scheme}://${uri.host}$port${base}xmltv.php?username=$user&password=$pass"
    }
}
