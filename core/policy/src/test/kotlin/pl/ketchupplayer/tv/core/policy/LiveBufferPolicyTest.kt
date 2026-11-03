package pl.ketchupplayer.tv.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBufferPolicyTest {
    /** Stałe ze starej aplikacji (BkSmartBufferPolicy.maxSeconds): zmiana = świadoma decyzja, nie przypadek. */
    @Test
    fun `profile maja stare wartosci maksymalnego bufora`() {
        assertEquals(15, BufferProfile.FAST.maxSeconds)
        assertEquals(30, BufferProfile.STANDARD.maxSeconds)
        assertEquals(50, BufferProfile.STABLE.maxSeconds)
        assertEquals(90, BufferProfile.VERY_LARGE.maxSeconds)
    }

    @Test
    fun `klucze i stare aliasy mapuja sie na profile, nieznane na standardowy`() {
        assertEquals(BufferProfile.FAST, BufferProfile.fromKey("fast"))
        assertEquals(BufferProfile.FAST, BufferProfile.fromKey("small"))
        assertEquals(BufferProfile.STABLE, BufferProfile.fromKey("stable"))
        assertEquals(BufferProfile.STABLE, BufferProfile.fromKey("large"))
        assertEquals(BufferProfile.VERY_LARGE, BufferProfile.fromKey("very_large"))
        assertEquals(BufferProfile.STANDARD, BufferProfile.fromKey(null))
        assertEquals(BufferProfile.STANDARD, BufferProfile.fromKey("cos-innego"))
        BufferProfile.entries.forEach { assertEquals(it, BufferProfile.fromKey(it.key)) }
    }

    /** DefaultLoadControl wymaga: playback ≤ min ≤ max oraz rebuffer ≤ min. Inaczej Media3 rzuca przy budowie. */
    @Test
    fun `czasy kazdego profilu spelniaja warunki Media3`() {
        for (p in BufferProfile.entries) {
            val b = LiveBufferPolicy.bufferMs(p)
            assertEquals(p.maxSeconds * 1000, b.maxMs)
            assertTrue("$p playback", b.playbackMs <= b.minMs)
            assertTrue("$p rebuffer", b.rebufferMs <= b.minMs)
            assertTrue("$p min<=max", b.minMs <= b.maxMs)
            assertTrue("$p dodatnie", b.playbackMs > 0 && b.rebufferMs > 0)
        }
    }
}
