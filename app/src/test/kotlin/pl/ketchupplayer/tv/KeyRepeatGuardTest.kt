package pl.ketchupplayer.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRepeatGuardTest {
    private val down = 20

    @Test
    fun `pierwsze nacisniecie nigdy nie jest odrzucane nawet gdy spoznione`() {
        val g = KeyRepeatGuard()
        assertFalse(g.shouldDrop(down, 0, eventTimeMs = 0, nowMs = 5_000))
    }

    @Test
    fun `zalegajace powtorzenia po zacieciu sa odrzucane`() {
        val g = KeyRepeatGuard()
        g.shouldDrop(down, 0, 1_000, 1_000)
        assertTrue(g.shouldDrop(down, 1, eventTimeMs = 1_100, nowMs = 1_500))
        assertTrue(g.shouldDrop(down, 2, eventTimeMs = 1_130, nowMs = 1_501))
    }

    @Test
    fun `swieze powtorzenia w tempie ekranu przechodza a zbyt gesto sa przycinane`() {
        val g = KeyRepeatGuard()
        g.shouldDrop(down, 0, 0, 0)
        assertFalse(g.shouldDrop(down, 1, 100, 110))
        assertTrue(g.shouldDrop(down, 2, 130, 140))
        assertFalse(g.shouldDrop(down, 3, 190, 200))
    }

    /** Pilot IR: jedno wciśnięcie = kilka osobnych naciśnięć co ~100 ms (każde repeatCount 0). */
    @Test
    fun `seria osobnych nacisniec jednego klawisza jest sprowadzana do jednego`() {
        val g = KeyRepeatGuard(debounceMs = 250)
        assertFalse(g.shouldDrop(down, 0, 0, 0))
        assertTrue(g.shouldDrop(down, 0, 100, 100))
        assertTrue(g.shouldDrop(down, 0, 200, 200))
        assertFalse(g.shouldDrop(down, 0, 300, 300)) // 300 ms od przyjętego: nowe naciśnięcie
    }

    @Test
    fun `inny klawisz nie jest tlumiony a wylaczony filtr przepuszcza wszystko`() {
        val g = KeyRepeatGuard(debounceMs = 250)
        assertFalse(g.shouldDrop(down, 0, 0, 0))
        assertFalse(g.shouldDrop(19, 0, 50, 50))
        g.debounceMs = 0
        assertFalse(g.shouldDrop(down, 0, 60, 60))
    }
}
