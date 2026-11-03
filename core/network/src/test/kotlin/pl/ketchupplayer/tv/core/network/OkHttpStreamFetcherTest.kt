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

class OkHttpStreamFetcherTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stop() = server.shutdown()

    private val fetcher = OkHttpStreamFetcher(NetworkClients.stream())

    @Test
    fun `czyta zwykly strumien`() =
        runTest {
            server.enqueue(MockResponse().setBody("abc"))
            val text = fetcher.open(server.url("/x").toString()) { it.readBytes().decodeToString() }.getOrThrow()
            assertEquals("abc", text)
        }

    @Test
    fun `rozpakowuje gzip po magicznych bajtach`() =
        runTest {
            val gz =
                ByteArrayOutputStream().also { o ->
                    GZIPOutputStream(o).use { it.write("skompresowane".toByteArray()) }
                }
            server.enqueue(MockResponse().setBody(Buffer().write(gz.toByteArray())))
            assertEquals(
                "skompresowane",
                fetcher
                    .open(server.url("/p.xml.gz").toString()) {
                        it.readBytes().decodeToString()
                    }.getOrThrow(),
            )
        }

    @Test
    fun `404 to Http`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            assertTrue(
                fetcher.open(server.url("/x").toString()) { it.readBytes() }.exceptionOrNull() is NetworkException.Http,
            )
        }
}
