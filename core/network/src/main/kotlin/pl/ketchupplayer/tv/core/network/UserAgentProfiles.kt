package pl.ketchupplayer.tv.core.network

/** Gotowy nagłówek User-Agent do wyboru (bez wpisywania). [value] null = automatyczny. */
data class UserAgentProfile(
    val id: String,
    val label: String,
    val value: String?,
)

/**
 * Nagłówki, które panele IPTV zwykle przepuszczają. Wartości są ogólnymi identyfikatorami odtwarzaczy
 * i przeglądarek; to nie jest podszywanie się pod konto ani logowanie.
 */
object UserAgentProfiles {
    val AUTO = UserAgentProfile("auto", "Automatyczny", null)
    val VLC = UserAgentProfile("vlc", "VLC", "VLC/3.0.20 LibVLC/3.0.20")
    val TIVIMATE = UserAgentProfile("tivimate", "TiviMate", "TiviMate/5.0.0 (Android 12)")
    val SMARTERS = UserAgentProfile("smarters", "IPTV Smarters", "IPTVSmartersPro")
    val KODI = UserAgentProfile("kodi", "Kodi", "Kodi/20.2 (Linux; Android 12) Android/12 Sys_CPU/aarch64")
    val CHROME =
        UserAgentProfile(
            "chrome",
            "Chrome",
            "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36",
        )

    /** Wszystkie profile do wyboru w ustawieniach; pierwszy to tryb automatyczny. */
    val ALL = listOf(AUTO, VLC, TIVIMATE, SMARTERS, KODI, CHROME)

    /** Kolejność prób przy błędzie połączenia w trybie automatycznym (po domyślnym VLC z klienta). */
    val FALLBACKS = listOf(TIVIMATE, SMARTERS, KODI, CHROME)

    fun byId(id: String?): UserAgentProfile = ALL.firstOrNull { it.id == id } ?: AUTO
}
