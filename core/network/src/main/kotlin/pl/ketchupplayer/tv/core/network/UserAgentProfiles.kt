package pl.ketchupplayer.tv.core.network

/** Gotowy nagłówek User-Agent do wyboru (bez wpisywania). [value] null = automatyczny. */
data class UserAgentProfile(
    val id: String,
    val label: String,
    val value: String?,
) {
    /** Własny User-Agent to dane dostępu: nie wypisujemy go w logach ani komunikatach. */
    override fun toString() = if (id.startsWith("custom:")) "UserAgentProfile(custom)" else "UserAgentProfile($id)"
}

/**
 * Nagłówki, które panele IPTV zwykle przepuszczają. Wartości są ogólnymi identyfikatorami odtwarzaczy
 * i przeglądarek; to nie jest podszywanie się pod konto ani logowanie.
 */
object UserAgentProfiles {
    val AUTO = UserAgentProfile("auto", "Domyślny (okhttp/4.10.0)", null)
    val OTT = UserAgentProfile("ott", "OTT Navigator", NetworkClients.OTT_USER_AGENT)
    val BK = UserAgentProfile("bk", "BK Player", "BK-player-Next/1.0")
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
    val ALL = listOf(AUTO, VLC, TIVIMATE, SMARTERS, OTT, BK, KODI, WINK, PREMIER, EXO, CHROME)

    private const val CUSTOM_PREFIX = "custom:"

    /** Pozycja „Własny…” w oknach wyboru: nie jest profilem, otwiera pole do wpisania własnej wartości dla listy. */
    val CUSTOM_ENTRY = UserAgentProfile(CUSTOM_PREFIX, "Własny…", null)

    /** Czy identyfikator oznacza własny User-Agent (wartość za prefiksem `custom:`). */
    fun isCustom(id: String): Boolean = id.startsWith(CUSTOM_PREFIX)

    /** Wartość własnego User-Agent z identyfikatora (pusta, gdy dopiero wybrano „Własny…”). */
    fun customValue(id: String): String = id.removePrefix(CUSTOM_PREFIX)

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
