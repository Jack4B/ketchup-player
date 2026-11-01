package pl.ketchupplayer.tv.provider.m3u

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ContentType
import pl.ketchupplayer.tv.core.model.ProviderId
import java.security.MessageDigest

/** Wynik parsowania listy M3U. [epgUrl] pochodzi z `url-tvg`/`x-tvg-url` nagłówka `#EXTM3U`. */
data class M3uPlaylist(
    val channels: List<Channel>,
    val epgUrl: String?,
    val skippedLines: Int,
    /** Liczba filmów/odcinków pominiętych przy `liveOnly` (do komunikatu dla użytkownika). */
    val omittedVod: Int = 0,
)

/**
 * Parser M3U/M3U8 listy IPTV. Strumieniowy po liniach, odporny na BOM, CRLF, puste linie, brakujące atrybuty
 * i błędne wpisy (pomijane i liczone w [M3uPlaylist.skippedLines]). Czysta JVM, bez sieci.
 */
object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
    private val numberRegex = Regex("""^\s*(\d{1,5})\s*[.\-:)]?\s+""")

    fun parse(
        providerId: ProviderId,
        text: String,
        liveOnly: Boolean = false,
    ): M3uPlaylist = parse(providerId, text.lineSequence(), liveOnly)

    /** Wersja strumieniowa: linie czytane leniwie, pamięć zależy od liczby kanałów, nie od rozmiaru listy. */
    fun parse(
        providerId: ProviderId,
        lines: Sequence<String>,
        liveOnly: Boolean = false,
    ): M3uPlaylist {
        val channels = ArrayList<Channel>()
        val acc = Accumulator(providerId, liveOnly, channels)
        var epgUrl: String? = null
        var pending: Pending? = null
        var skipped = 0
        var omittedVod = 0
        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U") -> epgUrl = headerEpg(line)
                line.startsWith("#EXTINF") -> {
                    if (pending != null) skipped++
                    pending = parseExtinf(line)
                }
                line.startsWith("#") -> pending?.let { applyDirective(it, line) }
                else -> {
                    when (acc.accept(pending, line)) {
                        Outcome.SKIPPED -> skipped++
                        Outcome.OMITTED -> omittedVod++
                        Outcome.ADDED -> Unit
                    }
                    pending = null
                }
            }
        }
        if (pending != null) skipped++
        return M3uPlaylist(channels, epgUrl, skipped, omittedVod)
    }

    /** `#EXTGRP:` i `#EXTVLCOPT:` uzupełniają bieżący wpis; pozostałe komentarze są ignorowane. */
    private fun applyDirective(
        pending: Pending,
        line: String,
    ) {
        when {
            line.startsWith("#EXTGRP:") -> pending.group = line.removePrefix("#EXTGRP:").trim().ifEmpty { null }
            line.startsWith("#EXTVLCOPT:") -> applyVlcOpt(pending, line.removePrefix("#EXTVLCOPT:"))
        }
    }

    private enum class Outcome { ADDED, OMITTED, SKIPPED }

    private class Accumulator(
        private val providerId: ProviderId,
        private val liveOnly: Boolean,
        private val channels: MutableList<Channel>,
    ) {
        fun accept(
            pending: Pending?,
            line: String,
        ): Outcome =
            when {
                pending == null || !isStreamUrl(line) -> Outcome.SKIPPED
                liveOnly && detectType(line, pending.group) != ContentType.LIVE -> Outcome.OMITTED
                else -> {
                    channels += pending.toChannel(providerId, line, channels.size + 1)
                    Outcome.ADDED
                }
            }
    }

    private class Pending(
        val name: String,
        val tvgId: String?,
        val tvgNumber: Int?,
        val logo: String?,
        var group: String?,
        var userAgent: String? = null,
    ) {
        fun toChannel(
            providerId: ProviderId,
            url: String,
            position: Int,
        ): Channel =
            Channel(
                id = stableId(providerId, name, url),
                providerId = providerId,
                name = name,
                streamUrl = url,
                number = tvgNumber ?: position,
                epgId = tvgId,
                logo = logo,
                group = group,
                type = detectType(url, group),
                userAgent = userAgent,
            )
    }

    private fun parseExtinf(line: String): Pending {
        val comma = commaOutsideQuotes(line)
        val title = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val attrs =
            attrRegex
                .findAll(
                    if (comma >=
                        0
                    ) {
                        line.substring(0, comma)
                    } else {
                        line
                    },
                ).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val name = title.ifEmpty { attrs["tvg-name"].orEmpty() }.ifEmpty { "Bez nazwy" }
        val number =
            attrs["tvg-chno"]?.toIntOrNull() ?: attrs["channel-number"]?.toIntOrNull()
                ?: numberRegex
                    .find(name)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
        return Pending(
            name = name,
            tvgId = attrs["tvg-id"]?.ifBlank { null },
            tvgNumber = number,
            logo = attrs["tvg-logo"]?.ifBlank { null },
            group = attrs["group-title"]?.ifBlank { null },
        )
    }

    private fun commaOutsideQuotes(line: String): Int {
        var inQuotes = false
        for ((i, ch) in line.withIndex()) {
            if (ch == '"') inQuotes = !inQuotes
            if (ch == ',' && !inQuotes) return i
        }
        return -1
    }

    private fun headerEpg(line: String): String? {
        val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return (attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: attrs["tvg-url"])
            ?.split(
                ',',
            )?.firstOrNull()
            ?.trim()
            ?.ifBlank { null }
    }

    private fun applyVlcOpt(
        pending: Pending,
        opt: String,
    ) {
        val (key, value) = opt.split('=', limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
        if (key == "http-user-agent" && value.isNotEmpty()) pending.userAgent = value
    }

    private fun isStreamUrl(line: String): Boolean = line.contains("://")

    private fun detectType(
        url: String,
        group: String?,
    ): ContentType =
        when {
            "/series/" in url -> ContentType.SERIES
            "/movie/" in url -> ContentType.VOD
            group?.startsWith("VOD", ignoreCase = true) == true -> ContentType.VOD
            else -> ContentType.LIVE
        }

    /** Stabilny identyfikator: ten sam kanał ma to samo id po odświeżeniu listy. */
    private fun stableId(
        providerId: ProviderId,
        name: String,
        url: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("$name|$url".toByteArray())
        return providerId.value + ":" + digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
