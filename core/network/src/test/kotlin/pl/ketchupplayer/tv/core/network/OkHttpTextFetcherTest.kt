package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class OkHttpTextFetcherTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stop() = server.shutdown()

    private fun fetcher(max: Long = 1024 * 1024) = OkHttpTextFetcher(NetworkClients.stream("TestUA/1"), max)

    private fun url(path: String = "/list.m3u") = server.url(path).toString()

    @Test
    fun `domyslny User-Agent jest w stylu odtwarzacza a klient strumieni uzywa HTTP 1_1`() =
        runTest {
            server.enqueue(MockResponse().setBody("x"))
            OkHttpTextFetcher(NetworkClients.stream()).fetch(url())
            assertEquals(NetworkClients.DEFAULT_USER_AGENT, server.takeRequest().getHeader("User-Agent"))
            assertEquals(listOf(okhttp3.Protocol.HTTP_1_1), NetworkClients.stream().protocols)
            assertEquals(true, NetworkClients.DEFAULT_USER_AGENT.startsWith("VLC/"))
        }

    @Test
    fun `pobiera tekst i wysyla User-Agent`() =
        runTest {
            server.enqueue(MockResponse().setBody("#EXTM3U"))
            assertEquals("#EXTM3U", fetcher().fetch(url()).getOrThrow())
            assertEquals("TestUA/1", server.takeRequest().getHeader("User-Agent"))
        }

    @Test
    fun `licznik bajtow rosnie podczas czytania linii`() =
        runTest {
            val body = "#EXTM3U\n" + "x".repeat(5000)
            server.enqueue(MockResponse().setBody(body))
            val meter = TransferMeter()
            val fetcher = OkHttpTextFetcher(NetworkClients.stream("TestUA/1"), 1024 * 1024, meter)
            val lines = fetcher.fetchLines(url()) { it.count() }.getOrThrow()
            assertEquals(2, lines)
            assertEquals(body.length.toLong(), meter.bytes.value)
        }

    @Test
    fun `rozpakowuje surowy gzip`() =
        runTest {
            val gz =
                ByteArrayOutputStream().also { o ->
                    GZIPOutputStream(o).use { it.write("skompresowane".toByteArray()) }
                }
            server.enqueue(MockResponse().setBody(Buffer().write(gz.toByteArray())))
            assertEquals("skompresowane", fetcher().fetch(url("/pltv.xml.gz")).getOrThrow())
        }

    @Test
    fun `kod 404 daje NetworkException Http`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val err = fetcher().fetch(url()).exceptionOrNull() as NetworkException.Http
            assertEquals(404, err.code)
        }

    @Test
    fun `zbyt duza odpowiedz`() =
        runTest {
            server.enqueue(MockResponse().setBody("x".repeat(5000)))
            assertTrue(fetcher(max = 1000).fetch(url()).exceptionOrNull() is NetworkException.TooLarge)
        }

    @Test
    fun `przekierowanie jest wykonane`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", url("/real")))
            server.enqueue(MockResponse().setBody("ok"))
            assertEquals("ok", fetcher().fetch(url("/start")).getOrThrow())
        }

    @Test
    fun `klient metadanych odrzuca HTTP bez TLS`() =
        runTest {
            server.enqueue(MockResponse().setBody("x"))
            val result = OkHttpTextFetcher(NetworkClients.metadata()).fetch(url())
            assertTrue(result.exceptionOrNull() is NetworkException.Io)
        }

    @Test
    fun `blad polaczenia to NetworkException Io`() =
        runTest {
            val dead = url()
            server.shutdown()
            assertTrue(fetcher().fetch(dead).exceptionOrNull() is NetworkException.Io)
        }

    @Test
    fun `fetchLines czyta linie strumieniowo`() =
        runTest {
            server.enqueue(MockResponse().setBody("a\nb\r\nc"))
            val lines = fetcher().fetchLines(url()) { it.toList() }.getOrThrow()
            assertEquals(listOf("a", "b", "c"), lines)
        }

    @Test
    fun `fetchLines rozpakowuje gzip`() =
        runTest {
            val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write("x\ny".toByteArray()) } }
            server.enqueue(MockResponse().setBody(Buffer().write(gz.toByteArray())))
            assertEquals(listOf("x", "y"), fetcher().fetchLines(url("/l.gz")) { it.toList() }.getOrThrow())
        }

    @Test
    fun `fetchLines przerywa po limicie, takze dla bomby gzip`() =
        runTest {
            server.enqueue(MockResponse().setBody("x\n".repeat(5000)))
            assertTrue(fetcher(1000).fetchLines(url()) { it.count() }.exceptionOrNull() is NetworkException.TooLarge)
            val bomb =
                ByteArrayOutputStream().also { o ->
                    GZIPOutputStream(o).use { it.write("y\n".repeat(50_000).toByteArray()) }
                }
            server.enqueue(MockResponse().setBody(Buffer().write(bomb.toByteArray())))
            assertTrue(
                fetcher(1000).fetchLines(url("/b.gz")) { it.count() }.exceptionOrNull() is NetworkException.TooLarge,
            )
        }

    @Test
    fun `fetchLines - kod HTTP to blad`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(
                503,
                (fetcher().fetchLines(url()) { it.count() }.exceptionOrNull() as NetworkException.Http).code,
            )
        }
}
