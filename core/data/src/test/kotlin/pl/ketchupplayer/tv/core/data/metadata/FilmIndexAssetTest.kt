package pl.ketchupplayer.tv.core.data.metadata

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Plik `film_index.db` z repozytorium: schemat i normalizacja zgadzają się z aplikacją, oceny się wczytują. */
@RunWith(RobolectricTestRunner::class)
class FilmIndexAssetTest {
    @Test
    fun prawdziwyAssetZnajdujeFilmyPoPolskimIAngielskimTytuleZOcenaIMDb() {
        val file = File("src/main/assets/film_index.db")
        assertTrue("brak assetu ${file.absolutePath}", file.exists())
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        db.use {
            val index = SqliteFilmIndex(it)
            val matrix = index.find("Matrix", 1999, series = false)
            assertNotNull(matrix)
            assertEquals("tt0133093", matrix?.imdb)
            assertEquals(603L, matrix?.tmdb)
            assertTrue("ocena IMDb ok. 8,7", (matrix?.imdbRating ?: 0.0) in 8.5..8.9)
            // Ten sam film po tytule angielskim; polskie znaki w zapytaniu dają ten sam klucz.
            assertEquals("tt0133093", index.find("The Matrix", 1999, series = false)?.imdb)
            assertEquals("tt0903747", index.find("Breaking Bad", 2008, series = true)?.imdb)
            assertEquals(
                "Matrix (1999) w tytule z roku nie jest szukany",
                null,
                index.find("zzz brak takiego filmu", 1999, false),
            )
        }
    }
}
