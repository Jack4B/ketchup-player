package pl.ketchupplayer.tv.provider.xtream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.network.TextFetcher

/**
 * Import konta Xtream przez `player_api.php` w kolejności z przechwycenia IPTV Extreme (GET, bez get.php), dane
 * syntetyczne o kształcie prawdziwych odpowiedzi. Dane wyłącznie fikcyjne.
 */
class XtreamApiImportTest {
    private val id = ProviderId("p1")
    private val creds = XtreamCredentials("http://h.example.invalid:8080", "user123", "pass456")
    private val auth = """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000"},"server_info":{}}"""
    private val liveCats = """[{"category_id":"1","category_name":"PL | News","parent_id":0}]"""
    private val icon = "http://i.example.invalid/1.png"
    private val live =
        """[{"num":1,"name":"JEDEN HD","stream_type":"live","stream_id":101,"stream_icon":"$icon",""" +
            """"epg_channel_id":"Jeden.pl","category_id":"1","added":"1600000000"},""" +
            """{"num":"2","name":"Bez grupy","stream_id":"102","stream_icon":null,""" +
            """"epg_channel_id":"","category_id":"9"}]"""
    private val vodCats = """[{"category_id":"5","category_name":"Akcja"}]"""
    private val vod =
        """[{"stream_id":"7","name":"Film (2019)","container_extension":"mp4","year":"2019","category_id":"5"}]"""
    private val seriesCats = """[{"category_id":"6","category_name":"Dramat"}]"""
    private val series = """[{"series_id":"9","name":"Serial","year":"2020","category_id":"6"}]"""

    private fun recording(requests: MutableList<String>) =
        TextFetcher { url ->
            requests += url
            val action = Regex("action=([a-z_]+)").find(url)?.groupValues?.get(1)
            Result.success(
                when (action) {
                    null -> auth
                    "get_live_categories" -> liveCats
                    "get_live_streams" -> live
                    "get_vod_categories" -> vodCats
                    "get_vod_streams" -> vod
                    "get_series_categories" -> seriesCats
                    "get_series" -> series
                    else -> "[]"
                },
            )
        }

    @Test
    fun `import wysyla zapytania w kolejnosci IPTV Extreme, tylko GET player_api`() =
        runTest {
            val requests = mutableListOf<String>()
            val provider = XtreamProvider(id, creds, recording(requests))
            assertTrue(provider.loadChannels().isSuccess)
            assertTrue(provider.loadVod().isSuccess)
            val actions = requests.map { Regex("action=([a-z_]+)").find(it)?.groupValues?.get(1) }
            assertEquals(
                listOf(
                    null,
                    "get_live_categories",
                    "get_live_streams",
                    "get_vod_categories",
                    "get_vod_streams",
                    "get_series_categories",
                    "get_series",
                ),
                actions,
            )
            val prefix = "http://h.example.invalid:8080/player_api.php?username=user123&password=pass456"
            assertTrue(requests.all { it.startsWith(prefix) })
            assertTrue(requests.none { "get.php" in it })
        }

    @Test
    fun `kanaly live z kategoriami, logo i epg_channel_id`() =
        runTest {
            val channels = XtreamProvider(id, creds, recording(mutableListOf())).loadChannels().getOrThrow()
            assertEquals(2, channels.size)
            val first = channels.first()
            assertEquals("p1:l101", first.id)
            assertEquals("JEDEN HD", first.name)
            assertEquals("PL | News", first.group)
            assertEquals("Jeden.pl", first.epgId)
            assertEquals("http://i.example.invalid/1.png", first.logo)
            assertEquals("xtream:live:101.ts", first.streamUrl)
            assertEquals(2, channels[1].number)
            assertNull(channels[1].group) // kategoria spoza listy
            assertNull(channels[1].epgId)
            assertNull(channels[1].logo)
        }

    /** Duża odpowiedź idzie przez strumień: tysiące wpisów bez składania całego tekstu w jedną wartość. */
    @Test
    fun `duza lista live czytana strumieniowo`() {
        val body =
            (1..30_000).joinToString(prefix = "[", postfix = "]", separator = ",") { n ->
                """{"num":$n,"name":"Kanał $n","stream_id":$n,"category_id":"1","epg_channel_id":"k$n"}"""
            }
        val channels = XtreamParser.liveChannels(id, body.byteInputStream(), mapOf("1" to "PL"))
        assertNotNull(channels)
        assertEquals(30_000, channels?.size)
        assertEquals("k30000", channels?.last()?.epgId)
    }

    @Test
    fun `odpowiedz bledu zamiast tablicy daje null a nie wyjatek`() {
        assertNull(XtreamParser.liveChannels(id, """{"user_info":{"auth":0}}""".byteInputStream(), emptyMap()))
        assertNull(XtreamParser.liveChannels(id, "<html>520</html>".byteInputStream(), emptyMap()))
    }

    @Test
    fun `konto z adresu get php i player_api php`() {
        val getPhp =
            XtreamCredentials.fromPlaylistUrl(
                "http://h.example.invalid:8080/get.php?username=u1&password=p%26w&type=m3u_plus&output=ts",
            )
        assertEquals(XtreamCredentials("http://h.example.invalid:8080", "u1", "p&w"), getPhp)
        val api =
            XtreamCredentials.fromPlaylistUrl("https://h.example.invalid/pre/player_api.php?password=p2&username=u2")
        assertEquals(XtreamCredentials("https://h.example.invalid/pre", "u2", "p2"), api)
        val amp =
            XtreamCredentials.fromPlaylistUrl("http://h.example.invalid/get.php?username=u&amp;password=p&amp;type=m3u")
        assertEquals("u", amp?.username)
    }

    @Test
    fun `zwykla lista M3U i adresy bez danych nie sa kontem Xtream`() {
        assertNull(XtreamCredentials.fromPlaylistUrl("http://h.example.invalid/list.m3u"))
        assertNull(XtreamCredentials.fromPlaylistUrl("http://h.example.invalid/get.php?type=m3u"))
        assertNull(XtreamCredentials.fromPlaylistUrl("http://h.example.invalid/get.php?username=u"))
        assertNull(XtreamCredentials.fromPlaylistUrl("http://h.example.invalid/list.m3u?username=u&password=p"))
        assertNull(XtreamCredentials.fromPlaylistUrl("ftp://h.example.invalid/get.php?username=u&password=p"))
    }
}
