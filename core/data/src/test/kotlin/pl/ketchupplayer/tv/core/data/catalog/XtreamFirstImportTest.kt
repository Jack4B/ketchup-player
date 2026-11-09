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
import pl.ketchupplayer.tv.core.data.network.RoomProviderHeadersRepository
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.network.NetworkClients
import pl.ketchupplayer.tv.core.network.OkHttpTextFetcher
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import pl.ketchupplayer.tv.core.security.InMemorySecretStore

/**
 * Panel za Cloudflare (jak na przechwyceniu IPTV Extreme): `get.php` zawsze 520, a `player_api.php` odpowiada 200 tylko
 * klientowi z User-Agent `okhttp/4.10.0`. Prawdziwy OkHttp, MockWebServer i Room. Dane wyłącznie fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XtreamFirstImportTest {
    private lateinit var db: KetchupDatabase
    private lateinit var server: MockWebServer
    private val secrets = InMemorySecretStore()
    private val requests = java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()
    private var getPhpWorks = false
    private var playerApiStatus = 200

    private val auth = """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000"},"server_info":{}}"""
    private val cats = """[{"category_id":"1","category_name":"PL | News"}]"""
    private val live =
        """[{"num":1,"name":"JEDEN HD","stream_id":101,"stream_icon":"http://i.example.invalid/1.png",""" +
            """"epg_channel_id":"Jeden.pl","category_id":"1"},""" +
            """{"num":2,"name":"INFO24","stream_id":102,"category_id":"1"}]"""
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
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val ua = request.getHeader("User-Agent")
                    val path = request.path.orEmpty()
                    return when {
                        ua != NetworkClients.DEFAULT_USER_AGENT -> MockResponse().setResponseCode(CLOUDFLARE_520)
                        path.startsWith("/get.php") ->
                            if (getPhpWorks) {
                                MockResponse().setBody(playlist)
                            } else {
                                MockResponse().setResponseCode(CLOUDFLARE_520)
                            }
                        playerApiStatus != 200 -> MockResponse().setResponseCode(playerApiStatus)
                        "action=get_live_categories" in path -> MockResponse().setBody(cats)
                        "action=get_live_streams" in path -> MockResponse().setBody(live)
                        "action=" in path -> MockResponse().setBody("[]")
                        else -> MockResponse().setBody(auth)
                    }
                }
            }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun repo(withHeaders: Boolean = false): RoomCatalogRepository {
        val provSecrets = ProviderSecrets(secrets)
        val headers = if (withHeaders) RoomProviderHeadersRepository(db.providers(), provSecrets) else null
        return RoomCatalogRepository(
            db.providers(),
            db.channels(),
            db.channelFlags(),
            db.vod(),
            provSecrets,
            ProviderFactory(provSecrets, OkHttpTextFetcher(NetworkClients.stream()), headers),
            Clock { 1_000L },
        )
    }

    private val getPhp get() =
        server.url("/get.php?username=user123&password=pass456&type=m3u_plus&output=ts").toString()

    @Test
    fun `link get php z panelu odrzucajacego get php importuje sie przez player_api`() =
        runTest {
            val repo = repo()
            val added = repo.addM3u("Panel", getPhp)
            assertTrue(added.toString(), added is AddProviderResult.Added)
            val channels = repo.liveChannels().first()
            assertEquals(listOf("JEDEN HD", "INFO24"), channels.map { it.name })
            assertEquals("PL | News", channels.first().group)
            assertEquals("Jeden.pl", channels.first().epgId)
            assertEquals("http://i.example.invalid/1.png", channels.first().logo)
            assertTrue("get.php nie powinien być pobierany", requests.none { it.path.orEmpty().startsWith("/get.php") })
            val actions = requests.map { Regex("action=([a-z_]+)").find(it.path.orEmpty())?.groupValues?.get(1) }
            // Cały import w kolejności IPTV Extreme: konto, kanały, potem filmy i seriale.
            assertEquals(
                listOf(
                    null,
                    "get_live_categories",
                    "get_live_streams",
                    "get_vod_categories",
                    "get_vod_streams",
                    "get_series_categories",
                    "get_series",
                ),
                actions,
            )
        }

    @Test
    fun `kazde zapytanie importu ma dokladnie cztery naglowki w kolejnosci IPTV Extreme`() =
        runTest {
            repo().addM3u("Panel", getPhp)
            assertTrue(requests.isNotEmpty())
            requests.forEach { r ->
                val names = List(r.headers.size) { r.headers.name(it) }
                assertEquals(r.path, listOf("User-Agent", "Host", "Connection", "Accept-Encoding"), names)
                assertEquals("okhttp/4.10.0", r.getHeader("User-Agent"))
                assertEquals("GET", r.method)
            }
        }

    @Test
    fun `gdy player_api nie dziala, lista idzie z get php jako tryb zapasowy`() =
        runTest {
            getPhpWorks = true
            playerApiStatus = 404
            val repo = repo()
            val added = repo.addM3u("Panel", getPhp)
            assertTrue(added.toString(), added is AddProviderResult.Added)
            assertEquals(listOf("TVN 24"), repo.liveChannels().first().map { it.name })
            val paths = requests.map { it.path.orEmpty().substringBefore('?') }
            assertEquals(listOf("/player_api.php", "/get.php"), paths)
        }

    @Test
    fun `adres odtwarzania z konta Xtream jest zbudowany z danych w SecretStore`() =
        runTest {
            val repo = repo()
            repo.addM3u("Panel", getPhp)
            val channel = repo.liveChannels().first().first()
            val spec = repo.resolveStream(channel.id).getOrThrow()
            assertEquals(server.url("/live/user123/pass456/101.ts").toString(), spec.url)
            assertEquals(null, spec.userAgent) // domyślny: klient odtwarzacza użyje VLC
        }

    /** Wybór UA dla listy: OTT Navigator dostaje 520, po powrocie do domyślnego import przechodzi. */
    @Test
    fun `User-Agent listy z Room obowiazuje przy imporcie`() =
        runTest {
            val repo = repo(withHeaders = true)
            val added = repo.addM3u("Panel", getPhp, UserAgentProfiles.OTT.id)
            assertTrue(added.toString(), added is AddProviderResult.SavedWithError)
            assertEquals("ott", db.providers().get((added as AddProviderResult.SavedWithError).id.value)?.userAgent)
            assertTrue(requests.all { it.getHeader("User-Agent") == NetworkClients.OTT_USER_AGENT })

            val id = added.id
            RoomProviderHeadersRepository(
                db.providers(),
                ProviderSecrets(secrets),
            ).set(id.value, UserAgentProfiles.AUTO)
            requests.clear()
            assertEquals(2, repo.refresh(id).getOrThrow())
            assertFalse(requests.any { it.getHeader("User-Agent") != "okhttp/4.10.0" })
        }

    private companion object {
        const val CLOUDFLARE_520 = 520
    }
}
