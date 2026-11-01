package pl.ketchupplayer.tv

/**
 * Ochrona przed „przeskakiwaniem” fokusu na pilocie. Dwa źródła problemu:
 * 1) przytrzymany klawisz generuje powtórzenia, które przy chwilowym zacięciu kolejkują się i wykonują po puszczeniu;
 * 2) niektóre piloty IR wysyłają na jedno wciśnięcie kilka osobnych naciśnięć (każde z repeatCount == 0).
 * Zdarzenie jest odrzucane, gdy powtórzenie jest zbyt stare albo za gęste, albo gdy to samo naciśnięcie przychodzi
 * szybciej niż [debounceMs] po poprzednim przyjętym (0 = filtr naciśnięć wyłączony).
 */
class KeyRepeatGuard(
    private val staleMs: Long = STALE_MS,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    var debounceMs: Long = 0L,
) {
    private val lastAcceptedByKey = HashMap<Int, Long>()

    /** true = zdarzenie należy odrzucić. [eventTimeMs] i [nowMs] w tej samej skali (uptimeMillis). */
    fun shouldDrop(
        keyCode: Int,
        repeatCount: Int,
        eventTimeMs: Long,
        nowMs: Long,
    ): Boolean {
        val last = lastAcceptedByKey[keyCode] ?: (nowMs - NEVER_MS)
        val drop =
            if (repeatCount == 0) {
                debounceMs > 0 && nowMs - last < debounceMs
            } else {
                nowMs - eventTimeMs > staleMs || nowMs - last < minIntervalMs
            }
        if (!drop) lastAcceptedByKey[keyCode] = nowMs
        return drop
    }

    companion object {
        const val STALE_MS = 120L
        const val MIN_INTERVAL_MS = 70L
        private const val NEVER_MS = 1_000_000L
    }
}
