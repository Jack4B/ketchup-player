package pl.ketchupplayer.tv.core.model

/**
 * Czyszczenie tytułów filmów i seriali z paneli IPTV: przedrostki platformy/języka (`A+ - `, `PL - `, `|EN| `),
 * znaczniki jakości i języka w nawiasach (`(MULTI SUB)`, `[4K]`) oraz rok w nawiasie. Własne nawiasy tytułu,
 * jak `(US)` albo `(500) Days of Summer`, zostają.
 */
object VodTitle {
    private val yearRegex = Regex("""\((19|20)\d{2}\)""")

    /** `PL - `, `PL-`, `A+ - `, `NF - ` (skrót platformy: 1-5 znaków, z plusem tylko ze spacjami wokół myślnika). */
    private val shortTag = Regex("""^[A-Za-z]{2,3}\s*[-|:]\s*""")
    private val platformTag = Regex("""^[A-Za-z0-9]{1,5}\+?\s+[-|:]\s+""")
    private val pipeTag = Regex("""^\|[A-Za-z]{2,3}\|\s*""")
    private val bracketGroup = Regex("""\s*[(\[]([^()\[\]]{1,30})[)\]]""")
    private val spaces = Regex("""\s{2,}""")

    private val qualityTokens =
        setOf(
            "MULTI",
            "SUB",
            "SUBS",
            "MULTISUB",
            "DUB",
            "PL",
            "EN",
            "LEKTOR",
            "NAPISY",
            "DUBBING",
            "4K",
            "UHD",
            "FHD",
            "HD",
            "SD",
            "HDR",
            "HDR10",
            "DV",
            "ATMOS",
            "WEB",
            "WEBRIP",
            "BLURAY",
            "H264",
            "H265",
            "HEVC",
            "1080P",
            "720P",
            "2160P",
        )

    fun clean(raw: String): String {
        val cleaned =
            raw
                .replace(pipeTag, "")
                .replace(shortTag, "")
                .replace(platformTag, "")
                .replace(yearRegex, "")
                .replace(bracketGroup) { m -> if (isQualityGroup(m.groupValues[1])) "" else m.value }
                .replace(spaces, " ")
                .trim()
                .trimEnd('-', ':', '|')
                .trim()
        return cleaned.ifEmpty { raw }
    }

    fun year(raw: String): Int? =
        yearRegex
            .find(raw)
            ?.value
            ?.trim('(', ')')
            ?.toIntOrNull()

    private fun isQualityGroup(content: String): Boolean {
        val tokens = content.uppercase().split(' ', '-', '+', '/', ',').filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.all { it in qualityTokens }
    }
}
