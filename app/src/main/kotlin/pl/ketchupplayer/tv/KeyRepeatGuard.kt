package pl.ketchupplayer.tv

/**
 * Ochrona przed „przeskakiwaniem” fokusu na pilocie: przytrzymany klawisz generuje powtórzenia, a gdy ekran się
 * chwilowo zacina, kolejkują się i wykonują po puszczeniu przycisku (jedno naciśnięcie = kilka pól).
 * Powtórzenie jest odrzucane, gdy jest zbyt stare (zalega w kolejce) albo przychodzi szybciej niż ekran nadąża.
 * Pierwsze naciśnięcie (repeatCount == 0) nigdy nie jest odrzucane.
 */
class KeyRepeatGuard(
    private val staleMs: Long = STALE_MS,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
) {
    private var lastAcceptedMs = Long.MIN_VALUE

    /** true = zdarzenie należy odrzucić. [eventTimeMs] i [nowMs] w tej samej skali (uptimeMillis). */
    fun shouldDrop(
        repeatCount: Int,
        eventTimeMs: Long,
        nowMs: Long,
    ): Boolean {
        if (repeatCount == 0) {
            lastAcceptedMs = nowMs
            return false
        }
        val drop = nowMs - eventTimeMs > staleMs || nowMs - lastAcceptedMs < minIntervalMs
        if (!drop) lastAcceptedMs = nowMs
        return drop
    }

    companion object {
        const val STALE_MS = 120L
        const val MIN_INTERVAL_MS = 70L
    }
}
