package pl.ketchupplayer.tv.core.model

/**
 * Czyszczenie tytułów filmów i seriali z paneli IPTV: przedrostki platformy/języka (`A+ - `, `PL - `, `|EN| `,
 * `NETFLIX - `), znaczniki jakości i języka w nawiasach (`(MULTI SUB)`, `[4K]`) i bez nawiasów (`PL`, `4K UHD`,
 * `1080p BluRay x264`), rok w nawiasach okrągłych i kwadratowych oraz bez nawiasów na końcu nazwy (`Matrix 1999`,
 * `The.Matrix.1999.1080p`). Własne nawiasy tytułu, jak `(US)` albo `(500) Days of Summer`, zostają.
 *
 * Ostrożność wobec zwykłych słów: znaczniki bez nawiasów usuwamy tylko z końca nazwy i tylko wielkimi literami
 * (`PL`, `HD`), więc `Charlotte's Web` zostaje; krótki przedrostek z dwukropkiem (`Saw: Spiral`, `Ice: Age`) zostaje,
 * bo przedrostki języka i platform są pisane wielkimi literami. Rok bez nawiasów wyciągamy tylko z końca nazwy
 * i zakresu 1900-2029 (`Blade Runner 2049`, `1917` zostają). Znany koszt: tytuły kończące się rokiem
 * (`Wonder Woman 1984`) dostają rok jako wskazówkę; indeks filmów próbuje wtedy też pełnego tytułu z liczbą.
 */
object VodTitle {
    /** Wynik czyszczenia: tytuł do wyszukiwania i rok (jeśli rozpoznany). */
    data class Parsed(
        val title: String,
        val year: Int?,
    )

    private const val YEAR = """(?:19\d{2}|20[0-2]\d)"""
    private const val MAX_INPUT = 300
    private const val MAX_PREFIXES = 3
    private const val MIN_DOTS_FOR_RELEASE = 2

    private val bracketYear = Regex("""[(\[]\s*($YEAR)\s*[)\]]""")
    private val yearToken = Regex("^$YEAR$")

    /** `|PL|`, `| PL |`. */
    private val pipeTag = Regex("""^\|\s*[A-Za-z]{2,3}\s*\|\s*""")

    /** `PL - `, `PL|`, `ENG-` (2-3 WIELKIE litery) oraz `PL: ` (dwukropek tylko dla znanych kodów języka). */
    private val langTag =
        Regex("""^(?:[A-Z]{2,3}\s*[-|]\s*|(?:PL|EN|ENG|DE|FR|ES|IT|RU|CZ|SK|TR)\s*:\s*)""")

    /** `A+ - `, `NF - `, `D+ - ` (skrót platformy: WIELKIE litery/cyfry albo z plusem). */
    private val platformTag = Regex("""^(?:[A-Z0-9]{1,5}\+?|[A-Za-z0-9]{1,4}\+)\s+[-|:]\s+""")

    /** `NETFLIX - `, `HBO | `, `DISNEY+ - `: nazwa platformy i wyraźny separator (`Prime Suspect` zostaje). */
    private val platformName =
        Regex(
            """^(?i:NETFLIX|HBO(?:\s+MAX)?|DISNEY\+?|AMAZON(?:\s+PRIME)?|PRIME(?:\s+VIDEO)?|APPLE(?:\s+TV\+?)?""" +
                """|SKYSHOWTIME|CANAL\+|CDA|RAKUTEN|HULU|PARAMOUNT\+?|SHOWTIME|PLAYER|VOD|MAX)\s*(?:\|\s*|\s[-:]\s)""",
        )

    private val bracketGroup = Regex("""\s*[(\[]([^()\[\]]{1,30})[)\]]""")
    private val spaces = Regex("""\s{2,}""")
    private val whitespace = Regex("""\s+""")
    private val releaseSeparators = Regex("""[._]""")
    private val releaseCodec = Regex("""(?i)^[xh]\.?26[45](?:-.*)?$""")
    private val channelsToken = Regex("""^\d\.\d$""")

    /** Znaczniki w nawiasach (bez względu na wielkość liter) oraz po nawiasach rozdzielane spacją/myślnikiem. */
    private val bracketTokens =
        setOf(
            "MULTI",
            "SUB",
            "SUBS",
            "MULTISUB",
            "DUB",
            "PL",
            "EN",
            "ENG",
            "DUAL",
            "AUDIO",
            "PLSUB",
            "SUBPL",
            "IVO",
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
            "X264",
            "X265",
            "HEVC",
            "1080P",
            "720P",
            "2160P",
        )

    /** Znaczniki bez nawiasów na końcu nazwy: tylko wielkimi literami (żeby `Web`, `Dub` w tytułach zostały). */
    private val upperSuffixTokens =
        setOf(
            "PL",
            "EN",
            "ENG",
            "DUAL",
            "MULTI",
            "SUB",
            "SUBS",
            "MULTISUB",
            "DUB",
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
            "HEVC",
            "REMUX",
        )

    /** Znaczniki wydań (rozdzielczość, źródło, kodek), niezależnie od wielkości liter. */
    private val releaseTokens =
        setOf(
            "1080p",
            "720p",
            "2160p",
            "480p",
            "bluray",
            "brrip",
            "bdrip",
            "webrip",
            "web-dl",
            "webdl",
            "dvdrip",
            "hdtv",
            "hdrip",
            "aac",
            "ac3",
            "dts",
            "remux",
            "repack",
            "proper",
            "hevc",
            "dd5",
            "ddp5",
        )

    private val separatorTokens = setOf("-", "|", "/", ":", "–", "—")

    fun clean(raw: String): String = parse(raw).title

    fun year(raw: String): Int? = parse(raw).year

    fun parse(raw: String): Parsed {
        var text = raw.take(MAX_INPUT).trim()
        val release = normalizeRelease(text)
        text = release ?: text
        text = stripPrefixes(text)
        var year: Int? = null
        bracketYear.find(text)?.let { year = it.groupValues[1].toInt() }
        text = text.replace(bracketYear, " ")
        text = text.replace(bracketGroup) { m -> if (isBracketNoise(m.groupValues[1])) "" else m.value }
        val tokens =
            text
                .trim()
                .split(whitespace)
                .filter { it.isNotEmpty() }
                .toMutableList()
        if (year == null) year = cutBareYear(tokens, release != null)
        stripSuffix(tokens)
        val cleaned =
            tokens
                .joinToString(" ")
                .replace(spaces, " ")
                .trim()
                .trimEnd('-', ':', '|')
                .trimStart('-', '|')
                .trim()
        return if (cleaned.isEmpty()) Parsed(raw, year) else Parsed(cleaned, year)
    }

    /** `The.Matrix.1999.1080p` -> `The Matrix 1999 1080p`; tylko gdy nazwa wygląda na wydanie (rok albo znacznik). */
    private fun normalizeRelease(text: String): String? {
        if (text.any { it.isWhitespace() }) return null
        if (text.count { it == '.' || it == '_' } < MIN_DOTS_FOR_RELEASE) return null
        val parts = text.split(releaseSeparators).filter { it.isNotEmpty() }
        val looksLikeRelease = parts.any { yearToken.matches(it) || isReleaseToken(it) }
        return if (looksLikeRelease) parts.joinToString(" ") else null
    }

    private fun stripPrefixes(input: String): String {
        var text = input
        repeat(MAX_PREFIXES) {
            val next =
                listOf(pipeTag, langTag, platformName, platformTag)
                    .firstNotNullOfOrNull { rx -> rx.find(text)?.let { text.substring(it.range.last + 1) } }
                    ?: return text
            text = next.trimStart()
        }
        return text
    }

    /**
     * Rok bez nawiasów: ostatni token-rok (nie pierwszy), po którym zostają tylko znaczniki jakości; w nazwie wydania
     * (`The.Matrix.1999.1080p.x264-GRP`) odcinamy wszystko po roku.
     */
    private fun cutBareYear(
        tokens: MutableList<String>,
        release: Boolean,
    ): Int? {
        val i = tokens.indexOfLast { yearToken.matches(it) }
        if (i <= 0) return null
        val after = tokens.subList(i + 1, tokens.size)
        if (!release && !after.all { isSuffixNoise(it) }) return null
        val year = tokens[i].toInt()
        while (tokens.size > i) tokens.removeAt(tokens.size - 1)
        return year
    }

    private fun stripSuffix(tokens: MutableList<String>) {
        while (tokens.size > 1 && isSuffixNoise(tokens.last())) tokens.removeAt(tokens.size - 1)
    }

    private fun isSuffixNoise(token: String): Boolean =
        token in separatorTokens || token in upperSuffixTokens || isReleaseToken(token)

    private fun isReleaseToken(token: String): Boolean =
        token.lowercase() in releaseTokens || releaseCodec.matches(token) || channelsToken.matches(token)

    private fun isBracketNoise(content: String): Boolean {
        val tokens = content.uppercase().split(' ', '-', '+', '/', ',', '.', '_').filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.all { it in bracketTokens }
    }
}
