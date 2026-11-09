package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** POST z danymi w ciele (lista wymaga POST zamiast GET). Dane wyłącznie fikcyjne: user123, pass456. */
class OkHttpTextFetcherPostTest {
    private lateinit var server: MockWebServer
    private val body = FormBody("username=user123&password=pass456&type=m3u_plus")
    private val playlist = "#EXTM3U\n#EXTINF:-1,Kanal\nhttp://h.example.invalid/1\n"

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stop() = server.shutdown()

    private fun fetcher() = OkHttpTextFetcher(NetworkClients.stream("TestUA/1"))

    private fun url(path: String = "/get.php") = server.url(path).toString()

    @Test
    fun `POST wysyla cialo form-urlencoded, a adres nie ma danych dostepu`() =
        runTest {
            server.enqueue(MockResponse().setBody(playlist))
            val text = fetcher().fetchWithBody(url(), body).getOrThrow()
            val request = server.takeRequest()
            assertEquals(playlist, text)
            assertEquals("POST", request.method)
            assertEquals("application/x-www-form-urlencoded", request.getHeader("Content-Type"))
            assertEquals(body.encoded, request.body.readUtf8())
            assertEquals("/get.php", request.path)
            assertEquals("TestUA/1", request.getHeader("User-Agent"))
            assertEquals("*/*", request.getHeader("Accept"))
        }

    @Test
    fun `serwer daje 504 na GET i 200 na POST - lista z POST sie importuje`() =
        runTest {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.method == "POST") {
                            MockResponse().setBody(playlist)
                        } else {
                            MockResponse().setResponseCode(504).setHeader("Retry-After", "120")
                        }
                }
            val get = fetcher().fetchLines(url("/get.php?username=user123&password=pass456")) { it.toList() }
            val post = fetcher().fetchLinesWithBody(url(), body) { it.toList() }
            val failure = get.exceptionOrNull() as NetworkException.Http
            assertEquals(504, failure.code)
            assertTrue(failure.message, failure.message.orEmpty().contains("ponów po: 120 s"))
            assertEquals(3, post.getOrThrow().size)
        }

    @Test
    fun `fetchLinesWithBody i fetchStreamWithBody wysylaja POST`() =
        runTest {
            server.enqueue(MockResponse().setBody(playlist))
            server.enqueue(MockResponse().setBody(playlist))
            val lines = fetcher().fetchLinesWithBody(url(), body) { it.count() }.getOrThrow()
            val bytes = fetcher().fetchStreamWithBody(url(), body) { it.readBytes().size }.getOrThrow()
            assertEquals(3, lines)
            assertEquals(playlist.length, bytes)
            repeat(2) {
                val r = server.takeRequest()
                assertEquals("POST", r.method)
                assertEquals(body.encoded, r.body.readUtf8())
            }
        }

    @Test
    fun `przekierowanie 307 i 308 zachowuje POST i cialo`() =
        runTest {
            for (code in listOf(307, 308)) {
                server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", "/next"))
                server.enqueue(MockResponse().setBody(playlist))
                assertEquals(playlist, fetcher().fetchWithBody(url(), body).getOrThrow())
                val first = server.takeRequest()
                val second = server.takeRequest()
                assertEquals("POST", first.method)
                assertEquals("POST", second.method)
                assertEquals("/next", second.path)
                assertEquals(body.encoded, second.body.readUtf8())
                assertEquals("application/x-www-form-urlencoded", second.getHeader("Content-Type"))
            }
        }

    @Test
    fun `przekierowanie 301 302 303 zamienia POST na GET bez ciala (zachowanie OkHttp)`() =
        runTest {
            for (code in listOf(301, 302, 303)) {
                server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", "/next"))
                server.enqueue(MockResponse().setBody(playlist))
                assertEquals(playlist, fetcher().fetchWithBody(url(), body).getOrThrow())
                assertEquals("POST", server.takeRequest().method)
                val second = server.takeRequest()
                assertEquals("GET", second.method)
                assertEquals(0L, second.bodySize)
                assertNull(second.getHeader("Content-Type"))
            }
        }

    @Test
    fun `przekierowanie POST 307 na inny host jest zatrzymane, ciala nie dostaje nikt`() =
        runTest {
            val other = "http://127.0.0.1:${server.port}/steal"
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other))
            server.enqueue(MockResponse().setBody(playlist))
            val start = "http://localhost:${server.port}/get.php"
            val failure = fetcher().fetchWithBody(start, body).exceptionOrNull()
            assertTrue(failure.toString(), failure is NetworkException.Io)
            assertTrue(failure?.cause is PostRedirectBlockedException)
            assertEquals(1, server.requestCount)
            assertFalse(failure?.message.orEmpty().contains("pass456"))
        }

    @Test
    fun `blad HTTP z POST nie ujawnia ciala w komunikacie ani w toString`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(503).setBody("username=user123 password=pass456"))
            val failure = fetcher().fetchWithBody(url(), body).exceptionOrNull() as NetworkException.Http
            val text = failure.message.orEmpty() + failure.toString() + body.toString()
            assertEquals(503, failure.code)
            assertFalse(text, text.contains("user123") || text.contains("pass456"))
        }

    @Test
    fun `domyslna implementacja TextFetcher nie obsluguje POST (zwraca blad)`() =
        runTest {
            val simple = TextFetcher { Result.success("x") }
            assertTrue(simple.fetchWithBody("http://h.example.invalid/", body).isFailure)
        }
}
