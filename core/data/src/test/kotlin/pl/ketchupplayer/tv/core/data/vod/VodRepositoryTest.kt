package pl.ketchupplayer.tv.core.data.vod

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import pl.ketchupplayer.tv.core.data.catalog.AddProviderResult
import pl.ketchupplayer.tv.core.data.catalog.ImportPhase
import pl.ketchupplayer.tv.core.data.catalog.ImportStatus
import pl.ketchupplayer.tv.core.data.catalog.ProviderFactory
import pl.ketchupplayer.tv.core.data.catalog.ProviderSecrets
import pl.ketchupplayer.tv.core.data.catalog.RoomCatalogRepository
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.model.VodKind
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.core.security.InMemorySecretStore

/** Scenariusz: konto Xtream z filmami i serialami -> katalog w bazie -> odcinki, Moja lista, wznawianie. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VodRepositoryTest {
    private lateinit var db: KetchupDatabase
    private var now = 1_000L

    private var api =
        mapOf(
            "&password=tajne" to """{"user_info":{"auth":1,"status":"Active"}}""",
            "action=get_live_categories" to """[{"category_id":"1","category_name":"Info"}]""",
            "action=get_live_streams" to
                """[{"num":1,"name":"INFO24","stream_id":"1","category_id":"1","epg_channel_id":null}]""",
            "action=get_vod_categories" to
                """[{"category_id":"10","category_name":"Dramat"},{"category_id":"11","category_name":"Komedia"}]""",
            "action=get_vod_streams" to
                """[{"stream_id":501,"name":"PL - Pitbull (2005)","stream_icon":"http://i.example.invalid/p.jpg",""" +
                """"rating":"7.4","category_id":"10","container_extension":"mkv","added":"1700000000"},""" +
                """{"stream_id":502,"name":"Seksmisja","rating":"8.1","category_id":"11",""" +
                """"container_extension":"mp4"}]""",
            "action=get_series_categories" to """[{"category_id":"20","category_name":"Kryminał"}]""",
            "action=get_series_info" to
                """{"episodes":{"1":[{"id":"9001","episode_num":1,"title":"Pilot","season":1,""" +
                """"container_extension":"mp4","info":{"duration_secs":2700}},""" +
                """{"id":"9002","episode_num":2,"title":"Drugi","season":1,"container_extension":"mp4"}],""" +
                """"2":[{"id":"9003","episode_num":1,"title":"Powrót","season":2,"container_extension":"mp4"}]}}""",
            "action=get_series" to
                """[{"series_id":77,"name":"Rodzina Kowalskich","cover":"http://i.example.invalid/s.jpg",""" +
                """"last_modified":"1700000000",""" +
                """"genre":"Komedia","plot":"Opis serialu","rating":"6.5","releaseDate":"2011-03-01",""" +
                """"category_id":"20"}]""",
        )

    private val fetcher =
        TextFetcher { url ->
            // Dłuższe klucze najpierw: `get_series_info` zawiera `get_series`.
            val body =
                api.entries
                    .filter { it.key != "&password=tajne" || !url.contains("action=") }
                    .sortedByDescending { it.key.length }
                    .firstOrNull { url.contains(it.key) }
                    ?.value
            if (body != null) Result.success(body) else Result.failure(IllegalStateException("404 $url"))
        }

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

    private lateinit var catalog: RoomCatalogRepository
    private var providerId = ""

    private suspend fun vodRepo(): VodRepository {
        val secrets = ProviderSecrets(InMemorySecretStore())
        val factory = ProviderFactory(secrets, fetcher)
        catalog =
            RoomCatalogRepository(
                db.providers(),
                db.channels(),
                db.channelFlags(),
                db.vod(),
                secrets,
                factory,
                Clock { now },
            )
        val added = catalog.addXtream("Konto", "http://x.example.invalid:8080", "jan", "tajne")
        check(added is AddProviderResult.Added)
        providerId = added.id.value
        return RoomVodRepository(db.vod(), db.providers(), factory, Clock { now })
    }

    @Test
    fun `katalog filmow i seriali trafia do bazy z oczyszczonymi tytulami`() =
        runTest {
            val repo = vodRepo()
            val movies = repo.entries(VodKind.MOVIE).first()
            assertEquals(listOf("Pitbull", "Seksmisja"), movies.map { it.item.title })
            val pitbull = movies.first().item
            assertEquals(2005, pitbull.year)
            assertEquals("Dramat", pitbull.category)
            assertEquals(7.4, pitbull.rating!!, 0.001)
            val series =
                repo
                    .entries(VodKind.SERIES)
                    .first()
                    .single()
                    .item
            assertEquals("Rodzina Kowalskich", series.title)
            assertEquals(2011, series.year)
            assertEquals("Kryminał", series.category)
        }

    @Test
    fun `odcinki serialu laduja sie na zadanie, pogrupowane w sezony`() =
        runTest {
            val repo = vodRepo()
            val series =
                repo
                    .entries(VodKind.SERIES)
                    .first()
                    .single()
                    .item
            assertTrue(repo.episodes(series.id).first().isEmpty())
            assertEquals(3, repo.refreshEpisodes(series.id).getOrThrow())
            val eps = repo.episodes(series.id).first().map { it.episode }
            assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), eps.map { it.season to it.number })
            assertEquals(45, eps.first().durationMin)
        }

    @Test
    fun `adres filmu i odcinka powstaje z danych logowania dopiero przy odtwarzaniu`() =
        runTest {
            val repo = vodRepo()
            val pitbull =
                repo
                    .entries(VodKind.MOVIE)
                    .first()
                    .first()
                    .item
            assertEquals("xtream:movie:501.mkv", pitbull.ref)
            assertEquals(
                "http://x.example.invalid:8080/movie/jan/tajne/501.mkv",
                repo.resolve(pitbull.id).getOrThrow().url,
            )
            val series =
                repo
                    .entries(VodKind.SERIES)
                    .first()
                    .single()
                    .item
            repo.refreshEpisodes(series.id)
            val ep =
                repo
                    .episodes(series.id)
                    .first()
                    .first()
                    .episode
            assertEquals(
                "http://x.example.invalid:8080/series/jan/tajne/9001.mp4",
                repo.resolve(ep.id).getOrThrow().url,
            )
        }

    @Test
    fun `moja lista i wznawianie przezywaja odswiezenie katalogu`() =
        runTest {
            val repo = vodRepo()
            val pitbull =
                repo
                    .entries(VodKind.MOVIE)
                    .first()
                    .first()
                    .item
            repo.setMyList(pitbull.id, true)
            now = 5_000
            repo.saveProgress(pitbull.id, 30 * 60_000L, 90 * 60_000L)
            assertEquals(listOf(pitbull.id), repo.myList().first().map { it.item.id })
            val resume = repo.continueWatching(5).first().single()
            assertEquals(pitbull.id, resume.item.id)
            assertEquals(0.33f, resume.progress, 0.01f)
            // Ukończony film znika z „Kontynuuj”.
            repo.saveProgress(pitbull.id, 85 * 60_000L, 90 * 60_000L)
            assertTrue(repo.continueWatching(5).first().isEmpty())
            assertFalse(repo.myList().first().isEmpty())
        }

    @Test
    fun `podobne - ta sama kategoria przed pozostalymi`() =
        runTest {
            val repo = vodRepo()
            val pitbull =
                repo
                    .entries(VodKind.MOVIE)
                    .first()
                    .first()
                    .item
            assertEquals(listOf("Seksmisja"), repo.similar(pitbull.id, 5).map { it.title })
        }

    /** Regresja (zgłoszenie z boxa): uszkodzony lub zbyt duży katalog filmów nie może wywrócić dodania konta. */
    @Test
    fun `uszkodzony katalog filmow nie psuje dodania konta, kanaly zostaja`() =
        runTest {
            api = api + ("action=get_vod_streams" to "<html>Błąd serwera</html>")
            val secrets = ProviderSecrets(InMemorySecretStore())
            val factory = ProviderFactory(secrets, fetcher)
            val catalog =
                RoomCatalogRepository(
                    db.providers(),
                    db.channels(),
                    db.channelFlags(),
                    db.vod(),
                    secrets,
                    factory,
                    Clock { now },
                )
            val added = catalog.addXtream("Konto", "http://x.example.invalid:8080", "jan", "tajne")
            assertTrue("dodanie konta: $added", added is AddProviderResult.Added)
            assertEquals(1, catalog.liveChannels().first().size)
            assertTrue(db.vod().byKind("MOVIE").isEmpty())
            // Użytkownik widzi powód braku filmów przy dostawcy.
            assertTrue(
                catalog.providers
                    .first()
                    .single()
                    .lastError!!
                    .contains("Filmy i seriale pominięte"),
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `import pokazuje etapy i konczy sie czystym stanem`() =
        runTest {
            val secrets = ProviderSecrets(InMemorySecretStore())
            val catalog =
                RoomCatalogRepository(
                    db.providers(),
                    db.channels(),
                    db.channelFlags(),
                    db.vod(),
                    secrets,
                    ProviderFactory(secrets, fetcher),
                    Clock { now },
                )
            val seen = mutableListOf<ImportStatus?>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { catalog.importStatus.toList(seen) }
            catalog.addXtream("Konto", "http://x.example.invalid:8080", "jan", "tajne")
            job.cancel()
            val phases = seen.filterNotNull().map { it.phase }.distinct()
            assertEquals(listOf(ImportPhase.DOWNLOADING, ImportPhase.SAVING_CHANNELS, ImportPhase.SAVING_VOD), phases)
            assertEquals(3, seen.filterNotNull().first { it.phase == ImportPhase.SAVING_VOD }.count)
            assertEquals(null, catalog.importStatus.value)
        }

    /** Zielona kropka: serial z nowszym `last_modified` po odświeżeniu listy; otwarcie serialu ją zdejmuje. */
    @Test
    fun `nowy odcinek po odswiezeniu zapala kropke a otwarcie serialu ja zdejmuje`() =
        runTest {
            val repo = vodRepo()
            assertFalse(repo.anyNewEpisodes().first()) // pierwszy import to stan wyjściowy, nic nie jest „nowe”
            api = api + ("action=get_series" to api.getValue("action=get_series").replace("1700000000", "1700500000"))
            catalog
                .refresh(
                    pl.ketchupplayer.tv.core.model
                        .ProviderId(providerId),
                ).getOrThrow()
            assertTrue(repo.anyNewEpisodes().first())
            val series = repo.entries(VodKind.SERIES).first().single()
            assertTrue(series.hasNewEpisodes)
            repo.markSeen(series.item.id)
            assertFalse(repo.anyNewEpisodes().first())
            assertFalse(
                repo
                    .entries(VodKind.SERIES)
                    .first()
                    .single()
                    .hasNewEpisodes,
            )
        }
}
