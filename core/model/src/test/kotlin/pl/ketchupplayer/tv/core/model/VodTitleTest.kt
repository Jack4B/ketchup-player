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

    // --- Zgłoszenie z telefonu: oceny IMDb nie dla wszystkich filmów (nazwy z paneli, dane fikcyjne/ogólne) ---

    private fun assertParsed(
        raw: String,
        title: String,
        year: Int?,
    ) {
        val p = VodTitle.parse(raw)
        assertEquals("tytuł z '$raw'", title, p.title)
        assertEquals("rok z '$raw'", year, p.year)
        assertEquals(title, VodTitle.clean(raw))
        assertEquals(year, VodTitle.year(raw))
    }

    @Test
    fun `rok bez nawiasow, w nawiasach kwadratowych i w nazwie z kropkami`() {
        assertParsed("Matrix 1999", "Matrix", 1999)
        assertParsed("Matrix [1999]", "Matrix", 1999)
        assertParsed("Matrix (1999)", "Matrix", 1999)
        assertParsed("The.Matrix.1999.1080p.BluRay.x264-GRP", "The Matrix", 1999)
        assertParsed("Pan_Tadeusz_1999_720p", "Pan Tadeusz", 1999)
        assertParsed("PL - Pitbull 2005 4K", "Pitbull", 2005)
    }

    @Test
    fun `liczby, ktore nie sa rokiem filmu, zostaja w tytule`() {
        assertParsed("1917", "1917", null)
        assertParsed("2012", "2012", null)
        assertParsed("Blade Runner 2049", "Blade Runner 2049", null)
        assertParsed("Apollo 13", "Apollo 13", null)
        assertParsed("1917 (2019)", "1917", 2019)
        assertParsed("S.W.A.T.", "S.W.A.T.", null)
        assertParsed("Mr. Robot", "Mr. Robot", null)
    }

    @Test
    fun `sufiksy jakosci i jezyka bez nawiasow`() {
        assertParsed("Matrix PL", "Matrix", null)
        assertParsed("Matrix - PL", "Matrix", null)
        assertParsed("Matrix 4K UHD", "Matrix", null)
        assertParsed("Matrix (1999) HD", "Matrix", 1999)
        assertParsed("PL | Matrix (1999) 4K", "Matrix", 1999)
        assertParsed("Matrix (1999) [ENG]", "Matrix", 1999)
        assertParsed("Matrix DUAL", "Matrix", null)
        assertParsed("Matrix 1080p BluRay x264", "Matrix", null)
    }

    @Test
    fun `sufiks to nie czesc tytulu gdy slowo jest zwykle`() {
        assertParsed("Charlotte's Web", "Charlotte's Web", null)
        assertParsed("The Web", "The Web", null)
        assertParsed("Dub", "Dub", null)
    }

    @Test
    fun `przedrostki platform`() {
        assertParsed("NETFLIX - Matrix (1999)", "Matrix", 1999)
        assertParsed("HBO - Matrix (1999)", "Matrix", 1999)
        assertParsed("DISNEY+ - Frozen (2013)", "Frozen", 2013)
        assertParsed("AMAZON | Matrix (1999)", "Matrix", 1999)
        assertParsed("PL | NETFLIX | Matrix (1999)", "Matrix", 1999)
        assertParsed("[PL] Matrix (1999)", "Matrix", 1999)
        assertParsed("|PL| Matrix (1999) [4K]", "Matrix", 1999)
    }

    @Test
    fun `przedrostek nie obcina tytulow z dwukropkiem lub myslnikiem`() {
        assertParsed("Saw: Spiral (2021)", "Saw: Spiral", 2021)
        assertParsed("Rio - Carnaval (2011)", "Rio - Carnaval", 2011)
        assertParsed("Ice: Age (2002)", "Ice: Age", 2002)
        assertParsed("Up: Wrong (2009)", "Up: Wrong", 2009)
        assertParsed("Mad: Fury (2015)", "Mad: Fury", 2015)
        assertParsed("SAW: SPIRAL (2021)", "SAW: SPIRAL", 2021)
        assertParsed("Prime Suspect", "Prime Suspect", null)
        assertParsed("Max Payne (2008)", "Max Payne", 2008)
        assertParsed("PL: Matrix (1999)", "Matrix", 1999)
    }

    @Test
    fun `idempotencja i brak zmiany czystego tytulu`() {
        listOf("Matrix", "Saw: Spiral", "Wonder Woman 1984", "Mission: Impossible", "The Office (US)").forEach {
            val once = VodTitle.parse(it)
            assertEquals(it, once.title.let { t -> VodTitle.parse(t).title }, VodTitle.parse(once.title).title)
        }
        assertParsed("Wonder Woman 1984", "Wonder Woman", 1984)
    }

    @Test
    fun `dlugie wejscie nie zawiesza czyszczenia`() {
        val raw = "x ".repeat(5000) + "(2001) [4K] ".repeat(2000)
        val t0 = System.nanoTime()
        VodTitle.parse(raw)
        assertEquals(true, (System.nanoTime() - t0) < 2_000_000_000L)
    }
}
