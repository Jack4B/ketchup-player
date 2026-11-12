package pl.ketchupplayer.tv.core.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassVerdictTest {
    private fun frames(
        count: Int,
        ms: Long,
    ) = List(count) { ms }

    @Test
    fun `plynne 60 fps z rozmyciem to tak`() {
        assertTrue(GlassVerdict.decide(frames(90, 17)))
    }

    @Test
    fun `wolny box z klatkami po 40 ms to nie`() {
        assertFalse(GlassVerdict.decide(frames(90, 40)))
    }

    @Test
    fun `rozgrzewka z dlugimi klatkami nie psuje werdyktu`() {
        val warmup = frames(GlassVerdict.WARMUP_FRAMES, 120)
        assertTrue(GlassVerdict.decide(warmup + frames(80, 16)))
    }

    /** Rzadkie zacięcia (do 10% klatek) są tolerowane, częste (25%) już nie. */
    @Test
    fun `sporadyczne zacinanie jest tolerowane a czeste nie`() {
        assertTrue(GlassVerdict.decide(frames(8, 16) + frames(75, 16) + frames(5, 60)))
        assertFalse(GlassVerdict.decide(frames(8, 16) + frames(60, 16) + frames(20, 60)))
    }

    @Test
    fun `za malo probek to ostroznie nie`() {
        assertFalse(GlassVerdict.decide(frames(20, 10)))
        assertFalse(GlassVerdict.decide(emptyList()))
    }
}
