package pl.ketchupplayer.tv.epg.xmltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XmlTvParserTest {
    private val xml =
        """<?xml version="1.0" encoding="UTF-8"?>
        |<tv generator-info-name="test">
        |  <channel id="jeden.pl"><display-name>JEDEN HD</display-name><display-name lang="pl">Jeden</display-name></channel>
        |  <channel id="info24.pl"><display-name>INFO24</display-name></channel>
        |  <channel><display-name>Bez id</display-name></channel>
        |  <programme start="20261031200000 +0100" stop="20261031210000 +0100" channel="jeden.pl">
        |    <title lang="pl">Wiadomości</title><title lang="en">News</title>
        |    <desc lang="pl">Serwis &amp; pogoda</desc><category lang="pl">Info</category>
        |  </programme>
        |  <programme start="20261031210000 +0100" stop="20261031220000 +0100" channel="jeden.pl"><title>Film</title></programme>
        |  <programme start="20261031200000 +0000" stop="20261031190000 +0000" channel="jeden.pl"><title>Odwrócone czasy</title></programme>
        |  <programme start="zle" stop="20261031190000 +0000" channel="jeden.pl"><title>Zła data</title></programme>
        |  <programme start="20261031200000 +0000" stop="20261031210000 +0000" channel="info24.pl"></programme>
        |</tv>
        """.trimMargin()

    private fun run(shift: Int = 0): Triple<List<XmlTvChannel>, List<XmlTvProgramme>, XmlTvParser.Result> {
        val channels = mutableListOf<XmlTvChannel>()
        val programmes = mutableListOf<XmlTvProgramme>()
        val r = XmlTvParser(shift).parse(xml.byteInputStream(), channels::add, programmes::add)
        return Triple(channels, programmes, r)
    }

    @Test
    fun `parsuje kanaly z nazwami i pomija kanal bez id`() {
        val (channels, _, r) = run()
        assertEquals(listOf("jeden.pl", "info24.pl"), channels.map { it.id })
        assertEquals(listOf("JEDEN HD", "Jeden"), channels[0].displayNames)
        assertEquals(2, r.channels)
    }

    @Test
    fun `parsuje audycje, pierwszy tytul, opis z encjami i kategorie`() {
        val (_, programmes, _) = run()
        val p = programmes.first()
        assertEquals("Wiadomości", p.title)
        assertEquals("Serwis & pogoda", p.description)
        assertEquals("Info", p.category)
        assertEquals(1_793_473_200_000L, p.startMs) // 2026-10-31 20:00 +01:00 = 19:00 UTC
        assertEquals(3_600_000L, p.endMs - p.startMs)
    }

    @Test
    fun `pomija bledne audycje i liczy je`() {
        val (_, programmes, r) = run()
        assertEquals(listOf("Wiadomości", "Film"), programmes.map { it.title })
        assertEquals(2, r.programmes)
        assertEquals(4, r.skipped) // kanał bez id, odwrócone czasy, zła data, brak tytułu
    }

    @Test
    fun `przesuniecie czasu o minuty`() {
        val base = run().second.first().startMs
        assertEquals(base + 30 * 60_000L, run(shift = 30).second.first().startMs)
        assertEquals(base - 60 * 60_000L, run(shift = -60).second.first().startMs)
    }

    @Test
    fun `czas bez strefy to UTC, bledny to null`() {
        assertEquals(XmlTvParser.parseTime("20261031190000 +0000"), XmlTvParser.parseTime("20261031190000"))
        assertNull(XmlTvParser.parseTime("2026-10-31"))
        assertNull(XmlTvParser.parseTime(null))
    }

    @Test
    fun `przerwanie parsowania przez shouldContinue`() {
        var seen = 0
        XmlTvParser().parse(xml.byteInputStream(), {}, { seen++ }, shouldContinue = { seen < 1 })
        assertEquals(1, seen)
    }
}
