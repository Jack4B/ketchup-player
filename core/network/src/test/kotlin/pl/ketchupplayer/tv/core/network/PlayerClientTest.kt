package pl.ketchupplayer.tv.core.network

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Klient odtwarzacza: User-Agent VLC, nagłówek Accept i przekierowanie 302 na inny serwer z zachowaniem nagłówków. */
class PlayerClientTest {
    private val panel = MockWebServer().apply { start() }
    private val streams = MockWebServer().apply { start() }

    @After
    fun stop() {
        panel.shutdown()
        streams.shutdown()
    }

    @Test
    fun `domyslny User-Agent odtwarzacza to VLC 3`() {
        assertEquals("VLC/3.4.3 LibVLC/3.4.31", NetworkClients.PLAYER_USER_AGENT)
        panel.enqueue(MockResponse().setBody("x"))
        NetworkClients
            .player()
            .newCall(Request.Builder().url(panel.url("/live/u/p/1.ts")).build())
            .execute()
            .close()
        val headers = panel.takeRequest().headers
        assertEquals("VLC/3.4.3 LibVLC/3.4.31", headers["User-Agent"])
        assertEquals("*/*", headers["Accept"])
    }

    /** Panel odpowiada 302 na serwer strumieni (inny host:port); User-Agent i Range idą dalej, jak u VLC. */
    @Test
    fun `302 na inny serwer zachowuje User-Agent i Range`() {
        streams.enqueue(MockResponse().setResponseCode(206).setBody("ts"))
        panel.enqueue(MockResponse().setResponseCode(302).setHeader("Location", streams.url("/s/1.ts").toString()))
        val request =
            Request
                .Builder()
                .url(panel.url("/live/u/p/1.ts"))
                .header("Range", "bytes=0-")
                .build()
        NetworkClients
            .player()
            .newCall(request)
            .execute()
            .use { assertEquals(206, it.code) }
        val first = panel.takeRequest()
        val second = streams.takeRequest()
        assertEquals("/live/u/p/1.ts", first.path)
        assertEquals("/s/1.ts", second.path)
        listOf(first, second).forEach {
            assertEquals("VLC/3.4.3 LibVLC/3.4.31", it.getHeader("User-Agent"))
            assertEquals("bytes=0-", it.getHeader("Range"))
            assertEquals("*/*", it.getHeader("Accept"))
        }
    }

    /** Własny User-Agent listy obowiązuje też po przekierowaniu. */
    @Test
    fun `wlasny User-Agent przezywa przekierowanie`() {
        streams.enqueue(MockResponse().setBody("ts"))
        panel.enqueue(MockResponse().setResponseCode(302).setHeader("Location", streams.url("/s/2.ts").toString()))
        val request =
            Request
                .Builder()
                .url(panel.url("/live/u/p/2.ts"))
                .header("User-Agent", "TiviMate/5.0.0 (Android 12)")
                .build()
        NetworkClients
            .player()
            .newCall(request)
            .execute()
            .close()
        assertEquals("TiviMate/5.0.0 (Android 12)", streams.takeRequest().getHeader("User-Agent"))
    }
}
