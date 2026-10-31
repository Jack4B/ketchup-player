package pl.ketchupplayer.tv.core.model

enum class EpgSourceKind { OVH, TELEMAN, CUSTOM_XMLTV }

/**
 * Źródło EPG. [providerId] == null oznacza „wszyscy dostawcy”.
 * [url] jest null dla Telemana (scraping, bez URL XMLTV).
 */
data class EpgSource(
    val id: String,
    val name: String,
    val kind: EpgSourceKind,
    val url: String?,
    val enabled: Boolean,
    val providerId: ProviderId? = null,
) {
    val builtIn: Boolean get() = kind != EpgSourceKind.CUSTOM_XMLTV
}

object BuiltInEpgSources {
    const val OVH_ID = "builtin.ovh"
    const val TELEMAN_ID = "builtin.teleman"
    const val OVH_URL = "https://epg.ovh/pltv.xml.gz"

    /** Teleman domyślnie WYŁ. (KONSPEKT §13.3 — decyzja właściciela 🔒 w ADR docs/adr/teleman.md). */
    fun ovh(enabled: Boolean = true) = EpgSource(OVH_ID, "epg.ovh (XMLTV)", EpgSourceKind.OVH, OVH_URL, enabled)

    fun teleman(enabled: Boolean = false) = EpgSource(TELEMAN_ID, "Teleman.pl", EpgSourceKind.TELEMAN, null, enabled)
}
