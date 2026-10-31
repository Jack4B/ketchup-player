package pl.ketchupplayer.tv.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tabele kontrastu z docs/mockups/NOTATKI-v3.md §4: wszystkie pary muszą przejść WCAG AA. */
class PaletteContrastTest {
    private fun Double.near(expected: Double) = assertEquals(expected, this, 0.02)

    @Test
    fun `wartosci zgodne z tabelami z notatek`() {
        val g = Palettes.tokens(ThemeId.GRANAT)
        Contrast.ratio(g.textPrimary, g.background).near(15.46)
        Contrast.ratio(g.textSecondary, g.surface3).near(4.97)
        Contrast.ratio(AccentId.TURKUS.argb, g.background).near(7.11)
        Contrast.ratio(AccentId.TURKUS.argb, g.surface3).near(3.94)
        val c = Palettes.tokens(ThemeId.CZERN)
        Contrast.ratio(c.textPrimary, c.background).near(18.76)
        val b = Palettes.tokens(ThemeId.BORDO)
        Contrast.ratio(b.textPrimary, b.background).near(14.91)
        Contrast.ratio(AccentId.BLEKIT.argb, b.surface3).near(3.64)
    }

    @Test
    fun `tekst przechodzi AA 4,5 na tle panelu i focusie w kazdym motywie`() {
        for (theme in ThemeId.entries) {
            val t = Palettes.tokens(theme)
            for (text in listOf(t.textPrimary, t.textSecondary)) {
                for (bg in listOf(t.background, t.surface2, t.surface3)) {
                    assertTrue("$theme $text/$bg", Contrast.ratio(text, bg) >= 4.5)
                }
            }
            assertTrue("$theme kafel kanalu", Contrast.ratio(t.channelTileText, t.channelTile) >= 4.5)
        }
    }

    @Test
    fun `kazdy akcent w kazdym motywie - ring 3 do 1 i tekst na przycisku 4,5 do 1`() {
        for (theme in ThemeId.entries) {
            val t = Palettes.tokens(theme)
            for (accent in AccentId.entries) {
                for (bg in listOf(t.background, t.surface3)) {
                    assertTrue("$theme/$accent ring na $bg", Contrast.ratio(accent.argb, bg) >= 3.0)
                }
                assertTrue("$theme/$accent przycisk", Contrast.ratio(Palettes.ON_ACCENT, accent.argb) >= 4.5)
            }
        }
    }

    @Test
    fun `zielen nowych odcinkow jest widoczna na panelu i nie jest akcentem`() {
        for (theme in ThemeId.entries) {
            assertTrue(Contrast.ratio(Palettes.NEW_EPISODE_GREEN, Palettes.tokens(theme).surface2) >= 3.0)
        }
        assertTrue(AccentId.entries.none { it.argb == Palettes.NEW_EPISODE_GREEN })
    }

    @Test
    fun `domyslne akcenty motywow`() {
        assertEquals(AccentId.TURKUS, ThemeId.GRANAT.defaultAccent)
        assertEquals(AccentId.BIEL, ThemeId.CZERN.defaultAccent)
        assertEquals(AccentId.BURSZTYN, ThemeId.BORDO.defaultAccent)
    }
}
