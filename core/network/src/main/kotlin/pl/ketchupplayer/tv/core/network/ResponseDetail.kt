package pl.ketchupplayer.tv.core.network

import okhttp3.Response

private const val DETAIL_BODY_BYTES = 512L
private const val DETAIL_MAX_CHARS = 160
private val SECRET_PARAM = Regex("""(?i)(username|password|token|user|pass|secret)=[^&\s"'<>]+""")
private val URL_USERINFO = Regex("""://[^/\s@]+@""")
private val TAGS = Regex("<[^>]*>")
private val SPACES = Regex("\\s+")

/** Zamienia odpowiedź błędu na wyjątek z krótkim, bezpiecznym opisem (bez danych logowania). */
internal fun Response.toHttpFailure(): NetworkException.Http {
    val body = runCatching { peekBody(DETAIL_BODY_BYTES).string() }.getOrDefault("")
    val parts = mutableListOf<String>()
    header("Server")?.let { parts += "serwer: $it" }
    header("Retry-After")?.let { parts += "ponów po: $it" }
    sanitizeDetail(body).takeIf { it.isNotEmpty() }?.let { parts += "«$it»" }
    return NetworkException.Http(code, parts.joinToString(", ").ifEmpty { message.ifBlank { null } })
}

/** Usuwa znaczniki HTML, białe znaki i wszystko, co przypomina login/hasło/token; przycina do kilku zdań. */
fun sanitizeDetail(raw: String): String =
    raw
        .replace(TAGS, " ")
        .replace(SECRET_PARAM) { "${it.groupValues[1]}=***" }
        .replace(URL_USERINFO, "://***@")
        .replace(SPACES, " ")
        .trim()
        .take(DETAIL_MAX_CHARS)
