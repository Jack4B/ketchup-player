package pl.ketchupplayer.tv.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.EpgSourceKind
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.policy.EpgSourcePolicy.UrlCheck
import pl.ketchupplayer.tv.core.policy.EpgSourcePolicy.UrlError

class EpgSourcePolicyTest {
    private fun invalid(raw: String) = (EpgSourcePolicy.validateUrl(raw) as UrlCheck.Invalid).reason

    @Test
    fun `https i http sa poprawne`() {
        assertEquals(UrlCheck.Valid("https://a.pl/x.xml.gz"), EpgSourcePolicy.validateUrl("  https://a.pl/x.xml.gz "))
        assertTrue(EpgSourcePolicy.validateUrl("http://h:8080/xmltv.php?username=u&password=p") is UrlCheck.Valid)
    }

    @Test
    fun `bledne adresy`() {
        assertEquals(UrlError.EMPTY, invalid("  "))
        assertEquals(UrlError.NOT_HTTP, invalid("ftp://a.pl/x"))
        assertEquals(UrlError.NOT_HTTP, invalid("file:///sdcard/x.xml"))
        assertEquals(UrlError.NOT_HTTP, invalid("javascript:alert(1)"))
        assertEquals(UrlError.NO_HOST, invalid("http:///x"))
        assertEquals(UrlError.MALFORMED, invalid("http://a b.pl"))
    }

    @Test
    fun `nazwa`() {
        assertEquals("Moje", EpgSourcePolicy.validateName(" Moje "))
        assertNull(EpgSourcePolicy.validateName("   "))
        assertNull(EpgSourcePolicy.validateName("x".repeat(61)))
    }

    @Test
    fun `aktywne zrodla - wylaczone odpadaja, zawezone do dostawcy`() {
        val a = ProviderId("a")
        val b = ProviderId("b")
        val custom = EpgSource("c1", "C", EpgSourceKind.CUSTOM_XMLTV, "http://x/y", true, providerId = a)
        val list = listOf(BuiltInEpgSources.ovh(), BuiltInEpgSources.teleman(enabled = false), custom)
        assertEquals(listOf("builtin.ovh", "c1"), EpgSourcePolicy.activeFor(list, a).map { it.id })
        assertEquals(listOf("builtin.ovh"), EpgSourcePolicy.activeFor(list, b).map { it.id })
    }

    @Test
    fun `teleman i ovh domyslnie wlaczone`() {
        assertTrue(BuiltInEpgSources.teleman().enabled)
        assertTrue(BuiltInEpgSources.ovh().enabled)
    }

    @Test
    fun `duplikat url ignoruje ukosnik koncowy i wielkosc liter`() {
        val list = listOf(BuiltInEpgSources.ovh())
        assertTrue(EpgSourcePolicy.isDuplicateUrl(list, "HTTPS://epg.ovh/pltv.xml.gz/"))
        assertFalse(EpgSourcePolicy.isDuplicateUrl(list, "https://inny.pl/x"))
    }
}
