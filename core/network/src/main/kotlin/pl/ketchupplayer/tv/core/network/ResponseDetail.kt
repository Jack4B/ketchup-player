package pl.ketchupplayer.tv.core.network

import okhttp3.Response
import pl.ketchupplayer.tv.core.common.Redactor
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val DETAIL_BODY_BYTES = 512L
private const val DETAIL_MAX_CHARS = 160
private const val RETRY_AFTER_MAX_SECONDS = 999_999L
private val DELTA_SECONDS = Regex("""\d{1,9}""")
private val SECRET_PARAM = Regex("""(?i)(username|password|token|user|pass|secret)=[^&\s"'<>]+""")
private val URL_USERINFO = Regex("""://[^/\s@]+@""")
private val TAGS = Regex("<[^>]*>")
private val SPACES = Regex("\\s+")

/**
 * Zamienia odpowiedź błędu na wyjątek z krótkim, bezpiecznym opisem. Treść i nagłówki odpowiedzi serwera są nieufne
 * (mogą zawierać adres z danymi dostępu bez schematu, np. „The requested URL /u/p/77 was not found”, którego reguła
 * URL w [Redactor] nie rozpozna), więc NIE są przepisywane do komunikatu: nagłówek `Server` pomijamy (nie jest
 * potrzebny funkcjonalnie), `Retry-After` przekazujemy tylko po sparsowaniu do liczby sekund ([parseRetryAfter]),
 * a z treści bierzemy wyłącznie stałe, własne podpowiedzi ([bodyHint]).
 */
internal fun Response.toHttpFailure(now: Instant = Instant.now()): NetworkException.Http {
    val body = runCatching { peekBody(DETAIL_BODY_BYTES).string() }.getOrDefault("")
    val parts = mutableListOf<String>()
    header("Retry-After")?.let { parseRetryAfter(it, now) }?.let { parts += "ponów po: $it s" }
    bodyHint(body)?.let { parts += "«$it»" }
    return NetworkException.Http(code, parts.joinToString(", ").ifEmpty { null })
}

/**
 * Wartość `Retry-After` jako liczba sekund (0..[RETRY_AFTER_MAX_SECONDS]) albo null. Obsługiwane formaty z RFC 9110:
 * liczba całkowita sekund i data HTTP (RFC 1123, liczona względem [now]). Cokolwiek innego, w tym wartość spoza
 * zakresu, jest odrzucane; surowa wartość nigdy nie trafia do komunikatu.
 */
internal fun parseRetryAfter(
    raw: String,
    now: Instant,
): Long? {
    val value = raw.trim()
    val seconds =
        if (DELTA_SECONDS.matches(value)) {
            value.toLongOrNull()
        } else {
            runCatching {
                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() - now.epochSecond
            }.getOrNull()?.coerceAtLeast(0)
        }
    return seconds?.takeIf { it in 0..RETRY_AFTER_MAX_SECONDS }
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
