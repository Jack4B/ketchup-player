package pl.ketchupplayer.tv.provider.xtream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.provider.api.ProviderError

class XtreamTest {
    private val creds = XtreamCredentials("http://h.example.invalid:8080", "jan", "p@ss w/ord")

    private val auth =
        """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000","max_connections":"2"}}"""
    private val cats =
        """[{"category_id":"1","category_name":"PL | News"},{"category_id":"2","category_name":"Sport"}]"""
    private val streams =
        "[" +
            """{"num":"1","name":"JEDEN HD","stream_id":"101","stream_icon":"http://i.example.invalid/1.png",""" +
            """"epg_channel_id":"jeden.pl","category_id":"1"},""" +
            """{"num":2,"name":"SPORT+","stream_id":102,"stream_icon":"","epg_channel_id":null,"category_id":"2"},""" +
            """{"name":"bez id"},{"stream_id":"104"}""" +
            "]"
    private val responsesOk =
        arrayOf(
            "action=get_live_categories" to Result.success(cats),
            "action=get_live_streams" to Result.success(streams),
            "player_api.php?username" to Result.success(auth),
        )

    private fun provider(vararg responses: Pair<String, Result<String>>): XtreamProvider {
        val map = responses.toMap()
        return XtreamProvider(
            ProviderId("x"),
            creds,
            TextFetcher { url ->
                map.entries.firstOrNull { url.contains(it.key) }?.value ?: Result.failure(NetworkException.Http(404))
            },
        )
    }

    @Test
    fun `adresy API koduja dane logowania`() {
        val url = creds.apiUrl("get_live_streams")
        assertTrue(
            url.startsWith(
                "http://h.example.invalid:8080/player_api.php?username=jan" +
                    "&password=p%40ss%20w%2Ford&action=get_live_streams",
            ),
        )
        assertEquals("http://h.example.invalid:8080/live/jan/p%40ss%20w%2Ford/101.ts", creds.liveUrl("101", "ts"))
    }

    @Test
    fun `toString nie ujawnia hasla`() {
        assertFalse(creds.toString().contains("p@ss"))
        assertFalse(creds.toString().contains("jan"))
    }

    @Test
    fun `normalizacja adresu serwera`() {
        assertEquals(
            "http://h.example.invalid:8080",
            XtreamCredentials.normalizeServer("http://h.example.invalid:8080/"),
        )
        assertEquals(
            "http://h.example.invalid:8080",
            XtreamCredentials.normalizeServer("http://h.example.invalid:8080/c/"),
        )
        assertEquals(
            "http://h.example.invalid",
            XtreamCredentials.normalizeServer("http://h.example.invalid/player_api.php?username=a&password=b"),
        )
        assertEquals(
            "https://h.example.invalid/panel",
            XtreamCredentials.normalizeServer("https://h.example.invalid/panel/c"),
        )
        assertNull(XtreamCredentials.normalizeServer("ftp://h.example.invalid"))
        assertNull(XtreamCredentials.normalizeServer("nie adres"))
    }

    @Test
    fun `parsuje konto`() {
        val a = XtreamParser.account(auth)!!
        assertTrue(a.authorized && a.active)
        assertEquals(1893456000L, a.expiresEpochSec)
        assertEquals(2, a.maxConnections)
        assertFalse(XtreamParser.account("""{"user_info":{"auth":0}}""")!!.authorized)
        assertNull(XtreamParser.account("to nie json"))
    }

    @Test
    fun `laduje kanaly z kategoriami, pomija wpisy bez id lub nazwy`() =
        runTest {
            val p = provider(*responsesOk)
            val ch = p.loadChannels().getOrThrow()
            assertEquals(listOf("JEDEN HD", "SPORT+"), ch.map { it.name })
            assertEquals("PL | News", ch[0].group)
            assertEquals("jeden.pl", ch[0].epgId)
            assertNull(ch[1].epgId)
            assertNull(ch[1].logo)
            assertEquals(listOf("x:l101", "x:l102"), ch.map { it.id })
        }

    @Test
    fun `kanaly nie zawieraja danych logowania`() =
        runTest {
            val p = provider(*responsesOk)
            val ch = p.loadChannels().getOrThrow()
            assertTrue(ch.none { (it.streamUrl ?: "").contains("jan") || (it.streamUrl ?: "").contains("p@ss") })
            assertEquals("xtream:live:101.ts", ch[0].streamUrl)
        }

    @Test
    fun `resolveStream buduje adres z danych logowania`() =
        runTest {
            val p = provider(*responsesOk)
            val spec = p.resolveStream(p.loadChannels().getOrThrow()[0]).getOrThrow()
            assertEquals("http://h.example.invalid:8080/live/jan/p%40ss%20w%2Ford/101.ts", spec.url)
        }

    @Test
    fun `zle dane logowania to Unauthorized`() =
        runTest {
            val p = provider("player_api.php?username" to Result.success("""{"user_info":{"auth":0}}"""))
            assertTrue(p.loadChannels().exceptionOrNull() is ProviderError.Unauthorized)
        }

    @Test
    fun `konto wygasle to Unauthorized`() =
        runTest {
            val p =
                provider(
                    "player_api.php?username" to Result.success("""{"user_info":{"auth":1,"status":"Expired"}}"""),
                )
            assertTrue(p.loadChannels().exceptionOrNull() is ProviderError.Unauthorized)
        }

    @Test
    fun `HTML zamiast JSON to BadResponse, 5xx to Network`() =
        runTest {
            assertTrue(
                provider(
                    "player_api.php?username" to Result.success("<html>"),
                ).loadChannels().exceptionOrNull() is ProviderError.BadResponse,
            )
            assertTrue(
                provider(
                    "player_api.php?username" to Result.failure(NetworkException.Http(502)),
                ).loadChannels().exceptionOrNull() is ProviderError.Network,
            )
        }

    @Test
    fun `adres EPG xmltv`() =
        runTest {
            assertEquals(
                "http://h.example.invalid:8080/xmltv.php?username=jan&password=p%40ss%20w%2Ford",
                provider().epgUrl(),
            )
        }
}
