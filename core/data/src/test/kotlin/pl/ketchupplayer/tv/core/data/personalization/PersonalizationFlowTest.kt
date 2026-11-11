package pl.ketchupplayer.tv.core.data.personalization

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
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationFixture.Companion.FILM_MS
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationFixture.Companion.MIN
import pl.ketchupplayer.tv.core.database.VodFeatureEntity
import pl.ketchupplayer.tv.core.database.VodStateEntity
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.HomeSnapshot

/**
 * Personalizacja VOD od odtwarzacza do pasa Home na prawdziwej bazie Room: zgoda, sesje, profil, ranking, oceny,
 * prywatność, eksport i czyszczenie (konspekt: punkty 16.2, 16.3, scenariusze S1, S2, S5). Dane zmyślone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalizationFlowTest {
    private lateinit var f: PersonalizationFixture

    @Before
    fun setUp() =
        runBlocking {
            f = PersonalizationFixture()
            f.seedCatalog()
        }

    @After
    fun tearDown() = f.close()

    private fun film(id: String) = PlaybackTarget.Vod(id, resumed = false)

    private suspend fun snapshot(): HomeSnapshot {
        f.engine.refreshNow()
        return f.repo.snapshot.first()
    }

    private suspend fun watchFilm(
        id: String,
        minutes: Long = 95,
    ) = f.watch(film(id), minutes * 60, FILM_MS)

    @Test
    fun domyslnieWylaczonaIBezZgodyNicNieJestZapisywane() =
        runBlocking {
            assertFalse(PersonalizationPrefs().enabled)
            watchFilm("film-a")
            watchFilm("film-b")
            assertEquals(0, f.dao.sessionCount())
            assertTrue(f.dao.features("household").isEmpty())
            assertEquals(HomeSnapshot(), snapshot())
        }

    @Test
    fun trybNieUczSieNieZapisujeSesjiINieZmieniaWag() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            val before = f.weight("GENRE:crime")
            f.settings.setIncognito(true)
            watchFilm("film-b")
            assertEquals(1, f.dao.sessionCount())
            assertEquals(before, f.weight("GENRE:crime"), 0.0)
        }

    /** S1: dwa dokończone kryminały jednej reżyserki, krótkie wejście w komedię. */
    @Test
    fun dwaDokonczoneFilmyDajaPasZPrawdziwymPowodemAKrotkieWejscieNieLiczySie() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            watchFilm("film-b")
            f.watch(film("film-c"), 40, FILM_MS)

            assertEquals("dwie obserwacje (z minimalnym zanikiem)", 2.0, f.evidence("DIRECTOR:anna testowa"), 0.01)
            assertEquals("40 s komedii nie zostawia śladu", 0.0, f.weight("GENRE:comedy"), 0.0)
            val because = snapshot().because
            val ids = because.map { it.itemId }
            assertEquals("najpierw film tej samej reżyserki", "film-d", ids.first())
            assertTrue("film-e" in ids)
            assertFalse("obejrzane filmy nie wracają", "film-a" in ids || "film-b" in ids)
            assertFalse("komedie nie są proponowane", "film-c" in ids || "film-f" in ids)
            assertFalse("pozycja bez metadanych nie jest proponowana z niczego", "film-g" in ids)
            val why = because.first().why
            assertTrue(why.any { it.namespace == FeatureNamespace.DIRECTOR && it.label == "Anna Testowa" })
            assertTrue(because.single { it.itemId == "film-e" }.why.none { it.namespace == FeatureNamespace.DIRECTOR })
        }

    @Test
    fun zimnyStartJedenFilmToZaMaloAPolubienieWystarcza() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            assertTrue("jeden seans: brak pasa zamiast udawanych rekomendacji", snapshot().because.isEmpty())
            f.repo.setLiked("film-e", true)
            val because =
                f.repo.snapshot
                    .first()
                    .because
            assertTrue(because.isNotEmpty())
            assertEquals(setOf("film-e"), f.repo.liked.first())
        }

    @Test
    fun polubienieLiczySieRazACofniecieJeOdejmuje() =
        runBlocking {
            f.settings.setEnabled(true)
            f.repo.setLiked("film-e", true)
            val once = f.weight("GENRE:thriller")
            assertTrue(once > 0.0)
            f.repo.setLiked("film-e", true)
            assertEquals("ponowne polubienie niczego nie dolicza", once, f.weight("GENRE:thriller"), 1e-9)
            f.repo.setLiked("film-e", false)
            assertEquals(0.0, f.weight("GENRE:thriller"), 1e-9)
        }

    @Test
    fun ukrytaPozycjaZnikaZPasaBezKaraniaGatunku() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            watchFilm("film-b")
            val crime = f.weight("GENRE:crime")
            f.repo.hide("film-d")
            val ids =
                f.repo.snapshot
                    .first()
                    .because
                    .map { it.itemId }
            assertFalse("film-d" in ids)
            assertTrue("film-e" in ids)
            assertEquals(crime, f.weight("GENRE:crime"), 1e-9)
        }

    /** Kontrfakt 5: wpis indeksu dla pozycji, której nie ma w katalogu, nigdy nie trafia do pasa. */
    @Test
    fun pozycjaSpozaKataloguNigdyNieTrafiaDoPasa() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            watchFilm("film-b")
            f.engine.refreshNow()
            f.dao.putVodFeatures(
                listOf(
                    VodFeatureEntity(GHOST, "DIRECTOR", "anna testowa", "Anna Testowa", 1.0, "TMDB", 0.9, 1),
                    VodFeatureEntity(GHOST, "GENRE", "crime", "Kryminał", 1.0, "TMDB", 0.9, 1),
                ),
            )
            val ids = snapshot().because.map { it.itemId }
            assertTrue(ids.isNotEmpty())
            assertFalse(GHOST in ids)
        }

    @Test
    fun pozycjeZMojejListyIZaczeteNieSaPowtarzaneWPasie() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            watchFilm("film-b")
            f.db.vod().upsertState(VodStateEntity("film-d", myList = true))
            val ids = snapshot().because.map { it.itemId }
            assertFalse("film-d" in ids)
            assertTrue("film-e" in ids)
        }

    @Test
    fun pauzaIBuforowanieNieSaOgladaniem() =
        runBlocking {
            f.settings.setEnabled(true)
            val t = f.recorder.open(film("film-a"))
            f.play(t, 5 * 60, FILM_MS)
            f.idle(t, 30 * 60, positionMs = 5 * MIN, buffering = false)
            f.idle(t, 2 * 60, positionMs = 5 * MIN, buffering = true)
            f.play(t, 5 * 60, FILM_MS, fromPositionMs = 5 * MIN)
            t.close()
            f.recorder.awaitIdle()
            val s = f.dao.recentSessions("household", 1).single()
            assertEquals(10 * MIN, s.activeWatchMs)
            assertEquals(30 * MIN, s.pauseMs)
            assertEquals(2 * MIN, s.bufferMs)
            assertEquals("POSITIVE", s.evidenceClass)
        }

    /** Kontrfakt 3: skok na napisy końcowe nie robi z dwóch minut „obejrzanego filmu”. */
    @Test
    fun przewiniecieNaKoniecNieJestUkonczeniem() =
        runBlocking {
            f.settings.setEnabled(true)
            val t = f.recorder.open(film("film-a"))
            f.play(t, 120, FILM_MS)
            f.play(t, 30, FILM_MS, fromPositionMs = FILM_MS - 30_000)
            t.close()
            f.recorder.awaitIdle()
            val s = f.dao.recentSessions("household", 1).single()
            assertEquals(150_000, s.activeWatchMs)
            assertEquals(1, s.seekCount)
            assertEquals(FILM_MS, s.maxPositionMs)
            assertEquals("za krótko jak na film: bez wpływu na profil", 0.0, s.appliedStrength, 0.0)
            assertEquals(0.0, f.weight("GENRE:crime"), 0.0)
        }

    /** S5: błąd po kilku sekundach buforowania to nieznana awaria, nie ocena treści. */
    @Test
    fun bladOdtwarzaniaNieZmieniaWag() =
        runBlocking {
            f.settings.setEnabled(true)
            val t = f.recorder.open(film("film-a"))
            f.idle(t, 3, positionMs = 0, buffering = true)
            f.error(t)
            t.close()
            f.recorder.awaitIdle()
            val s = f.dao.recentSessions("household", 1).single()
            assertEquals("UNKNOWN_FAILURE", s.evidenceClass)
            assertEquals("ERROR", s.endReason)
            assertTrue(f.dao.features("household").isEmpty())
        }

    /** Wznowienie w ciągu pół godziny to ta sama sesja: jeden wiersz, jedna obserwacja, waga jak za całość. */
    @Test
    fun wznowienieTegoSamegoFilmuNieLiczySieDrugiRaz() =
        runBlocking {
            f.settings.setEnabled(true)
            f.watch(film("film-a"), 40 * 60, FILM_MS)
            val afterFirst = f.weight("GENRE:crime")
            f.clock.advance(20 * MIN)
            f.watch(PlaybackTarget.Vod("film-a", resumed = true), 55 * 60, FILM_MS, fromPositionMs = 40 * MIN)
            assertEquals(1, f.dao.sessionCount())
            assertEquals(
                95 * MIN,
                f.dao
                    .recentSessions("household", 1)
                    .single()
                    .activeWatchMs,
            )
            assertEquals("jedna obserwacja mimo dwóch podejść", 1.0, f.evidence("GENRE:crime"), 0.01)
            assertTrue(f.weight("GENRE:crime") > afterFirst)

            val whole = PersonalizationFixture()
            try {
                whole.seedCatalog()
                whole.settings.setEnabled(true)
                whole.watch(film("film-a"), 95 * 60, FILM_MS)
                assertEquals("waga jak za jeden cały seans", whole.weight("GENRE:crime"), f.weight("GENRE:crime"), 0.01)
            } finally {
                whole.close()
            }
        }

    /** Kontrfakt 4: po śmierci procesu zostaje ostatni zapisany czas, a dalsze oglądanie nie dubluje wagi. */
    @Test
    fun smiercProcesuZostawiaOstatniZapisINieDublujeWagi() =
        runBlocking {
            f.settings.setEnabled(true)
            val t = f.recorder.open(film("film-a"))
            f.play(t, 10 * 60, FILM_MS)
            Thread.sleep(SETTLE_MS) // kolejka zdarzeń zdąży zapisać ostatni checkpoint
            f.recorder.invalidate() // proces ginie: nikt nie zamyka sesji
            val open = f.dao.openSessions().single()
            assertTrue("zapis co 15 s: stracone najwyżej kilkanaście sekund", open.activeWatchMs >= 9 * MIN + 30_000)

            val next = f.restarted()
            next.engine.maintain()
            val closed = next.dao.recentSessions("household", 1).single()
            assertEquals("PROCESS_DEATH", closed.endReason)
            assertEquals("nic nie jest doliczane z pozycji", open.activeWatchMs, closed.activeWatchMs)
            assertTrue(closed.appliedStrength > 0.0)
            val afterDeath = next.weight("GENRE:crime")

            next.clock.advance(5 * MIN)
            next.watch(PlaybackTarget.Vod("film-a", resumed = true), 85 * 60, FILM_MS, fromPositionMs = 10 * MIN)
            assertEquals(1, next.dao.sessionCount())
            assertEquals(1.0, next.evidence("GENRE:crime"), 0.01)
            assertTrue(next.weight("GENRE:crime") > afterDeath)
            next.engine.cancelPending()
        }

    /** S2: maraton serialu ma dzienny sufit, a serial już oglądany nie jest „rekomendowany” (bez spoilerów). */
    @Test
    fun maratonSerialuMaSufitDziennyISerialNieWracaJakoRekomendacja() =
        runBlocking {
            f.settings.setEnabled(true)
            (1..6).forEach { n ->
                f.watch(PlaybackTarget.Vod("serial-port:e$n", resumed = false), 48 * 60, 50 * MIN)
            }
            val sessions = f.dao.recentSessions("household", 10)
            assertEquals(6, sessions.size)
            assertTrue(sessions.all { it.seriesId == "serial-port" && it.contentKind == "EPISODE" })
            val withoutEffect = sessions.count { it.appliedStrength == 0.0 }
            assertEquals("po wyczerpaniu 150 minut kolejne odcinki nic nie dodają", 2, withoutEffect)
            watchFilm("film-a")
            watchFilm("film-b")
            val ids = snapshot().because.map { it.itemId }
            assertFalse(ids.any { it.startsWith("serial-port") })
        }

    @Test
    fun retencjaUsuwaStareSesjeAZostawiaNowe() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            f.clock.advance(100L * 24 * 60 * MIN)
            watchFilm("film-b")
            f.repo.setRetentionDays(90)
            val left = f.dao.recentSessions("household", 10)
            assertEquals(listOf("film-b"), left.map { it.itemId })
        }

    /** W żadnej tabeli personalizacji ani w eksporcie nie ma adresów listy, loginu ani hasła. */
    @Test
    fun brakAdresowIDanychDostepuWTabelachIEksporcie() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            watchFilm("film-b")
            f.repo.setLiked("film-e", true)
            f.engine.refreshNow()
            val everything = f.dumpLearned() + "\n" + f.repo.export()
            assertTrue(everything.contains("film-a"))
            val secrets = listOf(PersonalizationFixture.SECRET_USER, PersonalizationFixture.SECRET_PASS)
            (secrets + listOf("http", "get.php", "password")).forEach {
                assertFalse("wyciek: $it", everything.contains(it, ignoreCase = true))
            }
        }

    @Test
    fun eksportZawieraSesjePreferencjeIOceny() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            f.repo.setLiked("film-e", true)
            val json = f.repo.export()
            assertTrue(json.contains("\"format\":\"ketchup-personalization-export\""))
            assertTrue(json.contains("\"itemId\":\"film-a\""))
            assertTrue(json.contains("\"feedback\":\"LIKE\""))
            assertTrue(json.contains("\"key\":\"crime\""))
        }

    @Test
    fun czyszczenieUsuwaNaukeAleNieKatalogMojaListeIPostep() =
        runBlocking {
            f.settings.setEnabled(true)
            f.db.vod().upsertState(VodStateEntity("film-f", myList = true, positionMs = 5 * MIN, durationMs = FILM_MS))
            watchFilm("film-a")
            watchFilm("film-b")
            f.repo.setLiked("film-e", true)
            f.repo.follow("wisla-nadmorska")
            f.engine.refreshNow()
            assertTrue(
                f.repo.snapshot
                    .first()
                    .because
                    .isNotEmpty(),
            )

            f.repo.clear()

            assertEquals(0, f.dao.sessionCount())
            assertTrue(f.dao.features("household").isEmpty())
            assertTrue(f.dao.feedback("household").isEmpty())
            assertTrue(f.sportDao.follows("household").isEmpty())
            assertEquals(HomeSnapshot(), f.repo.snapshot.first())
            assertNotNull("katalog zostaje", f.db.vod().get("film-a"))
            assertNotNull(f.db.channels().get("ch_sport_1"))
            val state = f.db.vod().state("film-f")
            assertEquals("Moja lista zostaje", true, state?.myList)
            assertEquals("postęp oglądania zostaje", 5 * MIN, state?.positionMs)
        }

    @Test
    fun wylaczenieZgodyChowaPasyAleNieKasujeDanych() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            watchFilm("film-b")
            assertTrue(snapshot().because.isNotEmpty())
            f.repo.setEnabled(false)
            assertEquals(HomeSnapshot(), f.repo.snapshot.first())
            assertEquals(2, f.dao.sessionCount())
            watchFilm("film-d")
            assertEquals("bez zgody nowe sesje nie powstają", 2, f.dao.sessionCount())
        }

    @Test
    fun diagnostykaPodajeLiczbyBezTresci() =
        runBlocking {
            f.settings.setEnabled(true)
            watchFilm("film-a")
            f.watch(film("film-c"), 40, FILM_MS)
            f.engine.refreshNow()
            val d = f.repo.diagnostics()
            assertEquals(2, d.sessions)
            assertEquals(1, d.positive)
            assertEquals(1, d.neutral)
            assertTrue(d.features > 0)
            assertNotNull(d.snapshotBuiltAtMs)
            assertNull(d.lastError)
        }

    private companion object {
        const val SETTLE_MS = 800L
        const val GHOST = "duch-spoza-listy"
    }
}
