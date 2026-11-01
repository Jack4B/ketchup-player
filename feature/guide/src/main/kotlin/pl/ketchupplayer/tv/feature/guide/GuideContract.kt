package pl.ketchupplayer.tv.feature.guide

import pl.ketchupplayer.tv.core.model.Channel

/** Okno czasu siatki i kanały w kolejności wyświetlania (ulubione na początku). */
data class GuideUiState(
    val loading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val favorites: Set<String> = emptySet(),
    /** Początek osi czasu (zaokrąglony w dół do pół godziny) i jej koniec, ms UTC. */
    val windowStartMs: Long = 0,
    val windowEndMs: Long = 0,
    val nowMs: Long = 0,
    /** Rośnie po zakończeniu importu EPG; wiersze przeładowują wtedy swoje audycje. */
    val epgRevision: Int = 0,
)

/** Geometria siatki: długość audycji w minutach -> szerokość bloku. */
object GuideGeometry {
    const val WINDOW_MINUTES = 12 * 60
    const val STEP_MINUTES = 30
    const val DP_PER_MINUTE = 6f
    const val MIN_BLOCK_DP = 36f
    private const val MS_PER_MINUTE = 60_000L

    /** Początek okna: bieżąca pełna półgodzina. */
    fun windowStart(nowMs: Long): Long {
        val step = STEP_MINUTES * MS_PER_MINUTE
        return nowMs - Math.floorMod(nowMs, step)
    }

    fun windowEnd(startMs: Long): Long = startMs + WINDOW_MINUTES * MS_PER_MINUTE

    /** Odsunięcie bloku od lewej krawędzi osi i jego szerokość w dp, przycięte do okna. Null = poza oknem. */
    fun place(
        startMs: Long,
        endMs: Long,
        windowStartMs: Long,
        windowEndMs: Long,
    ): Pair<Float, Float>? {
        val from = maxOf(startMs, windowStartMs)
        val to = minOf(endMs, windowEndMs)
        if (to <= from) return null
        val offset = (from - windowStartMs) / MS_PER_MINUTE.toFloat() * DP_PER_MINUTE
        val width = ((to - from) / MS_PER_MINUTE.toFloat() * DP_PER_MINUTE).coerceAtLeast(MIN_BLOCK_DP)
        return offset to width
    }

    fun totalWidthDp(): Float = WINDOW_MINUTES * DP_PER_MINUTE
}
