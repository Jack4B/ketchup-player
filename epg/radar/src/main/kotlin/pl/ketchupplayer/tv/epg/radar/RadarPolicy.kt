package pl.ketchupplayer.tv.epg.radar

import kotlin.math.roundToInt

/** Zakładki Radaru (makieta v3, §10.3 konspektu): TOP DNIA, Teraz, Za chwilę, Wieczorem. */
enum class RadarSlot { TOP_DAY, NOW, SOON, EVENING }

/**
 * Czysta logika Radaru EPG, bez Androida i bez sieci. Port `RadarGuidePolicy` ze starej aplikacji
 * (fixture golden master wygenerowany kodem referencyjnym; docs/adr/legacy-behavior-decision.md).
 */
object RadarPolicy {
    const val MAX_RESULTS = 30
    const val SOON_WINDOW_MS = 90L * 60_000L

    /** Okno czasowe, do którego Radar liczy dany slot (granice dnia i wieczoru podaje wywołujący w swojej strefie). */
    data class Window(
        val dayStart: Long,
        val dayEnd: Long,
        val eveningStart: Long,
        val eveningEnd: Long,
    )

    fun isLive(
        now: Long,
        start: Long,
        end: Long,
    ) = now in start until end

    /** Audycja przyszła nigdy nie trafia do „Teraz”, nawet jeśli jest wysoko w rankingu. */
    fun inSlot(
        slot: RadarSlot,
        now: Long,
        start: Long,
        end: Long,
        w: Window,
    ): Boolean =
        when {
            end <= start -> false
            else ->
                when (slot) {
                    RadarSlot.TOP_DAY -> end > now && start < w.dayEnd && end > w.dayStart
                    RadarSlot.NOW -> isLive(now, start, end)
                    RadarSlot.SOON -> start > now && start <= now + SOON_WINDOW_MS
                    RadarSlot.EVENING -> end > now && end > w.eveningStart && start < w.eveningEnd
                }
        }

    private val ratingRegex = Regex("""(?i)(?:^|[^\p{L}])O\s*:\s*(10(?:[.,]0)?|[0-9](?:[.,][0-9])?)\s*/\s*10""")

    /** Ocena 0..100 (dziesiąte części) z opisu „O: 7,5/10”; null, gdy EPG nie podaje sprawdzalnej oceny. */
    fun ratingTenths(description: String?): Int? {
        if (description.isNullOrEmpty()) return null
        val value =
            ratingRegex
                .find(description)
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toFloatOrNull() ?: return null
        return if (value in 0f..10f) (value * 10f).roundToInt() else null
    }

    /**
     * Kolejność TOP DNIA: stabilna, z unikaniem trzech tych samych gatunków z rzędu, gdy jest równie dobra
     * alternatywa (nie więcej niż 16 punktów gorsza). Wejście jest już posortowane malejąco po wyniku.
     * Zwraca indeksy wejścia w nowej kolejności; nigdy nie wymyśla wyników.
     */
    fun balancedTopOrder(
        scores: IntArray,
        genres: IntArray,
    ): IntArray {
        val n = minOf(scores.size, genres.size)
        val result = IntArray(n)
        val seen = IntArray(GENRE_SLOTS)
        val selected = BooleanArray(n)
        for (pos in 0 until n) {
            val first = selected.indexOfFirst { !it }
            val overused = pos < BALANCE_POSITIONS && genres[first] in 1 until GENRE_SLOTS && seen[genres[first]] >= 2
            val usable = { i: Int -> !selected[i] && genres[i] in 1 until GENRE_SLOTS && seen[genres[i]] < 2 }
            val chosen =
                if (overused) {
                    alternative(
                        first,
                        minOf(n, first + BALANCE_LOOKAHEAD),
                        scores,
                        usable,
                    ) ?: first
                } else {
                    first
                }
            selected[chosen] = true
            result[pos] = chosen
            if (genres[chosen] in 1 until GENRE_SLOTS) seen[genres[chosen]]++
        }
        return result
    }

    /** Pierwsza użyteczna pozycja (inny, nienadużyty gatunek), o ile nie jest zbyt dużo gorsza od [first]. */
    private fun alternative(
        first: Int,
        last: Int,
        scores: IntArray,
        usable: (Int) -> Boolean,
    ): Int? {
        val alt = (first + 1 until last).firstOrNull(usable) ?: return null
        return alt.takeIf { scores[it] >= scores[first] - MAX_SCORE_LOSS }
    }

    private const val GENRE_SLOTS = 16
    private const val BALANCE_POSITIONS = 8
    private const val BALANCE_LOOKAHEAD = 19
    private const val MAX_SCORE_LOSS = 16
}
