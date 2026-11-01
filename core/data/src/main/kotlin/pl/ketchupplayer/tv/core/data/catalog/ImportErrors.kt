package pl.ketchupplayer.tv.core.data.catalog

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
        code != null -> httpMessage(code)
        text.isBlank() -> "Nieznany błąd pobierania listy."
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
            "Serwer odpowiedział, ale to nie jest lista kanałów. Konto może być nieaktywne albo adres błędny."
        else -> networkMessage(text) ?: text
    }
}

private fun httpMessage(code: Int): String =
    when {
        code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN ->
            "Dostawca odmówił dostępu (HTTP $code). Konto może być nieaktywne lub wygasłe albo dane są błędne."
        code == HTTP_NOT_FOUND -> "Nie znaleziono listy pod tym adresem (HTTP 404). Sprawdź adres."
        code >= HTTP_SERVER_ERROR -> "Serwer dostawcy ma awarię (HTTP $code). Spróbuj później albo wpisz inną listę."
        else -> "Serwer dostawcy zwrócił błąd (HTTP $code). Sprawdź adres albo wpisz inną listę."
    }

private fun networkMessage(text: String): String? =
    when {
        "SocketTimeout" in text || "InterruptedIO" in text ->
            "Serwer nie odpowiada (przekroczony czas). Sprawdź adres i internet albo wpisz inną listę."
        "UnknownHost" in text -> "Nie znaleziono serwera. Sprawdź adres i połączenie z internetem."
        "SSL" in text || "Certificate" in text ->
            "Nie udało się nawiązać bezpiecznego połączenia (certyfikat). Spróbuj adresu z http."
        "ConnectException" in text || "NoRoute" in text -> "Nie można połączyć się z serwerem. Sprawdź adres i port."
        else -> null
    }
