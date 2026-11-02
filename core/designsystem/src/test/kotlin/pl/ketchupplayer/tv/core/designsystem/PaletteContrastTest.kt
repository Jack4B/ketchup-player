package pl.ketchupplayer.tv.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tabele kontrastu z docs/mockups/NOTATKI-v3.md §4: wszystkie pary muszą przejść WCAG AA. */
class PaletteContrastTest {
    /** Progi zamiast dokładnych liczb: paleta v5 ma zachować czytelność, a nie konkretne wartości z notatek v3. */
    @Test
    fun `kluczowe kontrasty palety v5 nie spadaja ponizej progow`() {
        val m = Palettes.tokens(ThemeId.MORSKI)
        assertTrue(Contrast.ratio(m.textPrimary, m.background) >= 14.0)
        assertTrue(Contrast.ratio(AccentId.TURKUS.argb, m.background) >= 7.0)
        assertTrue(Contrast.ratio(AccentId.TURKUS.argb, m.surface3) >= 4.5)
        val c = Palettes.tokens(ThemeId.CZERN)
        assertTrue(Contrast.ratio(c.textPrimary, c.background) >= 18.0)
        val g = Palettes.tokens(ThemeId.GRAFIT)
        assertTrue(Contrast.ratio(g.textPrimary, g.background) >= 14.0)
        assertTrue(Contrast.ratio(AccentId.TURKUS.argb, g.surface3) >= 4.5)
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
        assertEquals(AccentId.TURKUS, ThemeId.MORSKI.defaultAccent)
        assertEquals(AccentId.BIEL, ThemeId.CZERN.defaultAccent)
        assertEquals(AccentId.TURKUS, ThemeId.GRAFIT.defaultAccent)
    }
}
