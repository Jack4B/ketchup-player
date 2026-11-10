package pl.ketchupplayer.tv.core.model

/**
 * Metoda żądania listy i API dostawcy, wybierana per lista: GET (domyślnie) albo POST (dane w ciele
 * `application/x-www-form-urlencoded`, adres bez danych dostępu). Niektórzy dostawcy odrzucają GET.
 */
enum class RequestMethod {
    GET,
    POST,
    ;

    companion object {
        /** Wartość zapisana w bazie; nieznana lub pusta oznacza GET (domyślną). */
        fun parse(stored: String?): RequestMethod = entries.firstOrNull { it.name == stored } ?: GET
    }
}
