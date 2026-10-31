package pl.ketchupplayer.tv.core.model

import java.text.Normalizer

/** Rodzaj treści z listy dostawcy. */
enum class ContentType { LIVE, VOD, SERIES }

/**
 * Kanał (lub pozycja VOD) z listy dostawcy. [id] jest stabilny między odświeżeniami listy.
 * [streamUrl] to adres bez rozwiązywania (M3U); dla Xtream/Stalker adres powstaje przy `resolveStream`.
 */
data class Channel(
    val id: String,
    val providerId: ProviderId,
    val name: String,
    val streamUrl: String?,
    val number: Int? = null,
    val epgId: String? = null,
    val logo: String? = null,
    val group: String? = null,
    val type: ContentType = ContentType.LIVE,
    val userAgent: String? = null,
) {
    /** Nazwa do wyświetlania: bez przedrostka dostawcy typu `PL| ` czy `PL-VIP| `. */
    val displayName: String get() = cleanDisplayName(name)

    /** Nazwa znormalizowana (EPG, wyszukiwanie); ten sam kanał u różnych dostawców ma ten sam klucz. */
    val normName: String get() = normalizeName(displayName)

    companion object {
        private val diacritics = Regex("\\p{M}+")
        private val nonAlnum = Regex("[^a-z0-9]+")
        private val providerPrefix = Regex("^[A-Za-z0-9+& ]{1,12}(-[A-Za-z0-9+& ]{1,12})?\\|\\s*")

        /** `PL-VIP| TVN 24 RAW` -> `TVN 24 RAW`; nazwa zawierająca tylko przedrostek zostaje bez zmian. */
        fun cleanDisplayName(raw: String): String {
            val cleaned = raw.replace(providerPrefix, "").trim()
            return cleaned.ifEmpty { raw }
        }

        /** `Wiadomości HD` -> `wiadomosci hd`; bez znaków diakrytycznych, interpunkcji i podwójnych spacji. */
        fun normalizeName(raw: String): String =
            Normalizer
                .normalize(raw.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
                .replace(diacritics, "")
                .replace(nonAlnum, " ")
                .trim()
    }
}

/** Gotowy do odtworzenia strumień po rozwiązaniu przez dostawcę. */
data class StreamSpec(
    val url: String,
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
