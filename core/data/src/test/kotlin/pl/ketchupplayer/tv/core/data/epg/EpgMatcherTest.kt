package pl.ketchupplayer.tv.core.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.database.ChannelEpgKey
import pl.ketchupplayer.tv.epg.xmltv.XmlTvChannel

class EpgMatcherTest {
    private val keys =
        listOf(
            ChannelEpgKey("c1", "Jeden.PL", "jeden hd"),
            ChannelEpgKey("c2", null, "info24"),
            ChannelEpgKey("c3", "jeden.pl", "jeden fhd"), // ten sam epgId u drugiego dostawcy
            ChannelEpgKey("c4", null, "tvp 1 hd"),
        )
    private val matcher = EpgMatcher(keys)

    @Test
    fun `dopasowanie po epgId bez wzgledu na wielkosc liter, wszystkie kanaly z tym id`() {
        assertEquals(setOf("c1", "c3"), matcher.match(XmlTvChannel("JEDEN.pl", emptyList())))
    }

    @Test
    fun `dopasowanie po nazwie z odcieciem sufiksow jakosci`() {
        assertEquals(setOf("c2"), matcher.match(XmlTvChannel("x", listOf("INFO24 HD"))))
        assertEquals(setOf("c4"), matcher.match(XmlTvChannel("y", listOf("TVP 1"))))
    }

    @Test
    fun `brak dopasowania`() {
        assertTrue(matcher.match(XmlTvChannel("zzz", listOf("Nieznany"))).isEmpty())
    }

    @Test
    fun `suma dopasowan po id i po nazwie`() {
        assertEquals(setOf("c1", "c3", "c2"), matcher.match(XmlTvChannel("jeden.pl", listOf("Info24"))))
    }
}
