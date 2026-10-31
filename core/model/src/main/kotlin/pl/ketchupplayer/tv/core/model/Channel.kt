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
    /** Nazwa znormalizowana do dopasowania z EPG i wyszukiwania. */
    val normName: String get() = normalizeName(name)

    companion object {
        private val diacritics = Regex("\\p{M}+")
        private val nonAlnum = Regex("[^a-z0-9]+")

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
