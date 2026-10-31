package pl.ketchupplayer.tv.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAccentTest {
    @Test
    fun `parsowanie HEX`() {
        assertEquals(0xFF33B5C4L, HexColor.parse("#33B5C4"))
        assertEquals(0xFF33B5C4L, HexColor.parse("33b5c4"))
        assertEquals(0xFFAABBCCL, HexColor.parse(" #abc "))
        assertNull(HexColor.parse(""))
        assertNull(HexColor.parse("#12"))
        assertNull(HexColor.parse("#GGGGGG"))
        assertNull(HexColor.parse("#1234567"))
    }

    @Test
    fun `formatowanie HEX`() {
        assertEquals("#33B5C4", HexColor.format(0xFF33B5C4L))
        assertEquals("#000000", HexColor.format(0xFF000000L))
        assertEquals(0xFF33B5C4L, HexColor.parse(HexColor.format(0xFF33B5C4L)))
    }

    @Test
    fun `gotowy akcent nie jest zmieniany`() {
        val f = AccentFit.fit(ThemeId.GRANAT, AccentId.TURKUS.argb)
        assertFalse(f.adjusted)
        assertEquals(AccentId.TURKUS.argb, f.argb)
    }

    @Test
    fun `zbyt ciemny kolor jest rozjasniany do kontrastu 3 do 1 w kazdym motywie`() {
        for (theme in ThemeId.entries) {
            val t = Palettes.tokens(theme)
            val f = AccentFit.fit(theme, 0xFF101830L)
            assertTrue("$theme", f.adjusted)
            assertTrue(Contrast.ratio(f.argb, t.background) >= 3.0)
            assertTrue(Contrast.ratio(f.argb, t.surface3) >= 3.0)
        }
    }

    @Test
    fun `czern na czerni jest rozjasniana do szarosci`() {
        val f = AccentFit.fit(ThemeId.CZERN, 0xFF000000L)
        assertTrue(f.adjusted)
        assertTrue(Contrast.ratio(f.argb, 0xFF262626L) >= 3.0)
    }

    @Test
    fun `tekst na przycisku zawsze czytelny`() {
        for (theme in ThemeId.entries) {
            for (argb in listOf(0xFFFFFFFFL, 0xFF8800FFL, 0xFF00AAAAL, 0xFFFF5500L, 0xFF202020L)) {
                val f = AccentFit.fit(theme, argb)
                assertTrue("$theme ${HexColor.format(argb)}", Contrast.ratio(f.onAccent, f.argb) >= 4.5)
            }
        }
    }

    @Test
    fun `ostrzezenia dla zieleni i czerwieni`() {
        assertTrue(
            AccentFit.fit(ThemeId.GRANAT, 0xFF3DBE4BL).warnings.contains(AccentWarning.LOOKS_LIKE_NEW_EPISODE_GREEN),
        )
        assertTrue(AccentFit.fit(ThemeId.GRANAT, 0xFFFF4040L).warnings.contains(AccentWarning.LOOKS_LIKE_ERROR_RED))
        assertTrue(AccentFit.fit(ThemeId.GRANAT, AccentId.TURKUS.argb).warnings.isEmpty())
        assertTrue(AccentFit.fit(ThemeId.GRANAT, 0xFFEDEDEDL).warnings.isEmpty())
    }

    @Test
    fun `konwersja HSL w obie strony`() {
        for (argb in listOf(0xFF33B5C4L, 0xFFE3A857L, 0xFFB09AF2L, 0xFF000000L, 0xFFFFFFFFL)) {
            val (h, s, l) = AccentFit.toHsl(argb)
            assertEquals(argb, AccentFit.fromHsl(h, s, l))
        }
    }
}
