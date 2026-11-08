package pl.ketchupplayer.tv.core.data.catalog

/** Ogólna informacja o błędzie, gdy zapisany komunikat nie jest rozpoznanym, bezpiecznym tekstem. */
internal const val STORED_ERROR_GENERIC =
    "Ostatnie pobieranie listy nie powiodło się. Odśwież listę, aby spróbować ponownie."

private const val CLASS_NAME = """[A-Za-z][A-Za-z0-9_$.]{0,79}"""
private const val MAX_HTTP_CODE = 599
private const val MIN_HTTP_CODE = 100
private val LIMIT_MESSAGE = Regex("""Lista jest zbyt duża \(limit (\d{1,6}|\?) MB\)\. Wpisz mniejszą listę\.""")
private val CLASS_MESSAGE =
    Regex("""(Nie udało się pobrać listy|Błąd pobierania listy|Błąd sieci) \($CLASS_NAME\)|Błąd sieci: $CLASS_NAME""")
private val PLAIN_HTTP = Regex("""HTTP \d{3}""")
private val PROVIDER_HTTP = Regex("""(Serwer|Dostawca) odmówił dostępu \(HTTP \d{3}\)""")

/** Stałe komunikaty dostawców i importu, które mogą trafić do `lastError` (własne napisy, bez treści serwera). */
private val KNOWN_MESSAGES: Set<String> =
    setOf(
        MSG_UNKNOWN,
        MSG_NO_CHANNELS,
        MSG_TIMEOUT,
        MSG_HOST,
        MSG_CERT,
        MSG_CONNECT,
        VOD_SKIPPED,
        VOD_SKIPPED_MEMORY,
        "Za mało pamięci na tak dużą listę",
        "Nieprawidłowa odpowiedź",
        "Nieprawidłowa odpowiedź serwera",
        "Nieprawidłowy login lub hasło",
        "Konto nieaktywne lub wygasłe",
        "Nieprawidłowa lista kanałów",
        "Konto nie ma żadnych kanałów",
        "Lista nie zawiera żadnych kanałów ani filmów",
        "Lista nie zawiera filmów ani seriali",
    ) + (MIN_HTTP_CODE..MAX_HTTP_CODE).map(::httpMessage)

private fun isKnownMessage(head: String): Boolean =
    head in KNOWN_MESSAGES ||
        LIMIT_MESSAGE.matches(head) ||
        CLASS_MESSAGE.matches(head) ||
        PLAIN_HTTP.matches(head) ||
        PROVIDER_HTTP.matches(head)

/**
 * Komunikat zapisanego błędu (`providers.lastError`) w postaci bezpiecznej do pokazania w UI i raportach. Baza może
 * zawierać wpisy sprzed poprawki #12 z surowym tekstem serwera, także takim, którego `Redactor` nie rozpozna
 * (np. „The requested URL /u/p/77 was not found”). Dlatego przechodzą wyłącznie rozpoznane komunikaty aplikacji
 * (allow-list), a część „Odpowiedź serwera” tylko w ścisłym, własnym formacie; reszta daje [STORED_ERROR_GENERIC].
 * Stan błędu jest zachowany (null zostaje null, niepusty wpis zawsze daje niepusty komunikat). Bazy nie zmieniamy.
 */
internal fun presentStoredError(raw: String?): String? {
    if (raw == null) return null
    val head = raw.substringBefore("\n$DETAIL_PREFIX").trim()
    if (!isKnownMessage(head)) return STORED_ERROR_GENERIC
    if ("\n$DETAIL_PREFIX" !in raw) return head
    val parts = raw.substringAfter("\n$DETAIL_PREFIX").split(", ").map { it.trim() }
    return if (parts.isNotEmpty() && parts.all { SAFE_DETAIL_PART.matches(it) }) {
        "$head\n$DETAIL_PREFIX${parts.joinToString(", ")}"
    } else {
        head
    }
}
