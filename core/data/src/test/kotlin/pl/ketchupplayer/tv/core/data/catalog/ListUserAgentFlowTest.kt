package pl.ketchupplayer.tv.core.data.catalog

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.epg.AddSourceResult
import pl.ketchupplayer.tv.core.data.epg.EpgSourcesRepository
import pl.ketchupplayer.tv.core.data.epg.EpgUrlProvider
import pl.ketchupplayer.tv.core.data.epg.RoomEpgRepository
import pl.ketchupplayer.tv.core.data.network.RoomProviderHeadersRepository
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.network.NetworkClients
import pl.ketchupplayer.tv.core.network.OkHttpStreamFetcher
import pl.ketchupplayer.tv.core.network.OkHttpTextFetcher
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import pl.ketchupplayer.tv.core.security.InMemorySecretStore
import java.io.File

/**
 * User-Agent wybrany dla listy obowiązuje w całym imporcie: API, EPG, ponowieniu i przekierowaniu. Dwie listy z różnymi
 * User-Agent nie nadpisują sobie konfiguracji (wspólny klient nie jest zmieniany). Dane wyłącznie fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListUserAgentFlowTest {
    private lateinit var db: KetchupDatabase
    private lateinit var server: MockWebServer
    private val secrets = InMemorySecretStore()
    private val seen = java.util.concurrent.CopyOnWriteArrayList<Seen>()
    private var failFirstXmltvOfA = true

    /** Zapytanie widziane przez serwer: użytkownik, ścieżka (z akcją) i User-Agent. */
    private data class Seen(
        val user: String,
        val path: String,
        val agent: String,
    )

    private class NoSources : EpgSourcesRepository {
        override val sources = MutableStateFlow<List<EpgSource>>(emptyList())

        override suspend fun setEnabled(
            id: String,
            enabled: Boolean,
        ) = Unit

        override suspend fun addCustom(
            name: String,
            url: String,
            providerId: ProviderId?,
        ) = AddSourceResult.ADDED

        override suspend fun removeCustom(id: String) = Unit
    }

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
                    val user = Regex("username=([A-Za-z0-9]+)").find(path)?.groupValues?.get(1) ?: "-"
                    seen +=
                        Seen(user, path.substringBefore('?') + action(path), request.getHeader("User-Agent").orEmpty())
                    return answer(path, user)
                }
            }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun action(path: String) = Regex("action=[a-z_]+").find(path)?.value?.let { "?$it" } ?: ""

    private fun takeFirstFailure() = failFirstXmltvOfA.also { failFirstXmltvOfA = false }

    private fun answer(
        path: String,
        user: String,
    ): MockResponse =
        when {
            path.startsWith(
                "/xmltv.php",
            ) &&
                user == "userA" &&
                takeFirstFailure() -> MockResponse().setResponseCode(500)
            path.startsWith("/xmltv.php") && user == "userB" ->
                MockResponse().setResponseCode(302).setHeader("Location", "/xmltv-b.php?username=userB&password=p")
            path.startsWith("/xmltv") -> MockResponse().setBody("<tv></tv>")
            path.contains("action=get_live_categories") -> MockResponse().setBody(CATS)
            path.contains("action=get_live_streams") -> MockResponse().setBody(LIVE)
            path.contains("action=") -> MockResponse().setBody("[]")
            else -> MockResponse().setBody(AUTH)
        }

    private val provSecrets = ProviderSecrets(secrets)
    private val headers by lazy { RoomProviderHeadersRepository(db.providers(), provSecrets) }

    private fun catalog() =
        RoomCatalogRepository(
            db.providers(),
            db.channels(),
            db.channelFlags(),
            db.vod(),
            provSecrets,
            ProviderFactory(provSecrets, OkHttpTextFetcher(NetworkClients.stream()), headers),
            Clock { NOW },
        )

    private fun epg() =
        RoomEpgRepository(
            db.epg(),
            db.channels(),
            EpgUrlProvider(NoSources(), db.providers(), provSecrets, headers),
            OkHttpStreamFetcher(NetworkClients.stream()),
            Clock { NOW },
            PreferenceDataStoreFactory.create { File.createTempFile("epg", ".preferences_pb").also { it.delete() } },
        )

    private fun link(user: String) = server.url("/get.php?username=$user&password=p&type=m3u_plus").toString()

    private fun uas(
        user: String,
        prefix: String,
    ) = seen.filter { it.user == user && it.path.startsWith(prefix) }.map { it.agent }

    @Test
    fun `dwie listy z roznymi UA, EPG, ponowienie i przekierowanie zachowuja UA swojej listy`() =
        runTest {
            val catalog = catalog()
            val custom = UserAgentProfiles.custom("AgentA/1").id
            assertTrue(catalog.addM3u("A", link("userA"), custom) is AddProviderResult.Added)
            assertTrue(catalog.addM3u("B", link("userB"), UserAgentProfiles.VLC.id) is AddProviderResult.Added)
            assertTrue(catalog.addM3u("C", link("userC")) is AddProviderResult.Added)

            val epg = epg()
            epg.refresh() // A: 500, B: 302 na inny adres tego samego hosta, C: domyślny UA
            epg.refresh() // ponowienie: A teraz działa

            val vlc = "VLC/3.0.20 LibVLC/3.0.20"
            assertEquals("API listy A", setOf("AgentA/1"), uas("userA", "/player_api.php").toSet())
            assertEquals("EPG A: błąd 500 i ponowienie", listOf("AgentA/1", "AgentA/1"), uas("userA", "/xmltv.php"))
            assertEquals("API listy B", setOf(vlc), uas("userB", "/player_api.php").toSet())
            assertEquals("EPG B", setOf(vlc), uas("userB", "/xmltv.php").toSet())
            assertEquals("przekierowanie B zachowało UA", setOf(vlc), uas("userB", "/xmltv-b.php").toSet())
            assertEquals("lista bez wyboru: okhttp także w EPG", setOf("okhttp/4.10.0"), uas("userC", "").toSet())
        }

    @Test
    fun `wspolny klient nie jest zmieniany przez UA listy`() =
        runTest {
            catalog().addM3u("A", link("userA"), UserAgentProfiles.custom("AgentA/1").id)
            val after = java.util.concurrent.CopyOnWriteArrayList<String>()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        after += request.getHeader("User-Agent").orEmpty()
                        return MockResponse().setBody("x")
                    }
                }
            OkHttpTextFetcher(NetworkClients.stream()).fetch(server.url("/inna").toString())
            assertEquals(listOf("okhttp/4.10.0"), after.toList())
        }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val AUTH = """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000"},"server_info":{}}"""
        const val CATS = """[{"category_id":"1","category_name":"PL | News"}]"""
        const val LIVE =
            """[{"num":1,"name":"JEDEN HD","stream_id":101,"category_id":"1","epg_channel_id":"jeden.pl"}]"""
    }
}
