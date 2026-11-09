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
 * Własny User-Agent z kreatora: zapis przy dodaniu listy, użycie od pierwszego pobrania, edycja, ponowne otwarcie
 * (nowe instancje repozytoriów), walidacja znaków nagłówka bez zapisu czegokolwiek. Dane fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomUserAgentWizardTest {
    private lateinit var db: KetchupDatabase
    private lateinit var server: MockWebServer
    private val secrets = InMemorySecretStore()
    private val agents = java.util.concurrent.CopyOnWriteArrayList<String>()

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
                    agents += request.getHeader("User-Agent").orEmpty()
                    return MockResponse().setBody(
                        "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",TVN 24\nhttp://h.invalid/1\n",
                    )
                }
            }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun repo(): RoomCatalogRepository {
        val s = ProviderSecrets(secrets)
        val headers = RoomProviderHeadersRepository(db.providers(), s)
        return RoomCatalogRepository(
            db.providers(),
            db.channels(),
            db.channelFlags(),
            db.vod(),
            s,
            ProviderFactory(s, OkHttpTextFetcher(NetworkClients.stream()), headers),
            Clock { 1_000L },
        )
    }

    private fun headers() = RoomProviderHeadersRepository(db.providers(), ProviderSecrets(secrets))

    private val list get() = server.url("/lista.m3u").toString()

    @Test
    fun `wlasny User-Agent z kreatora obowiazuje od pierwszego pobrania i jest w SecretStore`() =
        runTest {
            val secret = "MojAgent/3 sekret-abc"
            val added = repo().addM3u("Lista", list, UserAgentProfiles.custom(secret).id) as AddProviderResult.Added
            assertEquals(listOf(secret), agents.toList())
            val row = db.providers().get(added.id.value)
            assertEquals("custom", row?.userAgent)
            assertFalse(row.toString().contains("abc"))
            assertEquals(secret, ProviderSecrets(secrets).userAgent(added.id))
        }

    @Test
    fun `edycja i ponowne otwarcie pokazuja zapisana wartosc, zmiana na preset usuwa ja z SecretStore`() =
        runTest {
            val added =
                repo().addM3u(
                    "Lista",
                    list,
                    UserAgentProfiles.custom("Pierwszy/1").id,
                ) as AddProviderResult.Added
            val id = added.id.value
            assertEquals("Pierwszy/1", headers().current(id).value) // „ponowne otwarcie”: nowa instancja repozytorium

            headers().set(id, UserAgentProfiles.custom("Drugi/2"))
            assertEquals("Drugi/2", headers().current(id).value)
            assertEquals("Drugi/2", ProviderSecrets(secrets).userAgent(added.id))

            headers().set(id, UserAgentProfiles.VLC)
            assertEquals("vlc", db.providers().get(id)?.userAgent)
            assertEquals(null, ProviderSecrets(secrets).userAgent(added.id))
            repo().refresh(added.id)
            assertEquals("VLC/3.0.20 LibVLC/3.0.20", agents.last())
        }

    @Test
    fun `niedozwolone znaki naglowka sa odrzucane bez zapisu listy i sekretow`() =
        runTest {
            val repo = repo()
            val bad =
                listOf(
                    "custom:",
                    "custom:   ",
                    "custom:linia1\nlinia2",
                    "custom:zażółć",
                    "custom:" + "a".repeat(513),
                )
            for (id in bad) {
                val result = repo.addM3u("Lista", list, id)
                assertTrue(id.take(20), result is AddProviderResult.Invalid)
                assertEquals(AddProviderError.INVALID_USER_AGENT, (result as AddProviderResult.Invalid).error)
            }
            val xtream = repo.addXtream("X", server.url("/").toString().trimEnd('/'), "user123", "pass456", bad[2])
            assertEquals(AddProviderError.INVALID_USER_AGENT, (xtream as AddProviderResult.Invalid).error)
            assertEquals(emptyList<Any>(), repo.providers.first())
            assertTrue("żadnych żądań dla odrzuconych list", agents.isEmpty())
        }

    @Test
    fun `walidacja jako funkcja`() {
        assertEquals("Wink/1.31.1", UserAgentProfiles.validated("  Wink/1.31.1 "))
        assertEquals(null, UserAgentProfiles.validated(""))
        assertEquals(null, UserAgentProfiles.validated("a\tb"))
        assertEquals(null, UserAgentProfiles.validated("a\u0007b"))
        assertEquals(null, UserAgentProfiles.validated("zażółć"))
        assertEquals(null, UserAgentProfiles.validated("x".repeat(513)))
        assertEquals("x".repeat(512), UserAgentProfiles.validated("x".repeat(512)))
    }
}
