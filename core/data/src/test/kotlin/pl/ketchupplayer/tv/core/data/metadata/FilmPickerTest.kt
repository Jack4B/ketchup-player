package pl.ketchupplayer.tv.core.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Wybór pozycji o tym samym tytule: z rokiem najbliższy, bez roku tylko wyraźny lider (dane fikcyjne). */
class FilmPickerTest {
    private fun ref(
        imdb: String,
        year: Int,
        votes: Int?,
    ) = FilmRef(imdb, null, year, null, null, null, imdbRating = 7.0, imdbVotes = votes)

    @Test
    fun `z rokiem wybiera najblizszy, a przy remisie wiecej glosow`() {
        val a = ref("tt1", 2005, 100)
        val b = ref("tt2", 2005, 9000)
        val c = ref("tt3", 1999, 500_000)
        assertEquals("tt2", FilmPicker.pick(listOf(a, b, c), 2005)?.imdb)
        assertEquals("tt3", FilmPicker.pick(listOf(a, b, c), 1999)?.imdb)
        assertEquals("tt2", FilmPicker.pick(listOf(a, b, c), 2006)?.imdb)
        assertNull(FilmPicker.pick(listOf(a, b, c), 2015))
    }

    @Test
    fun `bez roku jedyny kandydat wygrywa, brak kandydatow to null`() {
        assertEquals("tt1", FilmPicker.pick(listOf(ref("tt1", 2005, null)), null)?.imdb)
        assertNull(FilmPicker.pick(emptyList(), null))
        assertNull(FilmPicker.pick(emptyList(), 2005))
    }

    @Test
    fun `bez roku wyrazny lider wygrywa`() {
        val leader = ref("tt1", 2021, 800_000)
        val other = ref("tt2", 1984, 30_000)
        assertEquals("tt1", FilmPicker.pick(listOf(other, leader), null)?.imdb)
    }

    @Test
    fun `bez roku brak wyraznego lidera daje null zamiast zgadywania`() {
        // Remake o zbliżonej popularności.
        assertNull(FilmPicker.pick(listOf(ref("tt1", 1990, 260_000), ref("tt2", 2012, 230_000)), null))
        // Lider poniżej progu pewności (mało głosów).
        assertNull(FilmPicker.pick(listOf(ref("tt1", 1990, 9_000), ref("tt2", 2012, 10)), null))
        // Brak danych o głosach.
        assertNull(FilmPicker.pick(listOf(ref("tt1", 1990, null), ref("tt2", 2012, null)), null))
        // Dokładnie na granicy stosunku: 20x to jeszcze lider, 19x już nie.
        assertEquals("tt1", FilmPicker.pick(listOf(ref("tt1", 1990, 200_000), ref("tt2", 2012, 10_000)), null)?.imdb)
        assertNull(FilmPicker.pick(listOf(ref("tt1", 1990, 190_000), ref("tt2", 2012, 10_000)), null))
    }
}
