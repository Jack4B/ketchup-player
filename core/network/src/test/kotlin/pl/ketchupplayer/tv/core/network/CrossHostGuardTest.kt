package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Przekierowanie na inny host: dalej idą tylko nagłówki tożsamości klienta, dane uwierzytelniające zostają. */
class CrossHostGuardTest {
    private val panel = MockWebServer().apply { start() }
    private val other = MockWebServer().apply { start() }

    @After
    fun stop() {
        panel.shutdown()
        other.shutdown()
    }

    private fun request(url: okhttp3.HttpUrl) =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", "AgentListy/1")
            .header("Range", "bytes=0-")
            .header("Authorization", "Bearer tajny")
            .header("Cookie", "sid=tajne")
            .header("X-Api-Key", "klucz-api")
            .build()

    /** Panel jest pod `localhost`, cel pod `127.0.0.1`: dwa różne hosty, więc przekierowanie jest „obce”. */
    private fun otherHostUrl(path: String) =
        other
            .url(path)
            .newBuilder()
            .host("127.0.0.1")
            .build()
            .toString()

    @Test
    fun `przekierowanie na inny host nie niesie danych uwierzytelniajacych, a tozsamosc tak`() {
        other.enqueue(MockResponse().setBody("ts"))
        panel.enqueue(MockResponse().setResponseCode(302).setHeader("Location", otherHostUrl("/stream/1.ts")))
        NetworkClients
            .player()
            .newCall(request(panel.url("/live/u/p/1.ts")))
            .execute()
            .close()

        val first = panel.takeRequest()
        assertEquals("panel dostaje komplet", "Bearer tajny", first.getHeader("Authorization"))
        assertEquals("klucz-api", first.getHeader("X-Api-Key"))

        val second = other.takeRequest()
        assertEquals("AgentListy/1", second.getHeader("User-Agent"))
        assertEquals("bytes=0-", second.getHeader("Range"))
        assertNull(second.getHeader("Authorization"))
        assertNull(second.getHeader("Cookie"))
        assertNull(second.getHeader("X-Api-Key"))
    }

    @Test
    fun `przekierowanie w obrebie tego samego hosta zachowuje naglowki`() {
        panel.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/gdzie-indziej"))
        panel.enqueue(MockResponse().setBody("ok"))
        NetworkClients
            .stream()
            .newCall(request(panel.url("/a")))
            .execute()
            .close()
        panel.takeRequest()
        val second = panel.takeRequest()
        assertEquals("/gdzie-indziej", second.path)
        assertEquals("klucz-api", second.getHeader("X-Api-Key"))
        assertEquals("AgentListy/1", second.getHeader("User-Agent"))
    }

    @Test
    fun `fetcher listy przekierowany na inny host zachowuje User-Agent listy i nie przenosi cudzych naglowkow`() =
        runTest {
            other.enqueue(MockResponse().setBody("[]"))
            panel.enqueue(MockResponse().setResponseCode(302).setHeader("Location", otherHostUrl("/api")))
            val fetcher = OkHttpTextFetcher(NetworkClients.stream()).withUserAgent("AgentListy/1")
            val text =
                fetcher
                    .fetchWithHeaders(panel.url("/player_api.php").toString(), mapOf("X-Api-Key" to "klucz-api"))
                    .getOrThrow()
            assertEquals("[]", text)
            assertEquals("klucz-api", panel.takeRequest().getHeader("X-Api-Key"))
            val second = other.takeRequest()
            assertEquals("AgentListy/1", second.getHeader("User-Agent"))
            assertNull(second.getHeader("X-Api-Key"))
        }
}
