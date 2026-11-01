package pl.ketchupplayer.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRepeatGuardTest {
    @Test
    fun `pierwsze nacisniecie nigdy nie jest odrzucane nawet gdy spoznione`() {
        val g = KeyRepeatGuard()
        assertFalse(g.shouldDrop(0, eventTimeMs = 0, nowMs = 5_000))
    }

    @Test
    fun `zalegajace powtorzenia po zacieciu sa odrzucane`() {
        val g = KeyRepeatGuard()
        g.shouldDrop(0, 1_000, 1_000)
        // Ekran stał 400 ms; kolejka oddaje powtórzenia sprzed pół sekundy.
        assertTrue(g.shouldDrop(1, eventTimeMs = 1_100, nowMs = 1_500))
        assertTrue(g.shouldDrop(2, eventTimeMs = 1_130, nowMs = 1_501))
    }

    @Test
    fun `swieze powtorzenia w tempie ekranu przechodza a zbyt gesto sa przycinane`() {
        val g = KeyRepeatGuard()
        g.shouldDrop(0, 0, 0)
        assertFalse(g.shouldDrop(1, 100, 110)) // 110 ms po pierwszym: ok
        assertTrue(g.shouldDrop(2, 130, 140)) // 30 ms po poprzednim: za gęsto
        assertFalse(g.shouldDrop(3, 190, 200)) // 90 ms po przyjętym: ok
    }
}
