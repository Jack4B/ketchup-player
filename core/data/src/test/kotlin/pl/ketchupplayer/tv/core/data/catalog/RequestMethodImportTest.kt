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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.model.RequestMethod
import pl.ketchupplayer.tv.core.network.NetworkClients
import pl.ketchupplayer.tv.core.network.OkHttpTextFetcher
import pl.ketchupplayer.tv.core.security.InMemorySecretStore

/**
 * Dostawca, który odrzuca GET (504 z Retry-After) i działa tylko przez POST: prawdziwy OkHttp, MockWebServer i Room.
 * Dane wyłącznie fikcyjne (user123, pass456).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RequestMethodImportTest {
    private lateinit var db: KetchupDatabase
    private lateinit var server: MockWebServer
    private val secrets = InMemorySecretStore()
    private val requests = java.util.concurrent.CopyOnWriteArrayList<Triple<String, String, String>>()
    private val playlist = "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",TVN 24\nhttp://h.example.invalid/1\n"

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
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun repo() =
        RoomCatalogRepository(
            db.providers(),
            db.channels(),
            db.channelFlags(),
            db.vod(),
            ProviderSecrets(secrets),
            ProviderFactory(ProviderSecrets(secrets), OkHttpTextFetcher(NetworkClients.stream())),
            Clock { 1_000L },
        )

    /** (metoda, ścieżka z zapytaniem, ciało) każdego żądania; GET -> 504 z Retry-After, POST -> [answer]. */
    private fun serve(answer: (String) -> String) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    requests += Triple(request.method.orEmpty(), request.path.orEmpty(), body)
                    return if (request.method == "POST") {
                        MockResponse().setBody(answer(body))
                    } else {
                        MockResponse().setResponseCode(504).setHeader("Retry-After", "120")
                    }
                }
            }
    }

    private val getPhp get() =
        server
            .url(
                "/get.php?username=user123&password=pass456&type=m3u_plus&output=ts",
            ).toString()

    private fun assertNoSecret(text: String?) =
        assertFalse(text, text != null && (text.contains("user123") || text.contains("pass456")))

    @Test
    fun `GET daje 504 z podpowiedzia POST, po zmianie metody import z POST sie udaje`() =
        runTest {
            serve { playlist }
            val repo = repo()
            val added = repo.addM3u("Panel", getPhp)
            assertTrue(added.toString(), added is AddProviderResult.SavedWithError)
            val message = (added as AddProviderResult.SavedWithError).message
            assertTrue(message, "HTTP 504" in message && POST_HINT in message && "ponów po: 120 s" in message)
            assertNoSecret(message)
            val saved = repo.providers.first().single()
            assertEquals(RequestMethod.GET, saved.requestMethod)
            assertTrue(saved.lastError.orEmpty(), POST_HINT in saved.lastError.orEmpty())
            assertNoSecret(saved.lastError)

            repo.setRequestMethod(added.id, RequestMethod.POST)
            val refreshed = repo.refresh(added.id)
            assertEquals(1, refreshed.getOrThrow())
            val after = repo.providers.first().single()
            assertEquals(RequestMethod.POST, after.requestMethod)
            assertNull(after.lastError)
            assertEquals(1, repo.liveChannels().first().size)
        }

    @Test
    fun `POST wysyla dane w ciele form-urlencoded, a adres nie ma loginu i hasla`() =
        runTest {
            serve { playlist }
            val result = repo().addM3u("Panel POST", getPhp, null, RequestMethod.POST)
            assertTrue(result.toString(), result is AddProviderResult.Added)
            val post = requests.single { it.first == "POST" }
            assertEquals("/get.php", post.second)
            assertEquals("username=user123&password=pass456&type=m3u_plus&output=ts", post.third)
            assertTrue(requests.none { it.first == "GET" })
        }

    @Test
    fun `Xtream przez POST - uwierzytelnienie, kategorie i kanaly bez danych w adresie`() =
        runTest {
            val auth = """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000"}}"""
            val cats = """[{"category_id":"1","category_name":"PL | News"}]"""
            val streams = """[{"num":"1","name":"JEDEN HD","stream_id":"101","category_id":"1"}]"""
            serve { body ->
                when {
                    "action=get_live_categories" in body -> cats
                    "action=get_live_streams" in body -> streams
                    else -> auth
                }
            }
            val baseUrl = server.url("/").toString().trimEnd('/')
            val result = repo().addXtream("Xtream", baseUrl, "user123", "pass456", null, RequestMethod.POST)
            assertTrue(result.toString(), result is AddProviderResult.Added)
            assertTrue(requests.all { it.first == "POST" && it.second == "/player_api.php" })
            assertTrue(requests.all { it.third.startsWith("username=user123&password=pass456") })
            assertTrue(requests.any { "action=get_live_streams" in it.third })
        }

    @Test
    fun `metoda jest zapisana w bazie i odczytywana, domyslnie GET`() =
        runTest {
            serve { playlist }
            val repo = repo()
            val a = (repo.addM3u("A", getPhp) as AddProviderResult.SavedWithError).id
            val b = (repo.addM3u("B", getPhp, null, RequestMethod.POST) as AddProviderResult.Added).id
            val byId = repo.providers.first().associateBy { it.id }
            assertEquals(RequestMethod.GET, byId.getValue(a).requestMethod)
            assertEquals(RequestMethod.POST, byId.getValue(b).requestMethod)
            assertEquals("POST", db.providers().get(b.value)?.requestMethod)
        }
}
