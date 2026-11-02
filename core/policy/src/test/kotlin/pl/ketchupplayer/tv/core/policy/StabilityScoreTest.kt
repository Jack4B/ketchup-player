package pl.ketchupplayer.tv.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityScoreTest {
    private val minute = 60_000L

    @Test
    fun `bez przerw i zgubionych klatek to sto procent`() {
        val s = StabilityScore.of(rebuffers = 0, stallMs = 0, playedMs = 5 * minute, droppedFrames = 0)
        assertEquals(100, s.percent)
        assertEquals(StabilityLevel.STABLE, s.level)
    }

    @Test
    fun `kazda przerwa i czas przerw obnizaja ocene`() {
        val few = StabilityScore.of(rebuffers = 1, stallMs = 2_000, playedMs = 5 * minute, droppedFrames = 0)
        val many = StabilityScore.of(rebuffers = 5, stallMs = 30_000, playedMs = 5 * minute, droppedFrames = 0)
        assertTrue(few.percent < 100)
        assertTrue(many.percent < few.percent)
        assertEquals(StabilityLevel.UNSTABLE, many.level)
    }

    @Test
    fun `zgubione klatki licza sie w przeliczeniu na minute`() {
        val shortDrops = StabilityScore.of(0, 0, 10 * minute, droppedFrames = 20) // 2 na minutę: bez kary
        val heavyDrops = StabilityScore.of(0, 0, minute, droppedFrames = 600)
        assertEquals(100, shortDrops.percent)
        assertTrue(heavyDrops.percent < 90)
    }

    @Test
    fun `ocena zawsze miesci sie w zakresie`() {
        val worst =
            StabilityScore.of(
                rebuffers = 500,
                stallMs = 10 * minute,
                playedMs = minute,
                droppedFrames = 100_000,
            )
        assertEquals(0, worst.percent)
        assertEquals(StabilityLevel.UNSTABLE, worst.level)
    }

    @Test
    fun `bez czasu odtwarzania nie ma oceny a nie dzielenia przez zero`() {
        val s = StabilityScore.of(rebuffers = 0, stallMs = 0, playedMs = 0, droppedFrames = 0)
        assertEquals(100, s.percent)
    }

    @Test
    fun `poziomy wedlug progow`() {
        assertEquals(StabilityLevel.STABLE, StabilityScore.levelOf(StabilityScore.STABLE_FROM))
        assertEquals(StabilityLevel.FAIR, StabilityScore.levelOf(StabilityScore.STABLE_FROM - 1))
        assertEquals(StabilityLevel.FAIR, StabilityScore.levelOf(StabilityScore.FAIR_FROM))
        assertEquals(StabilityLevel.UNSTABLE, StabilityScore.levelOf(StabilityScore.FAIR_FROM - 1))
    }
}
