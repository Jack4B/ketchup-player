package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.common.ErrorText

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_SERVER_ERROR = 500
private const val BYTES_PER_MB = 1024 * 1024
private val HTTP_CODE = Regex("""HTTP (\d{3})""")
private val LIMIT_BYTES = Regex("""większa niż (\d+) B""")

/**
 * Zamienia techniczny błąd pobierania listy na komunikat zrozumiały dla użytkownika: co się stało i co zrobić.
 * Dostawcy opakowują błędy sieci we własne wyjątki, więc rozpoznajemy je po treści, nie po typie.
 */
fun friendlyImportError(raw: String?): String {
    val text = raw.orEmpty()
    val code =
        HTTP_CODE
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toInt()
    return when {
        code != null -> httpMessage(code) + serverDetail(text)
        text.isBlank() -> MSG_UNKNOWN
        "większa niż" in text -> {
            val mb =
                LIMIT_BYTES
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.toLong()
                    ?.div(BYTES_PER_MB)
            "Lista jest zbyt duża (limit ${mb ?: "?"} MB). Wpisz mniejszą listę."
        }
        "nie zawiera żadnych kanałów" in text ->
            MSG_NO_CHANNELS
        else -> networkMessage(text) ?: ErrorText.safe(text, MSG_UNKNOWN)
    }
}

/**
 * Fragment po „ · ” w komunikacie HTTP. Treść odpowiedzi i nagłówki serwera są nieufne, więc z tego fragmentu zostają
 * wyłącznie części o ścisłym, własnym formacie ([SAFE_DETAIL_PART]): znana podpowiedź w «…» albo „ponów po: N s”.
 * Wszystko inne (np. „serwer: …”, dawna treść odpowiedzi) jest pomijane, także z wpisów sprzed poprawki.
 */
private fun serverDetail(text: String): String {
    val i = text.indexOf(" · ")
    if (i < 0) return ""
    val detail =
        text
            .substring(i + 3)
            .split(", ")
            .map { it.trim() }
            .filter { SAFE_DETAIL_PART.matches(it) }
    return if (detail.isEmpty()) "" else "\n$DETAIL_PREFIX${detail.joinToString(", ")}".take(DETAIL_MAX)
}

private const val DETAIL_MAX = 200
internal const val DETAIL_PREFIX = "Odpowiedź serwera: "

/** Własny format części szczegółu: stała podpowiedź z `bodyHint` albo odczekanie z `Retry-After` w sekundach. */
internal val SAFE_DETAIL_PART =
    Regex("""«(zbyt wiele połączeń|dostęp zablokowany|nie znaleziono zasobu)»|ponów po: \d{1,6} s""")

internal fun httpMessage(code: Int): String =
    when {
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN ->
            "Dostawca odmówił dostępu (HTTP $code). Konto może być nieaktywne lub wygasłe albo dane są błędne."
        code == HTTP_NOT_FOUND -> "Nie znaleziono listy pod tym adresem (HTTP 404). Sprawdź adres."
        code >= HTTP_SERVER_ERROR -> "Serwer dostawcy ma awarię (HTTP $code). Spróbuj później albo wpisz inną listę."
        else -> "Serwer dostawcy zwrócił błąd (HTTP $code). Sprawdź adres albo wpisz inną listę."
    }

internal const val MSG_UNKNOWN = "Nieznany błąd pobierania listy."
internal const val MSG_NO_CHANNELS =
    "Serwer odpowiedział, ale to nie jest lista kanałów. Konto może być nieaktywne albo adres błędny."
internal const val MSG_TIMEOUT =
    "Serwer nie odpowiada (przekroczony czas). Sprawdź adres i internet albo wpisz inną listę."
internal const val MSG_HOST = "Nie znaleziono serwera. Sprawdź adres i połączenie z internetem."
internal const val MSG_CERT = "Nie udało się nawiązać bezpiecznego połączenia (certyfikat). Spróbuj adresu z http."
internal const val MSG_CONNECT = "Nie można połączyć się z serwerem. Sprawdź adres i port."

private fun networkMessage(text: String): String? =
    when {
        "SocketTimeout" in text || "InterruptedIO" in text -> MSG_TIMEOUT
        "UnknownHost" in text -> MSG_HOST
        "SSL" in text || "Certificate" in text -> MSG_CERT
        "ConnectException" in text || "NoRoute" in text -> MSG_CONNECT
        else -> null
    }
