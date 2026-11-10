package pl.ketchupplayer.tv.core.policy.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.personalization.TeamAlias

/** Parser drużyn z tytułów EPG. Drużyny „Nadmorska” i „Leśny” są zmyślone; reszta to wbudowany słownik nazw. */
class TeamParserTest {
    private val synthetic =
        listOf(
            TeamAlias("wisla-nadmorska", "wisla nadmorska"),
            TeamAlias("hutnik-lesny", "hutnik lesny"),
            TeamAlias("hutnik-lesny", "hutnik"),
            TeamAlias("slask-testowy", "slask testowy"),
            TeamAlias("port-polnocny", "port", weak = true),
            TeamAlias("arka-testowa", "derby trojmiasta", ambiguousGroup = "derby-trojmiasta"),
            TeamAlias("lechia-testowa", "derby trojmiasta", ambiguousGroup = "derby-trojmiasta"),
        )
    private val index = AliasIndex(synthetic)
    private val bundled = AliasIndex(BundledTeams.aliases)

    private fun parse(
        title: String,
        description: String? = null,
        category: String? = null,
        idx: AliasIndex = index,
    ) = TeamParser.parse(title, description, category, idx)

    @Test
    fun paraZPrefiksemLigiIDywizem() {
        val r = parse("Liga Nadmorska: Wisła Nadmorska — Hutnik Leśny")
        assertEquals(listOf("wisla-nadmorska", "hutnik-lesny"), r.teams)
        assertEquals("h2h", r.pattern)
        assertEquals(0.85, r.confidence, 0.0)
        assertFalse(r.ambiguous)
        assertTrue(r.pair)
    }

    @Test
    fun separatoryVsIMyslnik() {
        listOf(
            "Wisła Nadmorska - Hutnik Leśny",
            "Wisła Nadmorska vs Hutnik Leśny",
            "WISLA NADMORSKA vs. HUTNIK",
            "Wisła Nadmorska – Hutnik Leśny (na żywo)",
        ).forEach {
            assertEquals(it, listOf("wisla-nadmorska", "hutnik-lesny"), parse(it).teams)
        }
    }

    @Test
    fun diakrytykiNieMajaZnaczenia() {
        assertEquals(parse("Śląsk Testowy - Hutnik Leśny").teams, parse("Slask Testowy - Hutnik Lesny").teams)
        assertEquals(listOf("slask-testowy", "hutnik-lesny"), parse("ŚLĄSK TESTOWY - HUTNIK LEŚNY").teams)
    }

    @Test
    fun derbyBezNazwDruzynSaNiejednoznaczne() {
        val r = parse("Derby Trójmiasta")
        assertTrue(r.ambiguous)
        assertEquals(0.2, r.confidence, 0.0)
        assertEquals("ambiguous", r.pattern)
    }

    @Test
    fun aliasWspolnyDlaDwochDruzynJestNiejednoznacznyAPelnaNazwaNie() {
        assertTrue(parse("Wisła - Legia Warszawa", idx = bundled).ambiguous)
        val full = parse("Wisła Kraków - Legia Warszawa", idx = bundled)
        assertFalse(full.ambiguous)
        assertEquals(listOf("wisla-krakow", "legia-warszawa"), full.teams)
        assertTrue(parse("Manchester - Arsenal", idx = bundled).ambiguous)
        assertEquals(listOf("manchester-city", "arsenal"), parse("Manchester City - Arsenal", idx = bundled).teams)
    }

    @Test
    fun jednaZnanaStronaToSlabszyDowod() {
        val r = parse("Wisła Nadmorska - Zespół Spoza Słownika")
        assertEquals(listOf("wisla-nadmorska"), r.teams)
        assertEquals(0.55, r.confidence, 0.0)
        assertTrue(r.pair)
    }

    @Test
    fun slabyAliasNieLiczySiePozaMeczem() {
        assertTrue("sam wyraz w zwykłym tytule", parse("Port lotniczy od kuchni").teams.isEmpty())
        val travel = parse("Port - miasto nad zatoką")
        assertTrue("para bez kategorii sportowej: poniżej progu dowodu", travel.confidence < 0.5)
        val match = parse("Port - Zespół Spoza Słownika", category = "Sport / Piłka nożna")
        assertEquals(0.55, match.confidence, 0.0)
        assertEquals(0.85, parse("Port - Hutnik Leśny").confidence, 0.0)
    }

    @Test
    fun kategoriaInnaNizSportObnizaPewnoscPojedynczegoTrafienia() {
        assertEquals(0.55, parse("Hutnik Leśny: kulisy sezonu").confidence, 0.0)
        assertTrue(parse("Hutnik Leśny: kulisy sezonu", category = "Film dokumentalny").confidence < 0.5)
        assertEquals(0.55, parse("Hutnik Leśny: kulisy sezonu", category = "Sport").confidence, 0.0)
    }

    @Test
    fun tytulBezDruzynNieDajeNic() {
        val r = parse("Wiadomości wieczorne")
        assertTrue(r.teams.isEmpty())
        assertEquals(0.0, r.confidence, 0.0)
        assertFalse(r.ambiguous)
    }

    @Test
    fun druzynyZOpisuGdyTytulJestOgolny() {
        val r = parse("Piłka nożna: Liga Nadmorska", description = "Wisła Nadmorska - Hutnik Leśny. Mecz 12. kolejki.")
        assertEquals(listOf("wisla-nadmorska", "hutnik-lesny"), r.teams)
        assertEquals(0.75, r.confidence, 1e-9)
    }

    @Test
    fun powtorkiISkrotySaOznaczane() {
        assertTrue(parse("Wisła Nadmorska - Hutnik Leśny (powtórka)").replay)
        assertTrue(parse("Skróty: Wisła Nadmorska - Hutnik Leśny").replay)
        assertTrue(parse("Studio: Wisła Nadmorska - Hutnik Leśny").replay)
        assertTrue(parse("Wisła Nadmorska - Hutnik Leśny", description = "Retransmisja meczu.").replay)
        assertFalse(parse("Wisła Nadmorska - Hutnik Leśny").replay)
    }

    @Test
    fun tytulZapisanyPrzySesjiParsujeSieTakSamoJakOryginal() {
        listOf(
            "Liga Nadmorska: Wisła Nadmorska — Hutnik Leśny",
            "Wisła Nadmorska vs. Hutnik Leśny (na żywo)",
            "Śląsk Testowy - Zespół Spoza Słownika",
            "Derby Trójmiasta",
            "Wiadomości wieczorne",
        ).forEach { title ->
            val stored = TeamParser.evidenceTitle(title)
            assertEquals(title, parse(title).teams, parse(stored).teams)
            assertEquals(title, parse(title).ambiguous, parse(stored).ambiguous)
            assertEquals(stored, stored.lowercase())
            assertTrue(stored.all { it.code < 128 })
        }
        assertEquals(
            "liga nadmorska: wisla nadmorska - hutnik lesny",
            TeamParser.evidenceTitle("Liga Nadmorska: Wisła Nadmorska — Hutnik Leśny"),
        )
    }

    @Test
    fun rozgrywkiZTytuluIOdwolanie() {
        assertEquals("pl-ekstraklasa", parse("Ekstraklasa: Legia Warszawa - Lech Poznań", idx = bundled).competitionKey)
        assertEquals("uefa-cl", parse("Liga Mistrzów: Real Madryt - FC Barcelona", idx = bundled).competitionKey)
        assertNull(parse("Wisła Nadmorska - Hutnik Leśny").competitionKey)
        assertEquals(ScheduleNote.CANCELED, TeamParser.scheduleNote("Wisła Nadmorska - Hutnik Leśny (odwołany)", null))
        assertEquals(
            ScheduleNote.POSTPONED,
            TeamParser.scheduleNote("Wisła Nadmorska - Hutnik Leśny", "Mecz przełożony."),
        )
        assertNull(TeamParser.scheduleNote("Wisła Nadmorska - Hutnik Leśny", null))
    }

    @Test
    fun wbudowanyKatalogJestSpojny() {
        val ids = BundledTeams.teams.map { it.id }
        assertEquals("unikalne identyfikatory", ids.size, ids.toSet().size)
        assertTrue(BundledTeams.aliases.all { it.teamId in ids && it.aliasNorm.isNotBlank() })
        assertEquals(listOf("legia-warszawa", "lech-poznan"), parse("Legia - Lech", idx = bundled).teams)
        assertEquals(listOf("real-madryt", "fc-barcelona"), parse("Real Madryt vs Barcelona", idx = bundled).teams)
    }

    @Test
    fun druzynaUzytkownikaDostajeAliasyZNazwy() {
        val id = BundledTeams.userTeamId("Błękitni Zmyśleni")
        assertEquals("user-blekitni-zmysleni", id)
        val idx = AliasIndex(BundledTeams.userAliases(id, "Błękitni Zmyśleni", listOf("Błękitni")))
        assertEquals(listOf(id), parse("Błękitni - Zespół Spoza Słownika", idx = idx).teams)
    }
}
