package pl.ketchupplayer.tv.core.designsystem

/**
 * Decyzja „czy stać nas na rozmycie tła”: z czasów kolejnych klatek (ms) zmierzonych podczas renderowania rozmycia.
 * Odrzuca rozgrzewkę, a potem wymaga płynności: mediana ≤ [MEDIAN_MAX_MS] i 90. percentyl ≤ [P90_MAX_MS].
 * Za mało próbek = ostrożnie „nie” (użytkownik może włączyć ręcznie).
 */
object GlassVerdict {
    const val WARMUP_FRAMES = 8
    const val MIN_SAMPLES = 30
    const val MEDIAN_MAX_MS = 18L
    const val P90_MAX_MS = 25L

    fun decide(frameDeltasMs: List<Long>): Boolean {
        val samples = frameDeltasMs.drop(WARMUP_FRAMES)
        if (samples.size < MIN_SAMPLES) return false
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        val p90 = sorted[(sorted.size * P90_NUM / P90_DEN).coerceAtMost(sorted.size - 1)]
        return median <= MEDIAN_MAX_MS && p90 <= P90_MAX_MS
    }

    private const val P90_NUM = 9
    private const val P90_DEN = 10
}
