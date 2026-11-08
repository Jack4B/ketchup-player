package pl.ketchupplayer.tv.core.network

import okhttp3.Response
import pl.ketchupplayer.tv.core.common.Redactor

private const val DETAIL_BODY_BYTES = 512L
private const val DETAIL_MAX_CHARS = 160
private val SECRET_PARAM = Regex("""(?i)(username|password|token|user|pass|secret)=[^&\s"'<>]+""")
private val URL_USERINFO = Regex("""://[^/\s@]+@""")
private val TAGS = Regex("<[^>]*>")
private val SPACES = Regex("\\s+")

/**
 * Zamienia odpowiedź błędu na wyjątek z krótkim, bezpiecznym opisem. Treść odpowiedzi serwera jest nieufna (może
 * zawierać adres z danymi dostępu bez schematu, np. „The requested URL /u/p/77 was not found”, którego reguła URL
 * w [Redactor] nie rozpozna), więc NIE jest przepisywana do komunikatu: z treści wyciągamy tylko stałe, własne
 * podpowiedzi ([bodyHint]). Nagłówki przechodzą przez [sanitizeDetail].
 */
internal fun Response.toHttpFailure(): NetworkException.Http {
    val body = runCatching { peekBody(DETAIL_BODY_BYTES).string() }.getOrDefault("")
    val parts = mutableListOf<String>()
    header("Server")?.let { parts += "serwer: ${sanitizeDetail(it)}" }
    header("Retry-After")?.let { parts += "ponów po: ${sanitizeDetail(it)}" }
    bodyHint(body)?.let { parts += "«$it»" }
    return NetworkException.Http(code, parts.joinToString(", ").ifEmpty { null })
}

/** Stałe, własne podpowiedzi rozpoznane po słowach kluczowych treści; nigdy fragment treści serwera. */
internal fun bodyHint(body: String): String? {
    val text = body.lowercase()
    return when {
        "too many connections" in text || "max connections" in text -> "zbyt wiele połączeń"
        "blocked" in text || "banned" in text -> "dostęp zablokowany"
        "not found" in text -> "nie znaleziono zasobu"
        else -> null
    }
}

/**
 * Usuwa znaczniki HTML, białe znaki i wszystko, co przypomina login/hasło/token (także adresy z danymi dostępu w
 * treści odpowiedzi, przez [Redactor]); przycina do kilku zdań. Przycięcie następuje PO maskowaniu.
 */
fun sanitizeDetail(raw: String): String =
    Redactor
        .redact(
            raw
                .replace(TAGS, " ")
                .replace(SECRET_PARAM) { "${it.groupValues[1]}=***" }
                .replace(URL_USERINFO, "://***@")
                .replace(SPACES, " ")
                .trim(),
        ).take(DETAIL_MAX_CHARS)
