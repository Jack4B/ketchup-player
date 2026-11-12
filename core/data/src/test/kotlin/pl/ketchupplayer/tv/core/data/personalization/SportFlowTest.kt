package pl.ketchupplayer.tv.core.data.personalization

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationFixture.Companion.utc
import pl.ketchupplayer.tv.core.model.personalization.FollowState
import pl.ketchupplayer.tv.core.model.personalization.HomeSnapshot
import pl.ketchupplayer.tv.core.policy.personalization.WarsawTime

/**
 * Tor sportowy od oglądania transmisji do karty meczu (konspekt: punkt 10, scenariusze S3, S4, S6). Drużyny, kanały
 * i terminy są zmyślone; to nie jest prawdziwy terminarz.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SportFlowTest {
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

    private suspend fun snapshot(): HomeSnapshot {
        f.engine.refreshNow()
        return f.repo.snapshot.first()
    }

    /** Ustawia zegar na początek transmisji, wpisuje ją do EPG i ogląda [minutes] minut. */
    private suspend fun watchMatch(
        startMs: Long,
        title: String,
        minutes: Long = 25,
        channel: String = "ch_sport_1",
    ) {
        f.clock.now = startMs
        f.epg(f.slot(channel, startMs, title))
        f.watch(PlaybackTarget.Live(channel), minutes * 60)
    }

    private suspend fun state(teamId: String): FollowState? =
        f.sport
            .choices()
            .firstOrNull { it.team.id == teamId }
            ?.state

    private suspend fun threeSaturdays() {
        watchMatch(utc(3, 7, 16), "Liga Nadmorska: Wisła Nadmorska — Hutnik Leśny")
        watchMatch(utc(3, 14, 16), "Liga Nadmorska: Górnik Zmyślony - Wisła Nadmorska")
        watchMatch(utc(3, 21, 16), "Liga Nadmorska: Wisła Nadmorska vs Stal Próbna")
    }

    /** S3: po dwóch datach cisza; po trzeciej pytanie, ale nigdy samodzielne obserwowanie. */
    @Test
    fun sugestiaDopieroPoTrzechDatachINigdyBezPotwierdzenia() =
        runBlocking {
            watchMatch(utc(3, 7, 16), "Liga Nadmorska: Wisła Nadmorska — Hutnik Leśny")
            assertNull("jeden mecz to nie kibicowanie", snapshot().suggestion)
            watchMatch(utc(3, 14, 16), "Liga Nadmorska: Górnik Zmyślony - Wisła Nadmorska")
            assertNull(snapshot().suggestion)
            assertNull(state("wisla-nadmorska"))

            watchMatch(utc(3, 21, 16), "Liga Nadmorska: Wisła Nadmorska vs Stal Próbna")
            val suggestion = snapshot().suggestion
            assertNotNull(suggestion)
            assertEquals(listOf("Wisła Nadmorska"), suggestion!!.teams.map { it.name })
            assertEquals(listOf("7.03", "14.03", "21.03"), suggestion.watchedDatesUtcMs.map { WarsawTime.dayMonth(it) })
            assertEquals("to tylko pytanie", FollowState.SUGGESTED, state("wisla-nadmorska"))
            assertTrue("nikt nie jest obserwowany bez potwierdzenia", f.sport.followedIds().isEmpty())
            assertTrue(
                "bez obserwowanej drużyny nie ma kart meczów",
                f.repo.snapshot
                    .first()
                    .sport
                    .isEmpty(),
            )
        }

    @Test
    fun krotkieZajrzenieNaMeczNieJestDowodem() =
        runBlocking {
            watchMatch(utc(3, 7, 16), "Wisła Nadmorska - Hutnik Leśny", minutes = 4)
            watchMatch(utc(3, 14, 16), "Wisła Nadmorska - Górnik Zmyślony", minutes = 4)
            watchMatch(utc(3, 21, 16), "Wisła Nadmorska - Stal Próbna", minutes = 4)
            assertNull(snapshot().suggestion)
        }

    @Test
    fun zawszeTaSamaParaToPytanieOObieDruzyny() =
        runBlocking {
            val saturdays = listOf(utc(3, 7, 16), utc(3, 14, 16), utc(3, 21, 16))
            saturdays.forEach { watchMatch(it, "Wisła Nadmorska - Hutnik Leśny") }
            val teams =
                snapshot()
                    .suggestion
                    ?.teams
                    ?.map { it.id }
                    ?.toSet()
            assertEquals(setOf("wisla-nadmorska", "hutnik-lesny"), teams)
            assertTrue(f.sport.followedIds().isEmpty())
        }

    /** S4: derby bez nazw drużyn nie są dowodem dla żadnej strony. */
    @Test
    fun niejednoznaczneDerbyNieTworzaSugestii() =
        runBlocking {
            listOf(utc(3, 7, 16), utc(3, 14, 16), utc(3, 21, 16)).forEach { watchMatch(it, "Derby Trójmiasta") }
            assertNull(snapshot().suggestion)
            assertTrue(f.sportDao.follows("household").isEmpty())
        }

    @Test
    fun odrzuconaSugestiaMilknie() =
        runBlocking {
            threeSaturdays()
            assertNotNull(snapshot().suggestion)
            f.repo.reject(listOf("wisla-nadmorska"))
            assertNull(
                f.repo.snapshot
                    .first()
                    .suggestion,
            )
            assertEquals(FollowState.REJECTED, state("wisla-nadmorska"))
            watchMatch(utc(3, 28, 16), "Wisła Nadmorska - Hutnik Leśny")
            assertNull("kolejny mecz nie wznawia pytania w okresie karencji", snapshot().suggestion)
        }

    /** S3 cd.: po potwierdzeniu karta najbliższego meczu z godziną w Warszawie i kanałem z listy. */
    @Test
    fun potwierdzonaDruzynaDajeKarteMeczuZKanalemZListy() =
        runBlocking {
            threeSaturdays()
            f.clock.now = utc(3, 22, 9)
            f.repo.follow("wisla-nadmorska")
            f.epg(f.slot("ch_sport_1", utc(3, 27, 19), "Liga Nadmorska: Hutnik Leśny - Wisła Nadmorska"))
            val snap = snapshot()
            assertNull("obserwowana drużyna nie jest już pytaniem", snap.suggestion)
            val card = snap.sport.single()
            assertEquals(utc(3, 27, 19), card.kickoffUtcMs)
            assertEquals("20:00", WarsawTime.hhmm(card.kickoffUtcMs))
            assertEquals(listOf("Sport Testowy 1"), card.channels.map { it.channelName })
        }

    @Test
    fun recznieWybranaDruzynaNieWymagaHistorii() =
        runBlocking {
            f.clock.now = utc(3, 1, 9)
            f.repo.follow("hutnik-lesny")
            f.epg(f.slot("ch_sport_2", utc(3, 3, 17), "Hutnik Leśny - Stal Próbna"))
            assertEquals(
                listOf("Sport Testowy 2"),
                snapshot()
                    .sport
                    .single()
                    .channels
                    .map { it.channelName },
            )
        }

    @Test
    fun wlasnaDruzynaUzytkownikaJestRozpoznawanaWEpg() =
        runBlocking {
            f.clock.now = utc(3, 1, 9)
            val team = f.repo.addTeam("Błękitni Zmyśleni")
            assertNotNull(team)
            f.epg(f.slot("ch_sport_1", utc(3, 2, 17), "BŁĘKITNI ZMYŚLENI - Zespół Spoza Słownika"))
            assertEquals(1, snapshot().sport.size)
            f.repo.removeTeam(team!!.id)
            assertTrue(
                f.repo.snapshot
                    .first()
                    .sport
                    .isEmpty(),
            )
        }

    /** S6: ten sam mecz na dwóch kanałach to jedna karta z dwoma kanałami. */
    @Test
    fun tenSamMeczNaDwochKanalachToJednaKarta() =
        runBlocking {
            f.clock.now = utc(3, 1, 9)
            f.repo.follow("wisla-nadmorska")
            val kickoff = utc(3, 2, 17)
            f.epg(
                f.slot("ch_sport_1", kickoff, "Wisła Nadmorska - Hutnik Leśny"),
                f.slot("ch_sport_2", kickoff, "Liga Nadmorska: Wisła Nadmorska — Hutnik Leśny"),
                f.slot("ch_info", kickoff, "Wiadomości"),
            )
            val card = snapshot().sport.single()
            assertEquals(listOf("Sport Testowy 1", "Sport Testowy 2"), card.channels.map { it.channelName }.sorted())
        }

    /** Kanału nie ma już na liście: karta zostaje, ale bez wskazania kanału („kanał niepotwierdzony”). */
    @Test
    fun kanalUsunietyZListyDajeKarteBezKanalu() =
        runBlocking {
            f.clock.now = utc(3, 1, 9)
            f.repo.follow("wisla-nadmorska")
            f.epg(f.slot("ch_sport_1", utc(3, 2, 17), "Wisła Nadmorska - Hutnik Leśny"))
            assertEquals(
                1,
                snapshot()
                    .sport
                    .single()
                    .channels.size,
            )
            f.db.channels().deleteByProvider(PersonalizationFixture.PROVIDER)
            val card = snapshot().sport.single()
            assertTrue(card.channels.isEmpty())
        }

    @Test
    fun ukrytyKanalNieJestWskazywany() =
        runBlocking {
            f.clock.now = utc(3, 1, 9)
            f.repo.follow("wisla-nadmorska")
            f.db.channelFlags().upsert(
                pl.ketchupplayer.tv.core.database
                    .ChannelFlagEntity("ch_sport_1", hidden = true),
            )
            f.epg(f.slot("ch_sport_1", utc(3, 2, 17), "Wisła Nadmorska - Hutnik Leśny"))
            assertTrue(
                snapshot()
                    .sport
                    .single()
                    .channels
                    .isEmpty(),
            )
        }

    /** Po podmianie EPG karta zmienia godzinę albo znika; stary termin nie zostaje obok nowego. */
    @Test
    fun podmianaEpgZmieniaGodzineAlboUsuwaKarte() =
        runBlocking {
            f.clock.now = utc(3, 1, 9)
            f.repo.follow("wisla-nadmorska")
            f.epg(f.slot("ch_sport_1", utc(3, 2, 17), "Wisła Nadmorska - Hutnik Leśny"))
            assertEquals(utc(3, 2, 17), snapshot().sport.single().kickoffUtcMs)

            f.epg(f.slot("ch_sport_1", utc(3, 2, 19, 30), "Wisła Nadmorska - Hutnik Leśny"))
            assertEquals(listOf(utc(3, 2, 19, 30)), snapshot().sport.map { it.kickoffUtcMs })

            f.epg(f.slot("ch_sport_1", utc(3, 9, 17), "Wisła Nadmorska - Hutnik Leśny"))
            val moved = snapshot().sport.map { it.kickoffUtcMs }
            assertEquals("mecz przeniesiony na inny dzień", listOf(utc(3, 9, 17)), moved)

            f.epg(f.slot("ch_info", utc(3, 2, 17), "Wiadomości"))
            assertTrue(snapshot().sport.isEmpty())
        }

    @Test
    fun godzinaMeczuWNocZmianyCzasuJestWarszawska() =
        runBlocking {
            f.clock.now = utc(3, 28, 9)
            f.repo.follow("wisla-nadmorska")
            f.epg(
                f.slot("ch_sport_1", utc(3, 28, 19), "Wisła Nadmorska - Hutnik Leśny"),
                f.slot("ch_sport_1", utc(3, 29, 19), "Wisła Nadmorska - Stal Próbna"),
            )
            val cards = snapshot().sport
            assertEquals(listOf("20:00", "21:00"), cards.map { WarsawTime.hhmm(it.kickoffUtcMs) })
            assertEquals("w bazie zostaje UTC", listOf(utc(3, 28, 19), utc(3, 29, 19)), cards.map { it.kickoffUtcMs })
        }

    @Test
    fun wzorzecWstepnegoFiltruObejmujeZnakiDiakrytyczne() {
        // Litery, które w polskim miewają ogonki i kreski (a, c, e, l, n, o, s, z), pasują do dowolnego znaku.
        assertEquals("%wi___%", SportStore.likePattern("wisla"))
        assertEquals("%____k%", SportStore.likePattern("slask"))
        assertEquals("najdłuższe słowo aliasu", "%hut_ik%", SportStore.likePattern("hutnik lesny"))
        assertNull("za krótki alias niczego nie zawęża", SportStore.likePattern("lks"))
    }
}
