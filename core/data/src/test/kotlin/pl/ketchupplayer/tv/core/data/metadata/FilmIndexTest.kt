package pl.ketchupplayer.tv.core.data.metadata

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind
import pl.ketchupplayer.tv.core.network.TextFetcher
import java.io.File

/** Wbudowany indeks filmów (Wikidata): normalizacja zgodna z `tools/wikidata/pack_index.py` i wybór pozycji. */
@RunWith(RobolectricTestRunner::class)
class FilmIndexTest {
    private lateinit var file: File
    private lateinit var db: SQLiteDatabase
    private lateinit var index: FilmIndex

    @Before
    fun setUp() {
        file = File.createTempFile("film-index", ".db")
        db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE film(id INTEGER PRIMARY KEY, imdb TEXT, tmdb INTEGER, fw TEXT, year INTEGER, kind INTEGER, " +
                "pl TEXT, en TEXT, orig TEXT, plwiki TEXT)",
        )
        db.execSQL("CREATE TABLE title(norm TEXT, film INTEGER)")
        film(1, "tt0425007", 9475, 2005, 0, "Pitbull", "Pitbull", "Pitbull_(film_2005)")
        film(2, "tt9999999", null, 1999, 0, "Pitbull", "Pitbull", null)
        film(3, "tt0133093", 603, 1999, 0, "Matrix", "The Matrix", "Matrix")
        film(4, "tt1520211", 1402, 2010, 1, "Żywe trupy", "The Walking Dead", null)
        index = SqliteFilmIndex(db)
    }

    @After
    fun tearDown() {
        db.close()
        file.delete()
    }

    @Suppress("LongParameterList") // Wiersz tabeli `film` w teście: parametry odpowiadają kolumnom
    private fun film(
        id: Int,
        imdb: String,
        tmdb: Int?,
        year: Int,
        kind: Int,
        pl: String,
        en: String,
        plwiki: String?,
    ) {
        db.execSQL(
            "INSERT INTO film VALUES(?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(id, imdb, tmdb, null, year, kind, pl, en, null, plwiki),
        )
        listOf(pl, en).map(FilmTitles::normalize).distinct().forEach {
            db.execSQL("INSERT INTO title VALUES(?,?)", arrayOf<Any?>(it, id))
        }
    }

    @Test
    fun normalizacjaJestZgodnaZSkryptemPython() {
        // Wartości z pack_index.py (normalize); zmiana jednej strony bez drugiej psuje wyszukiwanie.
        val golden =
            mapOf(
                "Żółć" to "zolc",
                "Łowca androidów" to "lowcaandroidow",
                "Ça va? (2005)" to "cava2005",
                "Mädchen in Uniform" to "madcheninuniform",
                "Gwiezdne wojny: Część IV – Nowa nadzieja" to "gwiezdnewojnyczescivnowanadzieja",
                "Æon Flux" to "aeonflux",
                "Привет" to "",
                "Ørnen" to "ornen",
            )
        golden.forEach { (input, want) -> assertEquals(input, want, FilmTitles.normalize(input)) }
    }

    @Test
    fun znajdujeFilmPoTytuleIRokuZTolerancjaJednegoRoku() {
        assertEquals("tt0425007", index.find("Pitbull", 2005, false)?.imdb)
        assertEquals("tt0425007", index.find("PITBULL", 2006, false)?.imdb) // rok w nazwie bywa o 1 różny
        assertEquals("tt9999999", index.find("Pitbull", 1999, false)?.imdb)
        assertNull(index.find("Pitbull", 2015, false)) // żaden film z tego roku: nie zgadujemy
    }

    @Test
    fun znajdujePoTytuleAngielskimIDajeStroneWikipedii() {
        val ref = index.find("The Matrix", 1999, false)
        assertNotNull(ref)
        assertEquals(603L, ref!!.tmdb)
        assertEquals("Matrix", ref.plWikiTitle)
    }

    @Test
    fun bezRokuTylkoGdyTytulJednoznaczny() {
        assertEquals("tt0133093", index.find("Matrix", null, false)?.imdb)
        assertNull(index.find("Pitbull", null, false)) // dwa filmy o tym tytule
    }

    @Test
    fun serialIFilmToOsobneSpisy() {
        assertEquals("tt1520211", index.find("The Walking Dead", 2010, true)?.imdb)
        assertNull(index.find("The Walking Dead", 2010, false))
    }

    /** Indeks oszczędza zapytania: id TMDB znane z indeksu pomija wyszukiwanie, a MDBList dostaje id IMDb. */
    @Test
    fun serwisKorzystaZIdZIndeksuIPomijaWyszukiwanie() {
        val urls = mutableListOf<String>()
        val fetcher =
            TextFetcher { url ->
                urls += url
                when {
                    "/movie/9475" in url -> Result.success("""{"vote_average":7.0,"overview":"Opis TMDB."}""")
                    "/imdb/movie/tt0425007" in url -> Result.success("""{"ratings":[{"source":"imdb","value":7.6}]}""")
                    else -> Result.failure(IllegalStateException("404"))
                }
            }
        val e = MetadataEndpoints()
        val service =
            MetadataService(
                MetadataSources(
                    tmdb = TmdbSource(fetcher, e),
                    omdb = OmdbSource(fetcher, e),
                    mdblist = MdbListSource(fetcher, e),
                    index = index,
                ),
            )
        val item =
            VodItem("x", ProviderId("p"), VodKind.MOVIE, "Pitbull", ref = "1", year = 2005)
        val r = runBlocking { service.fetch(item, ApiKeys(tmdb = "t", mdblist = "m"), deep = true) }!!
        assertEquals(7.6, r.ratings.imdb!!, 0.001)
        org.junit.Assert.assertFalse("bez wyszukiwania: $urls", urls.any { "/search/" in it })
        assertEquals(1, urls.count { "/imdb/movie/tt0425007" in it })
    }
}
