package pl.ketchupplayer.tv.core.data.personalization

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.database.VodItemEntity

/**
 * Indeks cech i ranking na katalogu 5 tys. zmyślonych pozycji (P7). To test poprawności i rzędu wielkości na JVM,
 * nie pomiar na urządzeniu: katalog 30 tys. pozycji na TV-boxie pozostaje niesprawdzony (NOT_RUN).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeatureIndexScaleTest {
    private lateinit var f: PersonalizationFixture

    @Before
    fun setUp() =
        runBlocking {
            f = PersonalizationFixture()
            f.seedCatalog()
            f.settings.setEnabled(true)
        }

    @After
    fun tearDown() = f.close()

    private val genres = listOf("Kryminał", "Komedia", "Dramat", "Thriller", "Akcja", "Horror", "Familijny", "Western")

    private suspend fun seedBig(count: Int) {
        val items =
            (1..count).map { n ->
                val genre = genres[n % genres.size] + if (n % 5 == 0) ", " + genres[(n + 3) % genres.size] else ""
                VodItemEntity(
                    "big-$n",
                    PersonalizationFixture.PROVIDER,
                    "MOVIE",
                    "Film numer $n",
                    "film numer $n",
                    "r$n",
                    null,
                    null,
                    2000 + n % 25,
                    null,
                    genre.takeIf { n % 10 != 0 },
                    null,
                    100,
                    null,
                    null,
                    "Aktor ${n % 40}, Aktorka ${n % 55}",
                )
            }
        items.chunked(CHUNK).forEach { f.db.vod().insertAll(it) }
    }

    @Test
    fun indeksPieciuTysiecyPozycjiBudujeSieRazIDajeOgraniczonyPas() =
        runBlocking {
            seedBig(BIG)
            f.watch(PlaybackTarget.Vod("film-a", false), 95 * 60, PersonalizationFixture.FILM_MS)
            f.watch(PlaybackTarget.Vod("film-b", false), 95 * 60, PersonalizationFixture.FILM_MS)

            val started = System.nanoTime()
            f.engine.refreshNow()
            val firstMs = (System.nanoTime() - started) / 1_000_000
            assertEquals("każda pozycja z metadanymi jest w indeksie", BIG + SEEDED_WITH_FEATURES, f.dao.indexedItems())

            val again = System.nanoTime()
            f.engine.refreshNow()
            val secondMs = (System.nanoTime() - again) / 1_000_000

            val because =
                f.repo.snapshot
                    .first()
                    .because
            assertEquals("pas ma stałą długość niezależnie od wielkości katalogu", 12, because.size)
            assertEquals("bez powtórzeń", because.size, because.map { it.itemId }.toSet().size)
            assertTrue("pierwsza budowa indeksu: $firstMs ms", firstMs < BUILD_LIMIT_MS)
            assertTrue("kolejne przeliczenie bez przebudowy indeksu: $secondMs ms", secondMs < REFRESH_LIMIT_MS)
            println("INDEKS_5K budowa=${firstMs}ms przeliczenie=${secondMs}ms")
        }

    private companion object {
        const val BIG = 5_000
        const val CHUNK = 500

        /** Z katalogu podstawowego: 6 filmów z gatunkiem i serial (film bez metadanych nie ma wierszy w indeksie). */
        const val SEEDED_WITH_FEATURES = 7
        const val BUILD_LIMIT_MS = 30_000L
        const val REFRESH_LIMIT_MS = 10_000L
    }
}
