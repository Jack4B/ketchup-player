package pl.ketchupplayer.tv.epg.radar

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme

/** Audycja pasująca do słowa kluczowego z „Moich zainteresowań”. */
data class InterestHit(
    val keyword: String,
    val channel: Channel,
    val programme: EpgProgramme,
)

/**
 * Dopasowanie słów kluczowych użytkownika (np. „Premier League”, „Kleks”) do audycji z EPG. Porównanie bez wielkości
 * liter i polskich znaków (`Channel.normalizeName`); słowo pasuje, gdy występuje w tytule albo opisie audycji.
 * Wynik: najbliższe w czasie najpierw, bez audycji, które już się skończyły.
 */
object InterestMatcher {
    const val DEFAULT_LIMIT = 100

    fun match(
        keywords: List<String>,
        channels: List<Channel>,
        programmes: List<EpgProgramme>,
        nowMs: Long,
        limit: Int = DEFAULT_LIMIT,
    ): List<InterestHit> {
        val wanted = keywords.map { it to Channel.normalizeName(it) }.filter { it.second.isNotEmpty() }
        if (wanted.isEmpty()) return emptyList()
        val byId = channels.associateBy { it.id }
        val hits =
            programmes.mapNotNull { p ->
                val channel = byId[p.channelId]
                val keyword = if (channel == null || p.endMs <= nowMs) null else firstMatch(wanted, p)
                if (channel != null && keyword != null) InterestHit(keyword, channel, p) else null
            }
        return hits.sortedWith(compareBy({ it.programme.startMs }, { it.channel.number ?: Int.MAX_VALUE })).take(limit)
    }

    private fun firstMatch(
        wanted: List<Pair<String, String>>,
        p: EpgProgramme,
    ): String? {
        val title = Channel.normalizeName(p.title)
        val description = p.description?.let { Channel.normalizeName(it) }.orEmpty()
        return wanted.firstOrNull { (_, norm) -> norm in title || norm in description }?.first
    }
}
