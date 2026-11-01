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
    val AUTO = UserAgentProfile("auto", "Domyślny (jak BK Player)", null)
    val BK = UserAgentProfile("bk", "BK Player", NetworkClients.DEFAULT_USER_AGENT)
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

    val WINK = UserAgentProfile("wink", "Wink", "Wink/1.31.1")
    val PREMIER = UserAgentProfile("premier", "PremierLeagueModPlayer", "PremierLeagueModPlayer")
    val EXO = UserAgentProfile("exo", "ExoPlayer", "ExoPlayerLib/2.19.1")

    /** Wszystkie profile do wyboru w ustawieniach; pierwszy to tryb automatyczny. */
    val ALL = listOf(AUTO, BK, VLC, TIVIMATE, SMARTERS, KODI, WINK, PREMIER, EXO, CHROME)

    private const val CUSTOM_PREFIX = "custom:"

    /** Własny wpis użytkownika: identyfikator niesie samą wartość, więc działa bez rejestru. */
    fun custom(value: String): UserAgentProfile = UserAgentProfile(CUSTOM_PREFIX + value, value, value)

    fun byId(id: String?): UserAgentProfile =
        when {
            id == null -> AUTO
            id.startsWith(CUSTOM_PREFIX) -> custom(id.removePrefix(CUSTOM_PREFIX))
            else -> ALL.firstOrNull { it.id == id } ?: AUTO
        }

    /** Jak w BK Playerze: do 512 znaków, tylko drukowalne ASCII (bez enterów); zwraca oczyszczoną wartość albo null. */
    fun validated(raw: String): String? =
        raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH && it.all { c -> c in ' '..'~' } }

    private const val MAX_LENGTH = 512
}
