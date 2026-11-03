package pl.ketchupplayer.tv.core.data.metadata

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.database.VodItemEntity
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.core.security.InMemorySecretStore

/** Oceny i opisy: TMDB (PL) + OMDb (IMDb, RT), zapis w bazie, brak trafienia nie jest pytany w kółko. */
@RunWith(RobolectricTestRunner::class)
class MetadataTest {
    private lateinit var db: KetchupDatabase
    private val urls = mutableListOf<String>()
    private var now = 1_000_000L
    private var tmdbSearch = """{"results":[{"id":603,"title":"Matrix"}]}"""
    private var tmdbDetails =
        """{"vote_average":8.2,"overview":"Haker odkrywa prawdę o świecie.","runtime":136,""" +
            """"genres":[{"name":"Akcja"},{"name":"Sci-Fi"}],"poster_path":"/p.jpg","backdrop_path":"/b.jpg",""" +
            """"external_ids":{"imdb_id":"tt0133093"},""" +
            """"credits":{"cast":[{"name":"Keanu Reeves"},{"name":"Carrie-Anne Moss"}]}}"""
    private var omdb =
        """{"Response":"True","imdbRating":"8.7","Plot":"Plot EN","Ratings":""" +
            """[{"Source":"Internet Movie Database","Value":"8.7/10"},{"Source":"Rotten Tomatoes","Value":"83%"}]}"""

    private val fetcher =
        TextFetcher { url ->
            urls += url
            val body =
                when {
                    "/search/" in url -> tmdbSearch
                    "themoviedb" in url || "tmdb.invalid" in url -> tmdbDetails
                    "omdb.invalid" in url -> omdb
                    else -> null
                }
            if (body != null) Result.success(body) else Result.failure(IllegalStateException("404"))
        }

    private val endpoints =
        MetadataEndpoints().apply {
            tmdb = "http://tmdb.invalid/3"
            omdb = "http://omdb.invalid"
        }
    private val store = InMemorySecretStore()
    private val keys = MetadataKeys(store, DefaultApiKeys { ApiKeys() })

    @Before
    fun open() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    KetchupDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        runBlocking {
            db.providers().upsert(ProviderEntity("p", "Lista", "M3U", "http://x.invalid/l.m3u"))
            db.vod().insertAll(
                listOf(
                    VodItemEntity(
                        "m1",
                        "p",
                        "MOVIE",
                        "Matrix",
                        "matrix",
                        "ref",
                        null,
                        null,
                        1999,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                ),
            )
        }
    }

    @After
    fun close() = db.close()

    private fun repository() =
        MetadataRepository(
            db.vod(),
            keys,
            MetadataService(TmdbSource(fetcher, endpoints), OmdbSource(fetcher, endpoints)),
            Clock { now },
        )

    private fun item() = VodItem("m1", ProviderId("p"), VodKind.MOVIE, "Matrix", "ref", year = 1999)

    @Test
    fun `tmdb i omdb daja opis oceny obsade i czas`() =
        runBlocking {
            val r = MetadataService(TmdbSource(fetcher, endpoints), OmdbSource(fetcher, endpoints))
            val meta = r.fetch(item(), ApiKeys("klucz-t", "klucz-o"))!!
            assertEquals(8.2, meta.ratings.tmdb!!, 0.001)
            assertEquals(8.7, meta.ratings.imdb!!, 0.001)
            assertEquals(83, meta.ratings.rtPercent)
            assertEquals(8.7, meta.ratings.best!!, 0.001) // plakietka: IMDb przed TMDB
            assertEquals("Haker odkrywa prawdę o świecie.", meta.overview) // opis po polsku z TMDB, nie z OMDb
            assertEquals("Keanu Reeves, Carrie-Anne Moss", meta.cast)
            assertEquals(136, meta.runtimeMin)
            assertEquals("Akcja, Sci-Fi", meta.genre)
            assertTrue(meta.poster!!.endsWith("/w500/p.jpg"))
            assertTrue(urls.any { "language=pl-PL" in it && "year=1999" in it }) // wyszukiwanie z rokiem, po polsku
            assertTrue(urls.any { "i=tt0133093" in it }) // OMDb po id IMDb z TMDB (trafniej niż po tytule)
        }

    @Test
    fun `samo omdb bez klucza tmdb daje oceny i plot angielski`() =
        runBlocking {
            val r = MetadataService(TmdbSource(fetcher, endpoints), OmdbSource(fetcher, endpoints))
            val meta = r.fetch(item(), ApiKeys(tmdb = "", omdb = "klucz-o"))!!
            assertNull(meta.ratings.tmdb)
            assertEquals(8.7, meta.ratings.imdb!!, 0.001)
            assertEquals("Plot EN", meta.overview)
            assertTrue(urls.none { "tmdb.invalid" in it })
            assertTrue(urls.any { "t=Matrix" in it && "y=1999" in it && "type=movie" in it })
        }

    /**
     * Raport z emulatora 3.11: dopisanie metadanych do filmu (INSERT OR REPLACE) przenosiło jego wiersz na koniec
     * katalogu, a kolejność „Najnowsze” to kolejność wstawienia: filmy skakały po wzbogaceniu, a siatka za nimi.
     */
    @Test
    fun `wzbogacenie filmu nie zmienia jego miejsca w katalogu`() =
        runBlocking {
            val second =
                db
                    .vod()
                    .get(
                        "m1",
                    )!!
                    .copy(id = "m2", title = "Drugi", normTitle = "drugi", ref = "r2", year = 2000)
            db.vod().insertAll(listOf(second))
            val before =
                db
                    .vod()
                    .observeByKind("MOVIE")
                    .first()
                    .map { it.id }
            assertEquals(listOf("m1", "m2"), before)
            keys.save(ApiKeys(tmdb = "klucz-t", omdb = "klucz-o"))
            assertTrue(repository().ensure("m1")) // wzbogacenie pierwszego filmu zapisuje opis, ocenę i plakat
            val after =
                db
                    .vod()
                    .observeByKind("MOVIE")
                    .first()
                    .map { it.id }
            assertEquals("kolejność po wzbogaceniu", before, after)
            assertNotNull(db.vod().get("m1")!!.poster)
        }

    @Test
    fun `bez zadnych kluczy nic nie jest pobierane ani zapisywane`() =
        runBlocking {
            val repo = repository()
            assertFalse(repo.ensure("m1"))
            assertTrue(urls.isEmpty())
            assertNull(db.vod().meta("m1")) // po wpisaniu klucza zadziała od razu
        }

    @Test
    fun `dane trafiaja do bazy i uzupelniaja puste pola filmu`() =
        runBlocking {
            keys.save(ApiKeys("klucz-t", "klucz-o"))
            val repo = repository()
            assertTrue(repo.ensure("m1"))
            val meta = db.vod().meta("m1")!!
            assertTrue(meta.found)
            assertEquals(8.7, meta.imdbRating!!, 0.001)
            val item = db.vod().get("m1")!!
            assertEquals("Haker odkrywa prawdę o świecie.", item.plot)
            assertEquals(136, item.durationMin)
            assertEquals("Keanu Reeves, Carrie-Anne Moss", item.cast)
            val calls = urls.size
            assertTrue(repo.ensure("m1")) // drugi raz z bazy, bez sieci
            assertEquals(calls, urls.size)
        }

    @Test
    fun `brak trafienia jest zapamietany i ponawiany dopiero po czasie`() =
        runBlocking {
            keys.save(ApiKeys("klucz-t", ""))
            tmdbSearch = """{"results":[]}"""
            val repo = repository()
            assertFalse(repo.ensure("m1"))
            val calls = urls.size
            assertFalse(repo.ensure("m1"))
            assertEquals("bez ponowienia w oknie", calls, urls.size)
            now += 7L * 60 * 60 * 1000
            assertFalse(repo.ensure("m1"))
            assertTrue("po oknie próbuje ponownie", urls.size > calls)
        }

    @Test
    fun `wlasne dane dostawcy listy nie sa nadpisywane`() =
        runBlocking {
            keys.save(ApiKeys("klucz-t", "klucz-o"))
            db.vod().insertAll(
                listOf(
                    VodItemEntity(
                        "m2",
                        "p",
                        "MOVIE",
                        "Matrix",
                        "matrix",
                        "ref2",
                        null,
                        null,
                        1999,
                        6.0,
                        null,
                        "Opis od dostawcy",
                        100,
                        null,
                        null,
                        null,
                    ),
                ),
            )
            repository().ensure("m2")
            val item = db.vod().get("m2")!!
            assertEquals("Opis od dostawcy", item.plot)
            assertEquals(6.0, item.rating!!, 0.001)
            assertEquals(100, item.durationMin)
        }
}
