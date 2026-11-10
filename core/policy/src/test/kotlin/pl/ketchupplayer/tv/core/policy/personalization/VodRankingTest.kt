package pl.ketchupplayer.tv.core.policy.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.Provenance

/** Cechy z metadanych i ranking pasa „Bo oglądasz”. Katalog w testach jest zmyślony. */
class VodRankingTest {
    private val p = PersonalizationParams.DEFAULT

    private fun candidate(
        id: String,
        genre: String?,
        director: String? = null,
        cast: String? = null,
    ) = VodCandidate(
        id,
        VodFeatures.extract(
            ItemMetadata(providerGenre = genre, providerCast = cast, tmdbDirector = director, tmdbMatch = TmdbMatch.ID),
        ),
    )

    @Test
    fun gatunkiWRoznychJezykachTrafiajaDoJednegoKlucza() {
        val keys = listOf("Akcja", "Action", "akcji", " AKCJA ").map { GenreDictionary.resolve(it)!!.key }.toSet()
        assertEquals(setOf("action"), keys)
        assertEquals("Kryminał", GenreDictionary.resolve("kryminal")!!.label)
        assertEquals("crime", GenreDictionary.resolve("Kryminał")!!.key)
    }

    @Test
    fun melodramatNieStajeSieDramatem() {
        assertEquals("drama", GenreDictionary.resolve("Dramat")!!.key)
        assertEquals("melodramat", GenreDictionary.resolve("Melodramat")!!.key)
    }

    @Test
    fun brakMetadanychToBrakCechNigdyZgadywanie() {
        assertTrue(VodFeatures.extract(ItemMetadata()).isEmpty())
        assertTrue(VodFeatures.extract(ItemMetadata(providerGenre = "  ", providerCast = "")).isEmpty())
    }

    @Test
    fun rezyserTylkoZTmdbAWyszukiwanieBezRokuNieDajeCech() {
        val byId =
            VodFeatures.extract(
                ItemMetadata(tmdbDirector = "Anna Testowa", tmdbGenre = "Kryminał", tmdbMatch = TmdbMatch.ID),
            )
        assertEquals(0.9, byId.single { it.namespace == FeatureNamespace.DIRECTOR }.confidence, 0.0)
        assertEquals(Provenance.TMDB, byId.single { it.namespace == FeatureNamespace.GENRE }.provenance)
        val weak =
            VodFeatures.extract(
                ItemMetadata(tmdbDirector = "Anna Testowa", tmdbGenre = "Kryminał", tmdbMatch = TmdbMatch.SEARCH),
            )
        assertTrue("dopasowanie po samym tytule jest poniżej progu", weak.isEmpty())
        val withYear =
            VodFeatures.extract(
                ItemMetadata(tmdbDirector = "Anna Testowa", tmdbMatch = TmdbMatch.SEARCH_WITH_YEAR),
            )
        assertEquals(0.6, withYear.single().confidence, 0.0)
    }

    @Test
    fun udzialGatunkuIObsadyMalejeZLiczbaAObsadaMaLimit() {
        val f =
            VodFeatures.extract(
                ItemMetadata(
                    providerGenre = "Akcja, Thriller / Dramat | Komedia",
                    providerCast = "A Aaa, B Bbb, C Ccc, D Ddd, E Eee, F Fff, G Ggg",
                ),
            )
        val genres = f.filter { it.namespace == FeatureNamespace.GENRE }
        val cast = f.filter { it.namespace == FeatureNamespace.CAST }
        assertEquals(4, genres.size)
        assertEquals(0.5, genres.first().weightOnItem, 1e-9)
        assertEquals("najwyżej 5 aktorów", 5, cast.size)
        assertEquals(1.0 / Math.sqrt(5.0), cast.first().weightOnItem, 1e-9)
    }

    @Test
    fun tenSamGatunekZDwochZrodelZostajeRazZWyzszaPewnoscia() {
        val f =
            VodFeatures.extract(
                ItemMetadata(providerGenre = "Crime", tmdbGenre = "Kryminał", tmdbMatch = TmdbMatch.ID),
            )
        assertEquals(1, f.size)
        assertEquals(0.9, f.single().confidence, 0.0)
    }

    /** Kontrfakt 5: pozycja spoza katalogu nigdy nie wchodzi na listę, nawet przy idealnym dopasowaniu. */
    @Test
    fun kandydatSpozaKataloguNigdyNieWchodzi() {
        val inside = candidate("w-katalogu", "Kryminał")
        val outside = candidate("spoza-katalogu", "Kryminał", director = "Anna Testowa")
        val pref = mapOf("GENRE:crime" to 2.0, "DIRECTOR:anna testowa" to 50.0)
        val cards = VodRanker.rank(setOf("w-katalogu"), listOf(outside, inside), pref, emptySet(), p)
        assertEquals(listOf("w-katalogu"), cards.map { it.itemId })
    }

    @Test
    fun ukryteIBezTrafnosciNieSaPokazywane() {
        val pref = mapOf("GENRE:crime" to 2.0)
        val items =
            listOf(
                candidate("a", "Kryminał"),
                candidate("b", "Kryminał"),
                candidate("c", "Komedia"),
                candidate("d", null),
            )
        val cards = VodRanker.rank(items.map { it.itemId }.toSet(), items, pref, excluded = setOf("b"), p)
        assertEquals(listOf("a"), cards.map { it.itemId })
    }

    @Test
    fun pustyProfilNieDajeRekomendacji() {
        val items = listOf(candidate("a", "Kryminał"), candidate("b", "Komedia"))
        assertTrue(VodRanker.rank(setOf("a", "b"), items, emptyMap(), emptySet(), p).isEmpty())
    }

    @Test
    fun uzasadnieniaTylkoZCechKtoreWeszlyDoWyniku() {
        val item = candidate("a", "Kryminał, Komedia", director = "Anna Testowa", cast = "Jan Przykładowy")
        val pref = mapOf("DIRECTOR:anna testowa" to 5.0, "GENRE:crime" to 1.0, "GENRE:comedy" to -3.0)
        val card = VodRanker.rank(setOf("a"), listOf(item), pref, emptySet(), p).single()
        assertEquals(listOf(FeatureNamespace.DIRECTOR, FeatureNamespace.GENRE), card.why.map { it.namespace })
        assertEquals(listOf("Anna Testowa", "Kryminał"), card.why.map { it.label })
        assertFalse(
            "ujemna i nieobecna w profilu cecha nie jest powodem",
            card.why.any {
                it.label == "Komedia" ||
                    it.label == "Jan Przykładowy"
            },
        )
    }

    @Test
    fun rzadMaLimitRezyseraIGatunku() {
        val sameDirector = (1..5).map { candidate("r$it", "Kryminał", director = "Anna Testowa") }
        val crime = (1..6).map { candidate("k$it", "Kryminał") }
        val other = (1..4).map { candidate("t$it", "Thriller") }
        val all = sameDirector + crime + other
        val pref = mapOf("DIRECTOR:anna testowa" to 9.0, "GENRE:crime" to 3.0, "GENRE:thriller" to 1.0)
        val cards = VodRanker.rank(all.map { it.itemId }.toSet(), all, pref, emptySet(), p)
        val ids = cards.map { it.itemId }
        assertEquals("najwyżej 2 pozycje jednego reżysera", 2, ids.count { it.startsWith("r") })
        assertEquals(
            "najwyżej 3 pozycje z tym samym głównym gatunkiem",
            3,
            ids.count {
                it.startsWith("r") ||
                    it.startsWith("k")
            },
        )
        assertEquals("limit gatunku dotyczy każdego gatunku", 3, ids.count { it.startsWith("t") })
        assertEquals("najtrafniejsza pozycja jest pierwsza", "r1", ids.first())
    }

    @Test
    fun pelnyRzadMaJednaPozycjeDlaOdmianyBezZmyslonegoPowodu() {
        val crime = (1..5).map { candidate("k%02d".format(it), "Kryminał") }
        val many =
            listOf("Thriller", "Dramat", "Akcja", "Horror", "Western").flatMapIndexed {
                g,
                genre,
                ->
                (1..3).map { candidate("g$g-$it", genre) }
            }
        val unknown = (1..3).map { candidate("u$it", "Familijny") }
        val all = crime + many + unknown
        val pref =
            mapOf(
                "GENRE:crime" to 5.0,
                "GENRE:thriller" to 4.0,
                "GENRE:drama" to 3.0,
                "GENRE:action" to 2.0,
                "GENRE:horror" to 1.5,
                "GENRE:western" to 1.0,
            )
        val cards = VodRanker.rank(all.map { it.itemId }.toSet(), all, pref, emptySet(), p, exploreSeed = "household-1")
        assertEquals(12, cards.size)
        val explore = cards.filter { it.explore }
        assertEquals(1, explore.size)
        assertTrue(explore.single().itemId.startsWith("u"))
        assertTrue(explore.single().why.isEmpty())
        assertEquals(
            "ten sam dzień = ten sam wybór",
            cards,
            VodRanker.rank(
                all
                    .map {
                        it.itemId
                    }.toSet(),
                all,
                pref,
                emptySet(),
                p,
                exploreSeed = "household-1",
            ),
        )
    }
}
