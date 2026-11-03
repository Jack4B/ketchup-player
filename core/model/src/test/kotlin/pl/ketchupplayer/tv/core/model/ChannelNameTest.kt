package pl.ketchupplayer.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelNameTest {
    @Test
    fun `usuwa przedrostki dostawcy`() {
        assertEquals("TVN 24 RAW", Channel.cleanDisplayName("PL-VIP| TVN 24 RAW"))
        assertEquals("TVN", Channel.cleanDisplayName("PL| TVN"))
        assertEquals("Sky Sports", Channel.cleanDisplayName("UK-NOWTV| Sky Sports"))
        assertEquals("ESPN", Channel.cleanDisplayName("LA LIGA| ESPN"))
    }

    @Test
    fun `nie rusza zwyklych nazw ani samego przedrostka`() {
        assertEquals("TVP Info", Channel.cleanDisplayName("TVP Info"))
        assertEquals("PL|", Channel.cleanDisplayName("PL|"))
        assertEquals("Mecz: Polska - Niemcy", Channel.cleanDisplayName("Mecz: Polska - Niemcy"))
    }

    @Test
    fun `ten sam kanal u roznych dostawcow ma ten sam klucz`() {
        val a = Channel("a:1", ProviderId("a"), "PL| TVN 24", null)
        val b = Channel("b:1", ProviderId("b"), "TVN 24", null)
        assertEquals(a.normName, b.normName)
    }
}
