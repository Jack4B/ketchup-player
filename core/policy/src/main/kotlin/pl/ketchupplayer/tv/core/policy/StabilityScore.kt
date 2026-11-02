package pl.ketchupplayer.tv.core.policy

enum class StabilityLevel { STABLE, FAIR, UNSTABLE }

/** Ocena stabilności łącza w bieżącej sesji odtwarzania: procent 0-100 i poziom (do wyświetlenia w diagnostyce). */
data class StabilityScore(
    val percent: Int,
    val level: StabilityLevel,
) {
    companion object {
        const val STABLE_FROM = 85
        const val FAIR_FROM = 60
        private const val MAX = 100
        private const val REBUFFER_PENALTY = 8
        private const val REBUFFER_PENALTY_CAP = 48
        private const val STALL_WEIGHT = 200.0
        private const val STALL_PENALTY_CAP = 40
        private const val FREE_DROPS_PER_MIN = 5.0
        private const val DROP_PENALTY_PER_EXTRA = 0.2
        private const val DROP_PENALTY_CAP = 20
        private const val MS_PER_MIN = 60_000.0

        /**
         * Ocena z liczby przerw ([rebuffers]) i ich łącznego czasu ([stallMs]) względem czasu grania ([playedMs])
         * oraz zgubionych klatek na minutę. Każda przerwa kosztuje, a długie przerwy bardziej.
         */
        fun of(
            rebuffers: Int,
            stallMs: Long,
            playedMs: Long,
            droppedFrames: Long,
        ): StabilityScore {
            val total = (playedMs + stallMs).coerceAtLeast(1L)
            val rebufferPenalty = (rebuffers * REBUFFER_PENALTY).coerceAtMost(REBUFFER_PENALTY_CAP)
            val stallPenalty = ((stallMs.toDouble() / total) * STALL_WEIGHT).toInt().coerceAtMost(STALL_PENALTY_CAP)
            val perMinute = droppedFrames / (playedMs.coerceAtLeast(1L) / MS_PER_MIN).coerceAtLeast(1.0)
            val dropPenalty =
                ((perMinute - FREE_DROPS_PER_MIN).coerceAtLeast(0.0) * DROP_PENALTY_PER_EXTRA)
                    .toInt()
                    .coerceAtMost(DROP_PENALTY_CAP)
            val percent = (MAX - rebufferPenalty - stallPenalty - dropPenalty).coerceIn(0, MAX)
            return StabilityScore(percent, levelOf(percent))
        }

        fun levelOf(percent: Int): StabilityLevel =
            when {
                percent >= STABLE_FROM -> StabilityLevel.STABLE
                percent >= FAIR_FROM -> StabilityLevel.FAIR
                else -> StabilityLevel.UNSTABLE
            }
    }
}
