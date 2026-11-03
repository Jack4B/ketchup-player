package pl.ketchupplayer.tv.feature.playerui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberTest {
    private val film = 7_200_000L // 2 h
    private val pos = 1_800_000L // 30 min

    private fun Scrubber.advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val dt = minOf(Scrubber.TICK_MS, left)
            tick(dt)
            left -= dt
        }
    }

    /** Przytrzymanie: pilot przysyła powtórzenia klawisza, które podtrzymują trzymanie. */
    private fun Scrubber.advanceHeld(ms: Long) {
        var left = ms
        while (left > 0) {
            val dt = minOf(100L, left)
            advance(dt)
            keepAlive()
            left -= dt
        }
    }

    @Test
    fun `krotkie naciskniecie przewija o 10 s po zwloce`() {
        val s = Scrubber()
        s.press(+1, pos, film)
        s.release()
        assertNull(s.takeCommit()) // jeszcze zbieramy kolejne naciśnięcia
        s.advance(Scrubber.COMMIT_DELAY_MS)
        assertEquals(pos + 10_000, s.takeCommit())
        assertFalse(s.active)
    }

    @Test
    fun `szybkie naciskniecia sumuja sie w jeden seek`() {
        val s = Scrubber()
        repeat(3) {
            s.press(+1, pos, film)
            s.release()
            s.advance(100)
        }
        s.advance(Scrubber.COMMIT_DELAY_MS)
        assertEquals(pos + 30_000, s.takeCommit())
    }

    @Test
    fun `przytrzymanie przyspiesza z czasem trzymania`() {
        val s = Scrubber()
        s.press(+1, pos, film)
        s.advanceHeld(1_000)
        val after1 = s.targetMs - pos
        s.advanceHeld(2_000)
        val after3 = s.targetMs - pos
        s.advanceHeld(5_000)
        val after8 = s.targetMs - pos
        assertTrue("1 s: $after1", after1 > 10_000)
        assertTrue("3 s ($after3) wiecej niz 1 s ($after1) razy 3", after3 > after1 * 3)
        assertTrue("8 s ($after8) znacznie dalej", after8 > after3 * 2)
    }

    @Test
    fun `dlugo trzymany klawisz w dlugim filmie przewija caly film w okolicach minuty`() {
        val s = Scrubber()
        s.press(+1, 0, film)
        s.advanceHeld(3_000) // po 3 s włącza się „cały film w minutę”
        val at3 = s.targetMs
        s.advanceHeld(30_000)
        // 30 s przy 2 h/60 = 120 s/s to ok. 3600 s: połowa filmu
        assertTrue("przesunięcie ${s.targetMs - at3}", s.targetMs - at3 >= 3_000_000)
    }

    @Test
    fun `cel nie wychodzi poza film`() {
        val s = Scrubber()
        s.press(+1, film - 5_000, film)
        s.advanceHeld(10_000)
        assertEquals(film, s.targetMs)
        val b = Scrubber()
        b.press(-1, 5_000, film)
        b.advanceHeld(10_000)
        assertEquals(0L, b.targetMs)
    }

    @Test
    fun `zmiana kierunku zaczyna od nowa i wraca`() {
        val s = Scrubber()
        s.press(+1, pos, film)
        s.advanceHeld(2_000)
        val far = s.targetMs
        s.press(-1, pos, film) // przeciwny kierunek: skok −10 s i wolne tempo od zera
        assertEquals(far - 10_000, s.targetMs)
        s.advance(100)
        assertTrue(s.targetMs < far - 10_000 + 1)
    }

    @Test
    fun `brak puszczenia klawisza konczy przewijanie po bezpieczniku`() {
        val s = Scrubber()
        s.press(+1, pos, film)
        s.advance(Scrubber.FAILSAFE_MS + 100)
        s.advance(Scrubber.COMMIT_DELAY_MS + 100)
        assertTrue(s.takeCommit() != null)
    }

    @Test
    fun `powtorzenia klawisza podtrzymuja trzymanie`() {
        val s = Scrubber()
        s.press(+1, pos, film)
        repeat(40) {
            s.advance(100)
            s.keepAlive()
        }
        assertTrue("nadal trzymamy po 4 s", s.active && s.takeCommit() == null)
        assertTrue(s.deltaMs > 60_000)
    }

    @Test
    fun `prędkosc rosnie schodkowo`() {
        val v = { hold: Long -> Scrubber.velocityMsPerSec(hold, film) }
        assertEquals(0L, v(100))
        assertTrue(v(1_000) < v(2_000))
        assertTrue(v(2_000) < v(4_000))
        assertTrue(v(4_000) <= v(9_000))
        assertTrue(v(9_000) >= film / 60)
    }
}
