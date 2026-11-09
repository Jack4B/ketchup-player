package pl.ketchupplayer.tv.provider.m3u

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.RequestMethod
import pl.ketchupplayer.tv.core.network.FormBody
import pl.ketchupplayer.tv.core.network.TextFetcher

/** POST listy M3U: dane z get.php w ciele, adres bez loginu i hasła. Dane wyłącznie fikcyjne. */
class M3uRequestTest {
    @Test
    fun `get-php - username password type output trafiaja do ciala, adres jest czysty`() {
        val r =
            M3uRequest.post(
                "http://panel.example.invalid:8080/get.php?username=user123&password=pass456&type=m3u_plus&output=ts",
            )
        assertEquals("http://panel.example.invalid:8080/get.php", r.url)
        assertEquals("username=user123&password=pass456&type=m3u_plus&output=ts", r.body?.encoded)
    }

    @Test
    fun `wartosci zakodowane zostaja bez zmian, pozostale parametry zostaja w adresie`() {
        val r =
            M3uRequest.post(
                "http://p.example.invalid/c/get.php?username=us%40er&password=pa%24s%2Bw&type=m3u&lang=pl#frag",
            )
        assertEquals("http://p.example.invalid/c/get.php?lang=pl", r.url)
        assertEquals("username=us%40er&password=pa%24s%2Bw&type=m3u", r.body?.encoded)
    }

    @Test
    fun `inny adres z username i password tez przenosi dane do ciala`() {
        val r = M3uRequest.post("http://p.example.invalid/list.php?password=pass456&username=user123&token=abc")
        assertEquals("http://p.example.invalid/list.php?token=abc", r.url)
        assertEquals("password=pass456&username=user123", r.body?.encoded)
    }

    @Test
    fun `inny adres bez danych - POST z pustym cialem i adresem bez zmian`() {
        val r = M3uRequest.post("http://p.example.invalid/playlist.m3u?foo=1&type=x")
        assertEquals("http://p.example.invalid/playlist.m3u?foo=1&type=x", r.url)
        assertEquals("", r.body?.encoded)
        assertEquals(
            "http://p.example.invalid/playlist.m3u",
            M3uRequest.post("http://p.example.invalid/playlist.m3u").url,
        )
    }

    @Test
    fun `get-php bez zapytania - puste cialo`() {
        val r = M3uRequest.post("http://p.example.invalid/get.php")
        assertEquals("http://p.example.invalid/get.php", r.url)
        assertEquals("", r.body?.encoded)
    }

    @Test
    fun `FormBody nie ujawnia tresci w toString`() {
        val text = M3uRequest.post("http://p.example.invalid/get.php?username=user123&password=pass456").body.toString()
        assertFalse(text, text.contains("user123") || text.contains("pass456"))
    }

    private class Recorder(
        private val playlist: String,
    ) : TextFetcher {
        val calls = mutableListOf<String>()
        var postBody: FormBody? = null

        override suspend fun fetch(url: String): Result<String> {
            calls += "GET $url"
            return Result.failure(
                pl.ketchupplayer.tv.core.network.NetworkException
                    .Http(504),
            )
        }

        override suspend fun fetchWithBody(
            url: String,
            body: FormBody,
        ): Result<String> {
            calls += "POST $url"
            postBody = body
            return Result.success(playlist)
        }
    }

    @Test
    fun `dostawca w trybie POST laduje liste cialem, a EPG wylicza z oryginalnego adresu`() =
        runTest {
            val getPhp = "http://panel.example.invalid:8080/get.php?username=user123&password=pass456&type=m3u_plus"
            val fetcher = Recorder("#EXTM3U\n#EXTINF:-1,A\nhttp://h.example.invalid/a\n")
            val p = M3uProvider(ProviderId("p"), getPhp, fetcher, RequestMethod.POST)
            assertEquals(listOf("A"), p.loadChannels().getOrThrow().map { it.name })
            assertEquals(listOf("POST http://panel.example.invalid:8080/get.php"), fetcher.calls)
            assertEquals("username=user123&password=pass456&type=m3u_plus", fetcher.postBody?.encoded)
            assertTrue(p.epgUrl().orEmpty().startsWith("http://panel.example.invalid:8080/xmltv.php"))
        }

    @Test
    fun `dostawca w trybie GET nadal uzywa GET`() =
        runTest {
            val getPhp = "http://panel.example.invalid:8080/get.php?username=user123&password=pass456"
            val fetcher = Recorder("#EXTM3U\n")
            val p = M3uProvider(ProviderId("p"), getPhp, fetcher)
            p.loadChannels()
            assertEquals(listOf("GET $getPhp"), fetcher.calls)
        }
}
