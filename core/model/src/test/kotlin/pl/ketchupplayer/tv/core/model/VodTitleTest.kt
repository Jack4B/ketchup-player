package pl.ketchupplayer.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tytuły z listy użytkownika (raport 2.11): przedrostki platform i znaczniki jakości psuły karty i Home. */
class VodTitleTest {
    @Test
    fun `usuwa przedrostek platformy z plusem`() {
        assertEquals("Matchbox The Movie", VodTitle.clean("A+ - Matchbox The Movie (MULTI SUB)"))
        assertEquals("Dear Edward", VodTitle.clean("A+ - Dear Edward"))
        assertEquals("Frozen", VodTitle.clean("D+ - Frozen"))
    }

    @Test
    fun `usuwa znaczniki jakosci i jezyka w nawiasach`() {
        assertEquals("Outcome", VodTitle.clean("Outcome (MULTI SUB)"))
        assertEquals("Echo Valley", VodTitle.clean("Echo Valley [4K] (HDR)"))
        assertEquals("Film", VodTitle.clean("Film (PL) (LEKTOR)"))
    }

    @Test
    fun `zachowuje tytuly z wlasnymi nawiasami i myslnikami`() {
        assertEquals("The Office (US)", VodTitle.clean("The Office (US)"))
        assertEquals("Spider-Man: No Way Home", VodTitle.clean("Spider-Man: No Way Home"))
        assertEquals("(500) Days of Summer", VodTitle.clean("(500) Days of Summer"))
        assertEquals("Mission: Impossible", VodTitle.clean("Mission: Impossible"))
    }

    @Test
    fun `dotychczasowe zasady zostaja jezyk i rok`() {
        assertEquals("Pitbull", VodTitle.clean("PL - Pitbull (2005)"))
        assertEquals("Seksmisja", VodTitle.clean("Seksmisja"))
        assertEquals("Film", VodTitle.clean("|EN| Film"))
        assertEquals(2005, VodTitle.year("PL - Pitbull (2005)"))
    }

    @Test
    fun `pusty wynik wraca do oryginalu`() {
        assertEquals("(MULTI SUB)", VodTitle.clean("(MULTI SUB)"))
    }
}
