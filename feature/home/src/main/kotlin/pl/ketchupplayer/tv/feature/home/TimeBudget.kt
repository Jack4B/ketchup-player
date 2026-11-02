package pl.ketchupplayer.tv.feature.home

import pl.ketchupplayer.tv.core.data.vod.VodEntry

/** Filmy do dokończenia, które zmieszczą się w podanym czasie. */
data class FinishSuggestion(
    val entry: VodEntry,
    val remainingMin: Int,
)

/** Audycje na żywo, które skończą się w podanym czasie (a nie tuż po wejściu). */
data class LiveSuggestion(
    val item: ChannelNow,
    val remainingMin: Int,
)

data class TimeSuggestions(
    val finish: List<FinishSuggestion>,
    val live: List<LiveSuggestion>,
) {
    val isEmpty: Boolean get() = finish.isEmpty() && live.isEmpty()
}

/**
 * „Mam X minut”: z tego, co znamy (czas filmów zaczętych oraz audycje z EPG), wybiera pozycje, które da się obejrzeć
 * do końca w podanym czasie. Najlepiej pasujące (najdłuższe z mieszczących się) są pierwsze.
 */
object TimeBudget {
    /** Audycja, której zostało mniej, nie jest propozycją: nie ma sensu zaczynać oglądania na ostatnie minuty. */
    const val MIN_LIVE_REMAINING_MIN = 10
    private const val MS_PER_MIN = 60_000L

    fun pick(
        minutes: Int,
        continueVod: List<VodEntry>,
        live: List<ChannelNow>,
        nowMs: Long,
        limit: Int = DEFAULT_LIMIT,
    ): TimeSuggestions {
        val finish =
            continueVod
                .mapNotNull { e ->
                    val left = e.durationMs - e.positionMs
                    if (e.durationMs <= 0 || left <= 0) return@mapNotNull null
                    FinishSuggestion(e, ceilMinutes(left)).takeIf { it.remainingMin <= minutes }
                }.sortedByDescending { it.remainingMin }
                .take(limit)
        val onAir =
            live
                .mapNotNull { c ->
                    val p = c.now ?: return@mapNotNull null
                    if (!p.isOnAt(nowMs)) return@mapNotNull null
                    LiveSuggestion(c, ceilMinutes(p.endMs - nowMs))
                }.filter { it.remainingMin in MIN_LIVE_REMAINING_MIN..minutes }
                .sortedByDescending { it.remainingMin }
                .take(limit)
        return TimeSuggestions(finish, onAir)
    }

    private fun ceilMinutes(ms: Long): Int = ((ms + MS_PER_MIN - 1) / MS_PER_MIN).toInt()

    private const val DEFAULT_LIMIT = 8
}
