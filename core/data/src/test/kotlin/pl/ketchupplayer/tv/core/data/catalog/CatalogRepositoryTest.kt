package pl.ketchupplayer.tv.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.core.security.InMemorySecretStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRepositoryTest {
    private lateinit var db: KetchupDatabase
    private var response: Result<String> = Result.success("")
    private val fetcher = TextFetcher { response }
    private var now = 1_000L
    private val secrets = InMemorySecretStore()

    private val list =
        "#EXTM3U url-tvg=\"https://epg.example.invalid/x.xml\"\n" +
            "#EXTINF:-1 group-title=\"Info\",INFO24\nhttp://h.example.invalid/1\n" +
            "#EXTINF:-1 group-title=\"Sport\",SPORT\nhttp://h.example.invalid/2\n"

    @Before
    fun open() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    KetchupDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun close() = db.close()

    private fun repoWith(f: TextFetcher) =
        RoomCatalogRepository(
            db.providers(),
            db.channels(),
            ProviderSecrets(
                secrets,
            ),
            ProviderFactory(ProviderSecrets(secrets), f),
            Clock {
                now
            },
        )

    private fun repo() = repoWith(fetcher)

    @Test
    fun `dodanie listy M3U zapisuje dostawce i kanaly`() =
        runTest {
            response = Result.success(list)
            val r = repo().addM3u("Moja lista", "http://l.example.invalid/x.m3u")
            assertTrue(r is AddProviderResult.Added && r.channelCount == 2)
            val repo = repo()
            assertEquals(listOf("INFO24", "SPORT"), repo.liveChannels().first().map { it.name })
            assertEquals(listOf("Info", "Sport"), repo.liveGroups().first())
            val info = repo.providers.first().single()
            assertEquals("l.example.invalid", info.urlHost)
            assertEquals(2, info.channelCount)
            assertEquals(1_000L, info.lastRefreshMs)
        }

    @Test
    fun `walidacja nazwy i adresu`() =
        runTest {
            val repo = repo()
            assertEquals(
                AddProviderResult.Invalid(AddProviderError.INVALID_NAME),
                repo.addM3u(" ", "http://a.example.invalid/x"),
            )
            assertEquals(
                AddProviderResult.Invalid(AddProviderError.INVALID_URL),
                repo.addM3u("A", "ftp://a.example.invalid/x"),
            )
            assertTrue(repo.providers.first().isEmpty())
        }

    @Test
    fun `blad pierwszego pobrania zapisuje dostawce z komunikatem`() =
        runTest {
            response = Result.failure(NetworkException.Http(500))
            val repo = repo()
            val r = repo.addM3u("A", "http://a.example.invalid/x")
            assertTrue(r is AddProviderResult.SavedWithError)
            assertNotNull(
                repo.providers
                    .first()
                    .single()
                    .lastError,
            )
        }

    @Test
    fun `nieudane odswiezenie zostawia stare kanaly`() =
        runTest {
            response = Result.success(list)
            val repo = repo()
            val id = (repo.addM3u("A", "http://a.example.invalid/x") as AddProviderResult.Added).id
            response = Result.failure(NetworkException.Http(503))
            now = 2_000L
            assertTrue(repo.refresh(id).isFailure)
            assertEquals(2, repo.liveChannels().first().size)
            assertEquals(
                2_000L,
                repo.providers
                    .first()
                    .single()
                    .lastRefreshMs,
            )
        }

    @Test
    fun `usuniecie dostawcy kasuje kanaly`() =
        runTest {
            response = Result.success(list)
            val repo = repo()
            val id = (repo.addM3u("A", "http://a.example.invalid/x") as AddProviderResult.Added).id
            repo.remove(id)
            assertTrue(repo.liveChannels().first().isEmpty())
            assertTrue(repo.providers.first().isEmpty())
        }

    private val xtreamFetcher =
        TextFetcher { url ->
            when {
                "action=get_live_categories" in url ->
                    Result.success(
                        """[{"category_id":"1","category_name":"News"}]""",
                    )
                "action=get_live_streams" in url ->
                    Result.success("""[{"num":1,"name":"JEDEN","stream_id":10,"category_id":"1"}]""")
                "username=jan" in url -> Result.success("""{"user_info":{"auth":1,"status":"Active"}}""")
                else -> Result.failure(NetworkException.Http(404))
            }
        }

    private fun xtreamRepo(f: TextFetcher = xtreamFetcher) = repoWith(f)

    @Test
    fun `dodanie Xtream zapisuje kanaly, a resolveStream buduje adres z sekretow`() =
        runTest {
            val repo = xtreamRepo()
            val r = repo.addXtream("Xtream", "http://x.example.invalid:8080/c/", "jan", "SEKRET")
            assertTrue(r is AddProviderResult.Added && r.channelCount == 1)
            val ch = repo.liveChannels().first().single()
            assertEquals("xtream:live:10.ts", ch.streamUrl)
            assertEquals(
                "http://x.example.invalid:8080/live/jan/SEKRET/10.ts",
                repo.resolveStream(ch.id).getOrThrow().url,
            )
            assertEquals(
                "http://x.example.invalid:8080/xmltv.php?username=jan&password=SEKRET",
                repo.epgUrl((r as AddProviderResult.Added).id),
            )
        }

    @Test
    fun `Xtream - walidacja i bledne dane logowania`() =
        runTest {
            val repo = xtreamRepo()
            assertEquals(
                AddProviderResult.Invalid(AddProviderError.INVALID_CREDENTIALS),
                repo.addXtream("X", "http://x.example.invalid", "jan", " "),
            )
            assertEquals(
                AddProviderResult.Invalid(AddProviderError.INVALID_URL),
                repo.addXtream("X", "ftp://x.example.invalid", "jan", "p"),
            )
            val bad = TextFetcher { Result.success("""{"user_info":{"auth":0}}""") }
            val r = xtreamRepo(bad).addXtream("X", "http://x.example.invalid", "jan", "zle")
            assertTrue(r is AddProviderResult.SavedWithError && "login" in r.message)
        }

    @Test
    fun `usuniecie dostawcy kasuje sekrety`() =
        runTest {
            val repo = xtreamRepo()
            val id = (repo.addXtream("X", "http://x.example.invalid", "jan", "SEKRET") as AddProviderResult.Added).id
            repo.remove(id)
            assertEquals(null, secrets.get("provider.${id.value}.pass"))
            assertEquals(null, repo.epgUrl(id))
        }

    /** DoD Fazy D: w pliku bazy nie ma hasła ani loginu (dane logowania tylko w SecretStore). */
    @Test
    fun `plik bazy nie zawiera hasla ani adresu z danymi logowania`() =
        runTest {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val fileDb =
                Room
                    .databaseBuilder(
                        ctx,
                        KetchupDatabase::class.java,
                        "secrets-test.db",
                    ).allowMainThreadQueries()
                    .build()
            val repo =
                RoomCatalogRepository(
                    fileDb.providers(),
                    fileDb.channels(),
                    ProviderSecrets(
                        secrets,
                    ),
                    ProviderFactory(ProviderSecrets(secrets), xtreamFetcher),
                    Clock {
                        now
                    },
                )
            repo.addXtream("X", "http://x.example.invalid:8080", "login-jan-777", "HASLO-SEKRET-777")
            response = Result.success(list)
            repo.addM3u("M", "http://m.example.invalid/get.php?username=u&password=M3U-SEKRET-777")
            fileDb.close()
            val dir = ctx.getDatabasePath("secrets-test.db").parentFile!!
            val bytes = dir.listFiles { f -> f.name.startsWith("secrets-test.db") }!!.map { it.readBytes() }
            assertTrue(bytes.isNotEmpty())
            val text = bytes.joinToString("\n") { String(it, Charsets.ISO_8859_1) }
            assertTrue("HASLO-SEKRET-777" !in text)
            assertTrue("login-jan-777" !in text)
            assertTrue("M3U-SEKRET-777" !in text)
            assertTrue("x.example.invalid" in text)
        }
}
