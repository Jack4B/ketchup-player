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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRepositoryTest {
    private lateinit var db: KetchupDatabase
    private var response: Result<String> = Result.success("")
    private val fetcher = TextFetcher { response }
    private var now = 1_000L

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

    private fun repo() = RoomCatalogRepository(db.providers(), db.channels(), fetcher, Clock { now })

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
}
