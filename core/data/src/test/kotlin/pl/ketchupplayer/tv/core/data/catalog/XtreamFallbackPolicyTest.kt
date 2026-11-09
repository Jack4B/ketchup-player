package pl.ketchupplayer.tv.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.network.NetworkClients
import pl.ketchupplayer.tv.core.network.OkHttpTextFetcher
import pl.ketchupplayer.tv.core.security.InMemorySecretStore
import pl.ketchupplayer.tv.provider.api.ProviderError

/**
 * Kiedy po porażce `player_api.php` wolno pobrać `get.php` (tryb zapasowy), a kiedy nie: odmowa dostępu, limity i
 * `Retry-After` nie uruchamiają kolejnych żądań. Prawdziwy OkHttp, MockWebServer i Room; dane fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XtreamFallbackPolicyTest {
    private lateinit var db: KetchupDatabase
    private lateinit var server: MockWebServer
    private val secrets = InMemorySecretStore()
    private val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var now = 1_000_000L

    /** Odpowiedź na `player_api.php` i na `get.php`; domyślnie API działa, a `get.php` oddaje listę M3U. */
    private var api: () -> MockResponse = { MockResponse().setBody(AUTH_OK) }
    private var getPhp: () -> MockResponse = { MockResponse().setBody(PLAYLIST) }

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    KetchupDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        server = MockWebServer().also { it.start() }
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    requests +=
                        path.substringBefore('?') + (Regex("action=[a-z_]+").find(path)?.value?.let { "?$it" } ?: "")
                    return when {
                        path.startsWith("/get.php") -> getPhp()
                        path.contains("action=get_live_categories") -> MockResponse().setBody(CATS)
                        path.contains("action=get_live_streams") -> MockResponse().setBody(LIVE)
                        path.contains("action=") -> MockResponse().setBody("[]")
                        else -> api()
                    }
                }
            }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun repo(): RoomCatalogRepository {
        val provSecrets = ProviderSecrets(secrets)
        return RoomCatalogRepository(
            db.providers(),
            db.channels(),
            db.channelFlags(),
            db.vod(),
            provSecrets,
            ProviderFactory(provSecrets, OkHttpTextFetcher(NetworkClients.stream())),
            Clock { now },
        )
    }

    private val link get() = server.url("/get.php?username=user123&password=pass456&type=m3u_plus").toString()

    private fun usedGetPhp() = requests.any { it.startsWith("/get.php") }

    private suspend fun addAndGetError(): String {
        val result = repo().addM3u("Panel", link)
        return (result as? AddProviderResult.SavedWithError)?.message ?: "OK:" + result
    }

    @Test
    fun `401 i 403 nie uruchamiaja get php`() =
        runTest {
            for (code in listOf(401, 403)) {
                requests.clear()
                api = { MockResponse().setResponseCode(code) }
                val message = addAndGetError()
                assertTrue("HTTP $code: $message", message.startsWith("HTTP") || message.isNotEmpty())
                assertFalse("HTTP $code nie może uruchamiać get.php", usedGetPhp())
                assertEquals("jedno żądanie, bez kaskady", 1, requests.size)
            }
        }

    @Test
    fun `nieaktywne konto i bledne dane nie uruchamiaja get php`() =
        runTest {
            for (body in listOf(
                """{"user_info":{"auth":1,"status":"Expired"}}""",
                """{"user_info":{"auth":0}}""",
            )) {
                requests.clear()
                api = { MockResponse().setBody(body) }
                addAndGetError()
                assertFalse(body, usedGetPhp())
                assertEquals(1, requests.size)
            }
        }

    @Test
    fun `429 z Retry-After nie uruchamia get php i wstrzymuje kolejne proby do konca okresu`() =
        runTest {
            api = { MockResponse().setResponseCode(429).setHeader("Retry-After", "30") }
            val repo = repo()
            val added = repo.addM3u("Panel", link) as AddProviderResult.SavedWithError
            assertTrue(added.message, "ponów po: 30 s" in added.message)
            assertFalse(usedGetPhp())
            assertEquals(1, requests.size)

            now += 10_000
            val early = repo.refresh(added.id)
            assertTrue(early.isFailure)
            assertTrue(
                early.exceptionOrNull()?.message.orEmpty(),
                "ponów po: 20 s" in early.exceptionOrNull()?.message.orEmpty(),
            )
            assertEquals("w oknie Retry-After nie ma żadnych żądań", 1, requests.size)

            now += 21_000
            api = { MockResponse().setBody(AUTH_OK) }
            assertEquals(1, repo.refresh(added.id).getOrThrow())
            assertTrue(requests.size > 1)
            assertFalse(usedGetPhp())
        }

    @Test
    fun `429 bez Retry-After tez nie uruchamia get php i ma domyslne odczekanie`() =
        runTest {
            api = { MockResponse().setResponseCode(429) }
            val repo = repo()
            val added = repo.addM3u("Panel", link) as AddProviderResult.SavedWithError
            assertFalse(usedGetPhp())
            now += 5_000
            assertTrue(repo.refresh(added.id).isFailure)
            assertEquals(1, requests.size)
        }

    @Test
    fun `520 z Retry-After nie uruchamia get php`() =
        runTest {
            api = { MockResponse().setResponseCode(520).setHeader("Retry-After", "120") }
            val added = repo().addM3u("Panel", link) as AddProviderResult.SavedWithError
            assertTrue(added.message, "ponów po: 120 s" in added.message)
            assertFalse(usedGetPhp())
            assertEquals(1, requests.size)
        }

    @Test
    fun `404 oznacza brak API i dopuszcza get php`() =
        runTest {
            api = { MockResponse().setResponseCode(404) }
            val repo = repo()
            val added = repo.addM3u("Panel", link)
            assertTrue(added.toString(), added is AddProviderResult.Added)
            assertEquals(listOf("TVN 24"), repo.liveChannels().first().map { it.name })
            assertEquals(listOf("/player_api.php", "/get.php"), requests)
        }

    @Test
    fun `200 ze strona HTML zamiast API dopuszcza get php`() =
        runTest {
            api = { MockResponse().setBody("<html>Welcome</html>") }
            val added = repo().addM3u("Panel", link)
            assertTrue(added.toString(), added is AddProviderResult.Added)
            assertEquals(listOf("/player_api.php", "/get.php"), requests)
        }

    @Test
    fun `520 bez Retry-After to blad serwera i dopuszcza jedno get php`() =
        runTest {
            api = { MockResponse().setResponseCode(520) }
            val added = repo().addM3u("Panel", link)
            assertTrue(added.toString(), added is AddProviderResult.Added)
            assertEquals(listOf("/player_api.php", "/get.php"), requests)
        }

    @Test
    fun `brak polaczenia z serwerem nie uruchamia get php`() =
        runTest {
            val repo = repo()
            val url = link
            server.shutdown()
            val added = repo.addM3u("Panel", url)
            assertTrue(added.toString(), added is AddProviderResult.SavedWithError)
            assertFalse(usedGetPhp())
        }

    @Test
    fun `reguly fallbacku jako funkcja`() {
        assertTrue(XtreamFirstProvider.allowsM3uFallback(ProviderError.NotSupported("x")))
        assertTrue(XtreamFirstProvider.allowsM3uFallback(ProviderError.Network("x", 502)))
        assertFalse(XtreamFirstProvider.allowsM3uFallback(ProviderError.Network("x", null)))
        assertFalse(XtreamFirstProvider.allowsM3uFallback(ProviderError.Unauthorized("x")))
        assertFalse(XtreamFirstProvider.allowsM3uFallback(ProviderError.RateLimited("x", 5)))
        assertFalse(XtreamFirstProvider.allowsM3uFallback(ProviderError.BadResponse("x")))
        assertFalse(XtreamFirstProvider.allowsM3uFallback(null))
    }

    private companion object {
        const val PLAYLIST =
            "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",TVN 24\nhttp://h.example.invalid/live/user123/pass456/1.ts\n"
        const val CATS = """[{"category_id":"1","category_name":"PL | News"}]"""
        const val LIVE = """[{"num":1,"name":"JEDEN HD","stream_id":101,"category_id":"1"}]"""

        const val AUTH_OK =
            """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000"},"server_info":{}}"""
    }
}
