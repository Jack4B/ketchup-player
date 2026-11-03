package pl.ketchupplayer.tv.feature.playerui

/** Stan przewijania do pokazania na osi: cel i przesunięcie względem pozycji z początku. */
data class ScrubUi(
    val targetMs: Long,
    val deltaMs: Long,
)

/**
 * Przewijanie pilotem w stylu współczesnych odtwarzaczy (YouTube, Netflix): zamiast seeka przy każdym powtórzeniu
 * klawisza przesuwa się *docelowa* pozycja, a jeden seek następuje po puszczeniu klawisza.
 *
 *  - krótkie naciśnięcie: ±[TAP_MS]; kolejne szybkie naciśnięcia sumują się w jeden seek,
 *  - przytrzymanie: po [HOLD_START_MS] pozycja płynie z prędkością rosnącą z czasem trzymania ([velocityMsPerSec]),
 *    a w długim filmie nie wolniej niż „cały film w minutę” po 3 s trzymania,
 *  - zmiana kierunku zaczyna od nowa; brak zdarzeń klawisza przez [FAILSAFE_MS] kończy przewijanie (pilot bez KeyUp).
 *
 * Klasa nie zna zegara: czas płynie przez [tick] (delta w ms), więc zachowanie jest deterministyczne w testach.
 */
class Scrubber {
    var active: Boolean = false
        private set

    /** Pozycja, do której przewiniemy po puszczeniu klawisza. */
    var targetMs: Long = 0
        private set

    /** Przesunięcie względem pozycji z początku przewijania (do podpisu „+1:30”). */
    val deltaMs: Long get() = targetMs - startMs

    private var startMs = 0L
    private var durationMs = 0L
    private var dir = 0
    private var holdMs = 0L
    private var idleMs = 0L
    private var releasedForMs = -1L

    /** Klawisz w dół. [dir] = −1 (wstecz) albo +1 (naprzód). */
    fun press(
        dir: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (!active) {
            active = true
            targetMs = positionMs
            startMs = positionMs
            this.durationMs = durationMs
            this.dir = 0
        }
        idleMs = 0
        val held = releasedForMs < 0
        if (dir == this.dir && held) return // powtórzenie przy trzymaniu: robi je tick
        this.dir = dir
        holdMs = 0
        releasedForMs = -1
        targetMs = clamp(targetMs + dir * TAP_MS)
    }

    /** Zdarzenie powtórzenia klawisza (KeyDown z repeatCount > 0): podtrzymuje trzymanie. */
    fun keepAlive() {
        idleMs = 0
    }

    /** Klawisz w górę: po krótkiej zwłoce (zbieranie kolejnych naciśnięć) przewijanie się zatwierdzi. */
    fun release() {
        if (active && releasedForMs < 0) releasedForMs = 0
    }

    /** Upływ czasu o [dtMs]: przy trzymaniu przesuwa cel, po puszczeniu odlicza zwłokę. */
    fun tick(dtMs: Long) {
        if (!active) return
        idleMs += dtMs
        if (releasedForMs >= 0) {
            releasedForMs += dtMs
            return
        }
        holdMs += dtMs
        if (holdMs >= HOLD_START_MS) {
            val v = velocityMsPerSec(holdMs, durationMs)
            targetMs = clamp(targetMs + dir * (v * dtMs / MS_PER_SEC))
        }
        if (idleMs > FAILSAFE_MS) releasedForMs = 0
    }

    /** Pozycja do wykonania seeka, gdy klawisz puszczono dość dawno; kończy przewijanie. Inaczej null. */
    fun takeCommit(): Long? {
        if (!active || releasedForMs < COMMIT_DELAY_MS) return null
        val result = targetMs
        reset()
        return result
    }

    fun reset() {
        active = false
        dir = 0
        holdMs = 0
        idleMs = 0
        releasedForMs = -1
    }

    private fun clamp(ms: Long): Long = ms.coerceIn(0, if (durationMs > 0) durationMs else Long.MAX_VALUE)

    companion object {
        const val TAP_MS = 10_000L
        const val TICK_MS = 50L
        const val HOLD_START_MS = 400L
        const val COMMIT_DELAY_MS = 350L
        const val FAILSAFE_MS = 1_200L
        private const val MS_PER_SEC = 1_000L
        private const val FULL_FILM_SECONDS = 60L

        /** Prędkość przewijania (ms materiału na sekundę czasu rzeczywistego) po [holdMs] trzymania. */
        fun velocityMsPerSec(
            holdMs: Long,
            durationMs: Long,
        ): Long {
            val tier =
                when {
                    holdMs < HOLD_START_MS -> 0L
                    holdMs < 1_500 -> 30_000L
                    holdMs < 3_000 -> 60_000L
                    holdMs < 5_000 -> 120_000L
                    holdMs < 8_000 -> 240_000L
                    else -> 480_000L
                }
            val fullFilm = if (holdMs >= 3_000 && durationMs > 0) durationMs / FULL_FILM_SECONDS else 0L
            return maxOf(tier, fullFilm)
        }
    }
}
