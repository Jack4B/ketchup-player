package pl.ketchupplayer.tv.provider.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ContentType
import pl.ketchupplayer.tv.core.model.ProviderId

class M3uParserTest {
    private val pid = ProviderId("p1")

    private val sample =
        """
        |#EXTM3U url-tvg="https://epg.example.invalid/pl.xml.gz"
        |#EXTINF:-1 tvg-id="jeden.pl" tvg-chno="101" tvg-logo="https://img.example.invalid/1.png" group-title="Ogólne",JEDEN HD
        |http://h.example.invalid:8080/live/u/p/1.ts
        |#EXTINF:-1 group-title="Sport",Sport, czyli "Plus"
        |#EXTVLCOPT:http-user-agent=Mozilla/5.0
        |http://h.example.invalid/live/2.m3u8
        |#EXTINF:-1,Film VOD
        |http://h.example.invalid/movie/u/p/9.mkv
        |#EXTINF:-1,Bez adresu
        |#EXTINF:-1,Serial
        |http://h.example.invalid/series/u/p/5.mp4
        |śmieci bez znaczenia
        """.trimMargin()

    @Test
    fun `parsuje atrybuty, naglowek EPG i typy`() {
        val list = M3uParser.parse(pid, sample)
        assertEquals("https://epg.example.invalid/pl.xml.gz", list.epgUrl)
        assertEquals(4, list.channels.size)
        val first = list.channels[0]
        assertEquals("JEDEN HD", first.name)
        assertEquals("jeden.pl", first.epgId)
        assertEquals(101, first.number)
        assertEquals("Ogólne", first.group)
        assertEquals("https://img.example.invalid/1.png", first.logo)
        assertEquals(ContentType.LIVE, first.type)
        assertEquals(ContentType.VOD, list.channels[2].type)
        assertEquals(ContentType.SERIES, list.channels[3].type)
    }

    @Test
    fun `przecinek w nazwie i user agent z VLCOPT`() {
        val c = M3uParser.parse(pid, sample).channels[1]
        assertEquals("Sport, czyli \"Plus\"", c.name)
        assertEquals("Mozilla/5.0", c.userAgent)
        assertEquals("Sport", c.group)
    }

    @Test
    fun `bledne wpisy sa pomijane i liczone`() {
        val list = M3uParser.parse(pid, sample)
        assertEquals(2, list.skippedLines) // wpis bez adresu + śmieci
    }

    @Test
    fun `BOM CRLF i puste linie`() {
        val text = "﻿#EXTM3U\r\n\r\n#EXTINF:-1,A\r\nhttp://x.example.invalid/a\r\n"
        assertEquals(listOf("A"), M3uParser.parse(pid, text).channels.map { it.name })
    }

    @Test
    fun `numer z poczatku nazwy gdy brak tvg-chno, inaczej pozycja`() {
        val text =
            "#EXTM3U\n#EXTINF:-1,12 TVP1\nhttp://x.example.invalid/a\n" +
                "#EXTINF:-1,Inny\nhttp://x.example.invalid/b\n"
        val ch = M3uParser.parse(pid, text).channels
        assertEquals(12, ch[0].number)
        assertEquals(2, ch[1].number)
    }

    @Test
    fun `id jest stabilne i unikalne dla innego adresu`() {
        val a = M3uParser.parse(pid, sample).channels
        val b = M3uParser.parse(pid, sample).channels
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(a.size, a.map { it.id }.toSet().size)
        assertTrue(a.all { it.id.startsWith("p1:") })
    }

    @Test
    fun `pusta lista i brak naglowka epg`() {
        val list = M3uParser.parse(pid, "")
        assertTrue(list.channels.isEmpty())
        assertNull(list.epgUrl)
    }

    @Test
    fun `normalizacja nazw`() {
        assertEquals("wiadomosci hd", Channel.normalizeName("Wiadomości HD"))
        assertEquals("tvp 1 hd", Channel.normalizeName("TVP-1  HD!"))
        assertEquals("lodz", Channel.normalizeName("Łódź"))
    }
}
