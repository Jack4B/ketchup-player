package pl.ketchupplayer.tv.core.data.metadata

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pl.ketchupplayer.tv.core.model.VodTitle
import java.io.File

/**
 * Regresja dopasowania ocen: syntetyczne nazwy z paneli IPTV zbudowane z tytułów wbudowanego indeksu (publiczne dane
 * Wikidata, CC0), przepuszczone przez prawdziwe `VodTitle` i `SqliteFilmIndex`. Próbka jest deterministyczna
 * (`id % 20`, pozycje z co najmniej 5000 głosów, czyli kształt typowego katalogu). To NIE jest pomiar prawdziwej listy
 * ani tłumaczeń tytułów: tytuły pochodzą z samego indeksu, więc wynik to górna granica dla danego wzorca nazwy.
 */
@RunWith(RobolectricTestRunner::class)
class TitleMatchingRegressionTest {
    private lateinit var db: SQLiteDatabase
    private lateinit var index: FilmIndex

    private data class Sample(
        val imdb: String,
        val year: Int,
        val title: String,
    )

    private class Shape(
        val name: String,
        val withYear: Boolean,
        val render: (String, Int) -> String,
    )

    private val shapes =
        listOf(
            Shape("T (Y)", true) { t, y -> "$t ($y)" },
            Shape("|PL| T (Y)", true) { t, y -> "|PL| $t ($y)" },
            Shape("PL - T (Y)", true) { t, y -> "PL - $t ($y)" },
            Shape("T (Y) [4K]", true) { t, y -> "$t ($y) [4K]" },
            Shape("T (Y) [LEKTOR PL]", true) { t, y -> "$t ($y) [LEKTOR PL]" },
            Shape("T Y", true) { t, y -> "$t $y" },
            Shape("T [Y]", true) { t, y -> "$t [$y]" },
            Shape("T.Y.1080p.BluRay.x264", true) { t, y -> t.replace(' ', '.') + ".$y.1080p.BluRay.x264" },
            Shape("A+ - T (Y)", true) { t, y -> "A+ - $t ($y)" },
            Shape("NETFLIX - T (Y)", true) { t, y -> "NETFLIX - $t ($y)" },
            Shape("PL | T (Y) 4K", true) { t, y -> "PL | $t ($y) 4K" },
            Shape("T (Y) (Dubbing)", true) { t, y -> "$t ($y) (Dubbing)" },
            Shape("T (Y) [ENG]", true) { t, y -> "$t ($y) [ENG]" },
            Shape("[PL] T (Y)", true) { t, y -> "[PL] $t ($y)" },
            Shape("T (Y) HD", true) { t, y -> "$t ($y) HD" },
            Shape("HBO - T [Y] DUAL", true) { t, y -> "HBO - $t [$y] DUAL" },
            Shape("T (bez roku)", false) { t, _ -> t },
            Shape("T PL", false) { t, _ -> "$t PL" },
            Shape("T - PL", false) { t, _ -> "$t - PL" },
            Shape("T 4K UHD", false) { t, _ -> "$t 4K UHD" },
        )

    @Before
    fun setUp() {
        // Katalog roboczy testów modułu to katalog modułu; plik jest tym samym, który trafia do APK.
        val asset = File("src/main/assets/film_index.db")
        assertTrue("brak ${asset.absolutePath}", asset.exists())
        db = SQLiteDatabase.openDatabase(asset.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        index = SqliteFilmIndex(db)
    }

    @After
    fun tearDown() = db.close()

    private fun samples(): List<Sample> =
        buildList {
            db
                .rawQuery(
                    "SELECT imdb, year, COALESCE(NULLIF(pl, ''), en) FROM film WHERE kind = 0 AND rating IS NOT NULL " +
                        "AND votes >= 5000 AND year BETWEEN 1980 AND 2025 AND (pl IS NOT NULL OR en IS NOT NULL) " +
                        "AND id % 20 = 0 ORDER BY id LIMIT 600",
                    null,
                ).use { c -> while (c.moveToNext()) add(Sample(c.getString(0), c.getInt(1), c.getString(2))) }
        }

    @Test
    fun wzorceNazwZRokiemMajaWysokaSkutecznoscINiskiBlad() {
        val rows = samples()
        assertTrue("za mała próbka: ${rows.size}", rows.size >= 400)
        val failures = mutableListOf<String>()
        for (shape in shapes) {
            var hit = 0
            var wrong = 0
            for (r in rows) {
                val parsed = VodTitle.parse(shape.render(r.title, r.year))
                val found = index.find(parsed.title, parsed.year, series = false)
                if (found?.imdb == r.imdb) {
                    hit++
                } else if (found != null) {
                    wrong++
                }
            }
            val hitPct = 100.0 * hit / rows.size
            val wrongPct = 100.0 * wrong / rows.size
            val minHit = if (shape.withYear) MIN_HIT_WITH_YEAR else MIN_HIT_WITHOUT_YEAR
            if (hitPct < minHit || wrongPct > MAX_WRONG) {
                failures +=
                    "'${shape.name}': trafione %.1f%% (min %.0f%%), błędne %.1f%% (max %.0f%%)"
                        .format(hitPct, minHit, wrongPct, MAX_WRONG)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val MIN_HIT_WITH_YEAR = 95.0
        const val MIN_HIT_WITHOUT_YEAR = 80.0
        const val MAX_WRONG = 1.0
    }
}
