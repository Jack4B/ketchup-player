package pl.ketchupplayer.tv.core.data.logos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.data.catalog.toModel
import java.io.File

class LogoIndexTest {
    /** Prawdziwy, wbudowany indeks (publiczne dane iptv-org): sprawdza też, że plik jest poprawny i niepusty. */
    private val real = LogoIndex.parse(File("src/main/assets/logos/iptv-org.tsv").readText())

    @Test
    fun `wbudowany indeks ma polskie stacje`() {
        assertTrue("za malo stacji: ${real.size}", real.size > 200)
    }

    @Test
    fun `dopasowanie po identyfikatorze iptv-org`() {
        assertNotNull(real.find("cokolwiek", "TVN24.pl"))
        assertNotNull(real.find("cokolwiek", "tvn24.PL"))
    }

    /** Realne zapisy z list IPTV: przedrostek kraju, sufiks jakości, nawias z rozdzielczością, wielkość liter. */
    @Test
    fun `dopasowanie po nazwie z typowymi ozdobnikami`() {
        val tvn24 = real.find("TVN 24")
        assertNotNull(tvn24)
        assertEquals(tvn24, real.find("PL| TVN 24 HD"))
        assertEquals(tvn24, real.find("PL: tvn24 (576i)"))
        assertEquals(tvn24, real.find("[PL] TVN24 FHD"))
        assertNotNull(real.find("TVP 1 HD"))
        assertNotNull(real.find("Polsat"))
    }

    @Test
    fun `nieznana stacja i pusta nazwa daja null`() {
        assertNull(real.find("Zupelnie Nieznany Kanal 123"))
        assertNull(real.find("", null))
        assertNull(real.find("   ", ""))
    }

    @Test
    fun `parser pomija bledne wiersze i adresy bez https`() {
        val i = LogoIndex.parse("a.pl\ta\thttps://x/a.png\nzly wiersz\nb.pl\tb\thttp://x/b.png\n\n")
        assertEquals(1, i.size)
        assertEquals("https://x/a.png", i.find("A"))
        assertNull(i.find("B"))
    }

    @Test
    fun `polskie znaki sa normalizowane`() {
        assertEquals(LogoIndex.normalize("Łódź TV"), LogoIndex.normalize("lodz"))
    }

    /** Kanał z listy bez logo dostaje logo z bazy; własne logo z listy ma pierwszeństwo. */
    @Test
    fun `mapowanie kanalu uzupelnia brakujace logo`() {
        LogoIndex.install(real)
        try {
            fun entity(logo: String?) =
                pl.ketchupplayer.tv.core.database.ChannelEntity(
                    "p:1",
                    "p",
                    "PL| TVN 24 HD",
                    "tvn24",
                    1,
                    null,
                    logo,
                    "Info",
                    "LIVE",
                    "http://h.invalid/1",
                    null,
                )
            val filled = entity(null).toModel().logo
            assertNotNull(filled)
            assertEquals(filled, entity("  ").toModel().logo)
            assertEquals("http://x.invalid/own.png", entity("http://x.invalid/own.png").toModel().logo)
        } finally {
            LogoIndex.install(LogoIndex.EMPTY)
        }
    }
}
