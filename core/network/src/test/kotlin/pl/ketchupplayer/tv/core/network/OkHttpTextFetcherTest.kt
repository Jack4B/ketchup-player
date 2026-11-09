package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

class OkHttpTextFetcherTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stop() = server.shutdown()

    private fun fetcher(max: Long = 1024 * 1024) =
        OkHttpTextFetcher(NetworkClients.stream("TestUA/1"), max, streamMaxBytes = max)

    private fun url(path: String = "/list.m3u") = server.url(path).toString()

    @Test
    fun `domyslny User-Agent listy to okhttp 4 10 0`() =
        runTest {
            server.enqueue(MockResponse().setBody("x"))
            OkHttpTextFetcher(NetworkClients.stream()).fetch(url())
            assertEquals("okhttp/4.10.0", server.takeRequest().getHeader("User-Agent"))
            assertEquals("okhttp/4.10.0", NetworkClients.DEFAULT_USER_AGENT)
        }

    /** Przechwycenie IPTV Extreme: dokładnie cztery nagłówki w tej kolejności, bez `Accept` i bez niczego więcej. */
    @Test
    fun `import wysyla dokladnie cztery naglowki w kolejnosci IPTV Extreme`() =
        runTest {
            server.enqueue(MockResponse().setBody("[]"))
            OkHttpTextFetcher(NetworkClients.stream()).fetch(url("/player_api.php?username=u&password=p"))
            val headers = server.takeRequest().headers
            assertEquals(
                listOf("User-Agent", "Host", "Connection", "Accept-Encoding"),
                List(headers.size) { headers.name(it) },
            )
            assertEquals("okhttp/4.10.0", headers["User-Agent"])
            assertEquals("Keep-Alive", headers["Connection"])
            assertEquals("gzip", headers["Accept-Encoding"])
        }

    /** Wybrany dla listy User-Agent zastępuje domyślny, a zestaw i kolejność nagłówków zostają te same. */
    @Test
    fun `User-Agent listy zastepuje domyslny bez zmiany reszty naglowkow`() =
        runTest {
            server.enqueue(MockResponse().setBody("[]"))
            OkHttpTextFetcher(NetworkClients.stream()).withUserAgent("VLC/3.0.20 LibVLC/3.0.20").fetch(url())
            val headers = server.takeRequest().headers
            assertEquals(
                listOf("User-Agent", "Host", "Connection", "Accept-Encoding"),
                List(headers.size) { headers.name(it) },
            )
            assertEquals("VLC/3.0.20 LibVLC/3.0.20", headers["User-Agent"])
        }

    /**
     * Błąd 513 (jak u dostawców blokujących łącze): wyjątek niesie stałą podpowiedź rozpoznaną w treści, nigdy samą
     * treść ani nagłówek `Server` (issue #12).
     */
    @Test
    fun `blad HTTP niesie krotki opis serwera bez danych logowania`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(513)
                    .setHeader("Server", "nginx")
                    .setBody("<html><b>Blocked</b> ?username=jan&password=tajne123 too many connections</html>"),
            )
            val failure =
                OkHttpTextFetcher(
                    NetworkClients.stream(),
                ).fetch(url()).exceptionOrNull() as NetworkException.Http
            assertEquals(513, failure.code)
            val msg = failure.message.orEmpty()
            assertEquals(true, "zbyt wiele połączeń" in msg)
            assertEquals(false, "tajne123" in msg || "jan&" in msg || "Blocked" in msg || "nginx" in msg)
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

    /** Raport z boxa: lista ok. 64 MB była odrzucana. Strumień listy ma własny, wysoki limit niezależny od [fetch]. */
    @Test
    fun `lista czytana strumieniem nie podlega limitowi tekstu w pamieci`() =
        runTest {
            val big = "x\n".repeat(5000) // 10 KB: ponad limit tekstu (1 KB), poniżej limitu strumienia (100 KB)
            val f = OkHttpTextFetcher(NetworkClients.stream("TestUA/1"), maxBytes = 1000, streamMaxBytes = 100_000)
            server.enqueue(MockResponse().setBody(big))
            assertTrue(f.fetch(url()).exceptionOrNull() is NetworkException.TooLarge)
            server.enqueue(MockResponse().setBody(big))
            assertEquals(5000, f.fetchLines(url()) { it.count() }.getOrThrow())
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

    private val m3u = (1..400).joinToString("\n", prefix = "#EXTM3U\n") { "#EXTINF:-1,Kanal $it\nhttp://x/$it" }

    /** Panele składające listę w locie często zrywają połączenie; inne odtwarzacze biorą to, co dotarło. */
    @Test
    fun `lista urwana w polowie daje to co dotarlo zamiast bledu`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(m3u).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            val lines = fetcher(10_000_000).fetchLines(url()) { it.toList() }.getOrThrow()
            assertTrue("dotarlo ${lines.size} linii", lines.size in 100..799)
            assertEquals("#EXTM3U", lines.first())
        }

    @Test
    fun `lista gzip urwana w polowie daje to co dotarlo zamiast bledu`() =
        runTest {
            val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(m3u.toByteArray()) } }
            server.enqueue(
                MockResponse()
                    .setBody(Buffer().write(gz.toByteArray()))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            val lines = fetcher(10_000_000).fetchLines(url()) { it.toList() }.getOrThrow()
            assertEquals("#EXTM3U", lines.first())
        }

    @Test
    fun `urwanie bez zadnych danych nadal jest bledem`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertTrue(fetcher().fetchLines(url()) { it.toList() }.isFailure)
        }

    @Test
    fun `tresc odpowiedzi serwera ze sciezka bez schematu nie trafia do wyjatku`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Server", "nginx")
                    .setBody("The requested URL /user123/pass456/77 was not found on this server"),
            )
            val failure = OkHttpTextFetcher(NetworkClients.stream()).fetch(url()).exceptionOrNull()
            val msg = failure?.message.orEmpty()
            assertEquals(false, "user123" in msg || "pass456" in msg || "requested URL" in msg)
            assertEquals(true, "HTTP 404" in msg)
            assertEquals(false, "nginx" in msg)
        }

    private suspend fun failureWith(
        server: MockWebServer,
        url: String,
        build: MockResponse.() -> Unit,
    ): String {
        server.enqueue(MockResponse().setResponseCode(503).apply(build))
        val failure = OkHttpTextFetcher(NetworkClients.stream()).fetch(url).exceptionOrNull()
        return failure?.message.orEmpty()
    }

    @Test
    fun `naglowek Server z danymi dostepu nie trafia do wyjatku`() =
        runTest {
            val msg = failureWith(server, url()) { setHeader("Server", "user123:pass456 /user123/pass456/77") }
            assertEquals("HTTP 503", msg)
        }

    @Test
    fun `Retry-After jest przekazywany tylko jako sparsowane sekundy`() =
        runTest {
            assertEquals("HTTP 503 · ponów po: 120 s", failureWith(server, url()) { setHeader("Retry-After", "120") })
        }

    @Test
    fun `niepoprawny Retry-After z danymi dostepu jest pomijany`() =
        runTest {
            val bad = listOf("soon /user123/pass456/77", "120; user123:pass456", "-5", "1.5", "99999999")
            bad.forEach { value ->
                val msg = failureWith(server, url()) { setHeader("Retry-After", value) }
                assertEquals(value, "HTTP 503", msg)
            }
        }

    /**
     * Błąd z emulatora (import konta Xtream wywalał aplikację): treść odpowiedzi tekstowej (`fetch`, `fetchWithBody`)
     * była czytana na wątku wołającego, czyli głównym (`NetworkOnMainThreadException`). Wołający udaje wątek główny.
     */
    @Test
    fun `cialo odpowiedzi tekstowej nie jest czytane na watku wolajacego`() {
        server.enqueue(MockResponse().setBody("[1,2,3]"))
        val readers = ConcurrentLinkedQueue<String>()
        val spying =
            NetworkClients
                .stream()
                .newBuilder()
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val body = requireNotNull(response.body)
                    val spy =
                        object : ForwardingSource(body.source()) {
                            override fun read(
                                sink: Buffer,
                                byteCount: Long,
                            ): Long {
                                readers += Thread.currentThread().name
                                return super.read(sink, byteCount)
                            }
                        }.buffer()
                    response.newBuilder().body(spy.asResponseBody(body.contentType(), body.contentLength())).build()
                }.build()
        val caller = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }
        try {
            val text = runBlocking(caller.asCoroutineDispatcher()) { OkHttpTextFetcher(spying).fetch(url()) }
            assertEquals("[1,2,3]", text.getOrThrow())
        } finally {
            caller.shutdown()
        }
        assertTrue(readers.toString(), readers.isNotEmpty() && readers.none { it == "fake-main" })
    }
}
