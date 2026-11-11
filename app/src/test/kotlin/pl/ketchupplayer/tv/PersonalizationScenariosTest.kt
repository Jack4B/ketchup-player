package pl.ketchupplayer.tv

import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.data.appearance.AppearanceRepository
import pl.ketchupplayer.tv.core.data.epg.EpgSourcesRepository
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationRepository
import pl.ketchupplayer.tv.core.data.personalization.SessionRecorder
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.designsystem.ThemeId
import pl.ketchupplayer.tv.core.designsystem.UiScale
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Personalizacja end-to-end: pełny graf Hilt, prawdziwy Room, serwer HTTP udający dostawcę, prawdziwe ekrany.
 * Podmieniony jest tylko odtwarzacz i zegar (czas „oglądania” przesuwa test, żeby 95 minut filmu nie trwało 95 minut).
 * Katalog i audycje są zmyślone; nazwy klubów w scenariuszu sportowym pochodzą z wbudowanego słownika nazw, a mecz
 * i godzina są fikcyjne.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w960dp-h540dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class PersonalizationScenariosTest {
    @get:Rule(order = -1)
    val tvBox = TvBoxRule()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var db: KetchupDatabase

    @Inject lateinit var personalization: PersonalizationRepository

    @Inject lateinit var recorder: SessionRecorder

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Inject lateinit var appearance: AppearanceRepository

    @Inject lateinit var epgSources: EpgSourcesRepository

    @Inject lateinit var onboarding: pl.ketchupplayer.tv.core.data.onboarding.OnboardingRepository

    private val server = ProviderServer()

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    private fun blockImageNetwork() {
        coil3.SingletonImageLoader.setUnsafe(
            coil3.ImageLoader
                .Builder(
                    androidx.test.core.app.ApplicationProvider
                        .getApplicationContext(),
                ).components {
                    add(
                        coil3.intercept.Interceptor { chain ->
                            coil3.request.ErrorResult(null, chain.request, IllegalStateException("offline w testach"))
                        },
                    )
                }.build(),
        )
    }

    @Before
    fun setUp() {
        hilt.inject()
        blockImageNetwork()
        FakePlayers.reset()
        FakePlayers.playingWhenReady = true
        FakePlayers.durationMs = FILM_MS
        TestClock.reset()
        runBlocking {
            dataStore.edit { it.clear() }
            dataStore.edit { it[booleanPreferencesKey("live_preview")] = false }
            appearance.setUiScale(UiScale.X100)
            appearance.setTheme(ThemeId.MORSKI)
            appearance.setGlassBlur(false)
            onboarding.setDone(true)
            epgSources.setEnabled(BuiltInEpgSources.OVH_ID, false)
            epgSources.setEnabled(BuiltInEpgSources.TELEMAN_ID, false)
            personalization.clear()
        }
        serveCatalog()
    }

    @After
    fun tearDown() {
        TestClock.reset()
        server.shutdown()
    }

    // ---- serwer dostawcy (dane zmyślone) ----

    private fun serveCatalog() {
        val ok =
            """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000",""" +
                """"active_cons":"1","max_connections":"2"}}"""
        server.serveRequest("/player_api.php") { ok }
        server.serve("/player_api.php?get_live_categories") { """[{"category_id":"1","category_name":"Sport"}]""" }
        server.serve("/player_api.php?get_live_streams") {
            """[{"num":1,"name":"Sport Testowy 1","stream_id":101,"epg_channel_id":"sport1.example",""" +
                """"category_id":"1"},""" +
                """{"num":2,"name":"Info Testowe","stream_id":102,"epg_channel_id":"","category_id":"1"}]"""
        }
        server.serve("/player_api.php?get_vod_categories") {
            """[{"category_id":"10","category_name":"PL | Kryminał"},{"category_id":"11","category_name":"Komedia"}]"""
        }
        server.serve("/player_api.php?get_vod_streams") {
            listOf(
                movie(501, "Cień nad zatoką", 10),
                movie(502, "Ostatni prom", 10),
                movie(503, "Mgła na molo", 10),
                movie(504, "Druga latarnia", 10),
                movie(505, "Wesele u sąsiadów", 11),
                movie(506, "Sezon ogórkowy", 11),
            ).joinToString(",", "[", "]")
        }
        server.serve("/player_api.php?get_series_categories") { "[]" }
        server.serve("/player_api.php?get_series") { "[]" }
    }

    private fun movie(
        id: Int,
        name: String,
        category: Int,
    ) = """{"stream_id":$id,"name":"$name","rating":"7.1","category_id":"$category","container_extension":"mkv"}"""

    /** XMLTV z fikcyjnym meczem na kanale `sport1.example`, który zaczyna się za [inMinutes] minut. */
    private fun serveMatch(
        title: String,
        inMinutes: Long,
    ) {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val start = TestClock.nowMs() + inMinutes * MIN
        val xml =
            """<?xml version="1.0" encoding="UTF-8"?><tv>""" +
                """<channel id="sport1.example"><display-name>Sport Testowy 1</display-name></channel>""" +
                """<programme start="${fmt.format(Date(start))} +0000" """ +
                """stop="${fmt.format(Date(start + 120 * MIN))} +0000" channel="sport1.example">""" +
                """<title lang="pl">$title</title><category>Sport</category></programme></tv>"""
        server.serve("/xmltv.php") { xml }
    }

    // ---- sterowanie ekranem ----

    private fun waitFor(
        timeoutMs: Long = WAIT_MS,
        condition: () -> Boolean,
    ) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(CLOCK_STEP_MS))
            compose.waitForIdle()
            check(System.nanoTime() < end) {
                "Warunek niespełniony po $timeoutMs ms. Ekran:\n" + compose.onRoot().printToString(maxDepth = 14)
            }
            Thread.sleep(POLL_MS)
        }
    }

    private fun present(
        text: String,
        substring: Boolean = false,
    ) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(
        text: String,
        substring: Boolean = false,
    ) = waitFor { present(text, substring) }

    private fun clickableNodes(text: String) = compose.onAllNodesWithText(text).filter(hasClickAction())

    private fun click(text: String) {
        waitFor { clickableNodes(text).fetchSemanticsNodes().isNotEmpty() }
        clickableNodes(text)[0].performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Home to leniwa lista: rząd poniżej ekranu trzeba najpierw przewinąć w pole widzenia (pilot robi to fokusem). */
    private fun scrollHomeTo(text: String) {
        waitFor(BIG_WAIT_MS) {
            runCatching {
                compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text))
            }.isSuccess
        }
        compose.waitForIdle()
    }

    private fun nav(label: String) {
        waitFor { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(label).performClick()
        compose.waitForIdle()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun addProvider() {
        nav("Ustawienia")
        click("Listy i dostawcy")
        click("Dodaj dostawcę")
        waitForText("Zapisz i pobierz")
        click("Xtream Codes")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Lista testowa")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(server.baseUrl())
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("jan")
        compose.onAllNodes(hasSetTextAction())[3].performTextInput("haslo-testowe")
        compose.onNodeWithText("Zapisz i pobierz").performScrollTo().performClick()
        waitForText("Kanały: 2", substring = true)
    }

    private fun enablePersonalization() {
        nav("Ustawienia")
        click("Personalizacja")
        waitForText("Wyłączone: aplikacja nie zapisuje historii oglądania.")
        click("Ucz się, co oglądam")
        waitForText("Włączone: liczy się tylko czas faktycznego oglądania.")
    }

    /** Otwiera film z siatki, gra [minutes] minut (zegar i pozycja przesuwane krokami po minucie) i wychodzi. */
    private fun watchMovie(
        title: String,
        minutes: Int,
    ) {
        nav("Filmy")
        click(title)
        waitForText("FILM")
        val before = FakePlayers.played.size
        click("▶ Oglądaj")
        waitFor { FakePlayers.played.size > before }
        compose.waitForIdle()
        repeat(minutes) {
            TestClock.advance(MIN)
            FakePlayers.advance(MIN)
            compose.waitForIdle()
        }
        back() // z odtwarzacza do szczegółów: sesja się zamyka
        waitFor { FakePlayers.live.isEmpty() }
        runBlocking { recorder.awaitIdle() }
        back()
    }

    /** Stan nauki do komunikatu błędu (same liczby i klucze cech, bez adresów). */
    private fun learnedState(): String =
        runBlocking {
            val dao = db.personalization()
            val features = dao.features("household").map { "${it.namespace}:${it.featureKey}=${it.decayedWeight}" }
            val index = dao.featureCounts().map { "${it.namespace}:${it.featureKey}x${it.items}" }
            "diag=${personalization.diagnostics()} cechy=$features indeks=$index wykluczone=" +
                dao.excludedVodItems("household") + " sesje=" + sessions().map { it.itemId to it.appliedStrength }
        }

    private fun sessions() = runBlocking { db.personalization().recentSessions("household", 50) }

    // ---- scenariusze ----

    /** Bez zgody oglądanie nie zostawia żadnego śladu, a Home nie udaje rekomendacji. */
    @Test
    fun bezZgodyOgladanieNieZostawiaSladuAHomeNieMaPasow() {
        addProvider()
        watchMovie("Cień nad zatoką", 95)
        watchMovie("Ostatni prom", 95)
        assertTrue("bez zgody nie powstaje żadna sesja", sessions().isEmpty())
        nav("Home")
        waitForText("Mam czas na", substring = true)
        assertFalse(present("Bo oglądasz"))
        assertFalse(present("Twoje drużyny"))
        nav("Ustawienia")
        click("Personalizacja")
        waitForText("Wyłączone: aplikacja nie zapisuje historii oglądania.")
        assertFalse("bez zgody nie ma przełącznika trybu „nie ucz się”", present("Nie ucz się z tej sesji"))
    }

    /** S1 na pełnej aplikacji: zgoda z Ustawień, dwa dokończone kryminały, krótkie wejście w komedię, pas na Home. */
    @Test
    fun poZgodzieDwaObejrzaneFilmyDajaPasBoOgladaszZPowodemIOcena() {
        addProvider()
        enablePersonalization()
        watchMovie("Cień nad zatoką", 95)
        watchMovie("Ostatni prom", 95)
        watchMovie("Wesele u sąsiadów", 1)

        val all = sessions()
        assertEquals(3, all.size)
        val positive = all.filter { it.evidenceClass == "POSITIVE" }
        assertEquals("dwa seanse to oglądanie, krótkie wejście jest neutralne", 2, positive.size)
        assertTrue(positive.all { it.activeWatchMs >= 94 * MIN })
        assertTrue("żadnych adresów w sesjach", all.none { it.itemId.contains("http") || it.dedupKey.contains("http") })

        // Pas przelicza się sam po zamknięciu sesji (z kilkusekundowym opóźnieniem), bez udziału testu.
        runCatching {
            waitFor(BIG_WAIT_MS) { runBlocking { personalization.snapshot.first() }.because.isNotEmpty() }
        }.onFailure { error("Pas się nie przeliczył. " + learnedState()) }
        nav("Home")
        waitForText("Mam czas na", substring = true)
        scrollHomeTo("Bo oglądasz")
        waitForText("Mgła na molo")
        waitForText("Druga latarnia")
        waitForText("Gatunek: Kryminał")
        val recommended = runBlocking { personalization.snapshot.first() }.because.map { it.itemId }
        assertEquals("tylko pozostałe kryminały z listy", 2, recommended.size)
        assertTrue(
            "obejrzane filmy i komedie nie są polecane",
            recommended.all {
                it.endsWith("m503") ||
                    it.endsWith("m504")
            },
        )
        assertFalse("komedia po minucie nie jest polecana", present("Sezon ogórkowy"))
        val issues = LayoutAudit.run("Home z personalizacją", compose)
        assertTrue("Problemy układu:\n" + issues.joinToString("\n"), issues.isEmpty())

        // Ocena z przytrzymania: „Nie pokazuj tego” usuwa kartę, reszta pasa zostaje.
        clickableNodes("Mgła na molo")[0].performScrollTo().performTouchInput { longClick() }
        waitForText("Nie pokazuj tego")
        waitForText("Dlaczego to widzę: Gatunek: Kryminał")
        click("Nie pokazuj tego")
        waitFor(BIG_WAIT_MS) { !present("Mgła na molo") }
        assertTrue(present("Druga latarnia"))

        // OK na karcie otwiera szczegóły tej pozycji; tam jest „Lubię to”.
        click("Druga latarnia")
        waitForText("FILM")
        click("♡ Lubię to")
        waitForText("♥ Lubisz to")
        val feedback = runBlocking { db.personalization().feedback("household") }.map { it.feedback }.toSet()
        assertEquals(setOf("HIDE", "LIKE"), feedback)
    }

    /** Tryb „nie ucz się z tej sesji”: obejrzany film nie zostawia sesji; po wyłączeniu trybu znów się liczy. */
    @Test
    fun trybNieUczSieWstrzymujeNaukeAWyczyszczenieKasujeHistorie() {
        addProvider()
        enablePersonalization()
        click("Nie ucz się z tej sesji")
        watchMovie("Cień nad zatoką", 95)
        assertTrue(sessions().isEmpty())
        nav("Ustawienia")
        click("Personalizacja")
        click("Nie ucz się z tej sesji")
        watchMovie("Ostatni prom", 95)
        assertEquals(1, sessions().size)

        nav("Ustawienia")
        click("Personalizacja")
        click("Wyczyść dane personalizacji")
        waitForText("Usunąć historię oglądania, oceny i drużyny?")
        click("Tak, usuń")
        waitForText("Dane personalizacji usunięte.")
        assertTrue(sessions().isEmpty())
        nav("Filmy")
        waitForText("Cień nad zatoką") // katalog został
    }

    /** Sport: ręcznie wybrana drużyna, mecz z EPG na kanale z listy, karta na Home otwiera ten kanał. */
    @Test
    fun obserwowanaDruzynaDajeKarteMeczuKtoraOtwieraKanalZListy() {
        serveMatch("Liga Testowa: Arka Gdynia - Lechia Gdańsk", inMinutes = 90)
        addProvider()
        enablePersonalization()
        click("Dodaj drużynę z listy")
        waitForText("Wybierz drużynę")
        click("Arka Gdynia")
        waitForText("Obserwowana · OK: przestań obserwować")
        waitFor(BIG_WAIT_MS) { runBlocking { db.epg().count() } > 0 }
        runBlocking { personalization.refresh() }

        nav("Home")
        waitFor(BIG_WAIT_MS) { present("Twoje drużyny") }
        waitForText("Liga Testowa: Arka Gdynia - Lechia Gdańsk")
        waitForText("Sport Testowy 1")
        assertFalse("kanał jest potwierdzony przez EPG", present("Kanał niepotwierdzony", substring = true))
        val issues = LayoutAudit.run("Home z meczem", compose)
        assertTrue("Problemy układu:\n" + issues.joinToString("\n"), issues.isEmpty())
        click("Liga Testowa: Arka Gdynia - Lechia Gdańsk")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertTrue("gra kanał z listy użytkownika", FakePlayers.played.last().contains("/101"))
    }

    /** Nikt nie jest obserwowany bez potwierdzenia: sam mecz w EPG i oglądanie kanału niczego nie dodają. */
    @Test
    fun meczWEpgBezObserwowanejDruzynyNieTworzyKartAniObserwowania() {
        serveMatch("Liga Testowa: Legia Warszawa - Lech Poznań", inMinutes = -5)
        addProvider()
        enablePersonalization()
        waitFor(BIG_WAIT_MS) { runBlocking { db.epg().count() } > 0 }
        nav("Na żywo")
        click("Sport Testowy 1")
        waitFor { FakePlayers.played.isNotEmpty() }
        repeat(30) {
            TestClock.advance(MIN)
            FakePlayers.advance(MIN) // zmiana stanu odtwarzacza = próbka dla obserwatora
            compose.waitForIdle()
        }
        back()
        waitFor { FakePlayers.live.isEmpty() }
        runBlocking {
            recorder.awaitIdle()
            personalization.refresh()
        }
        val live = sessions().filter { it.contentKind == "LIVE" }
        assertTrue("sesja kanału powstała", live.isNotEmpty())
        assertTrue("z tytułem audycji, bez adresów", live.all { it.programmeTitleNorm?.contains("legia") == true })
        assertTrue(live.any { it.activeWatchMs >= 29 * MIN && it.evidenceClass == "POSITIVE" })
        val follows = runBlocking { db.sport().follows("household") }
        assertTrue("po jednym meczu nikt nie jest obserwowany ani proponowany", follows.isEmpty())
        nav("Home")
        waitForText("Mam czas na", substring = true)
        assertFalse(present("Twoje drużyny"))
        assertFalse(present("Obserwować drużynę?"))
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val BIG_WAIT_MS = 60_000L
        const val POLL_MS = 25L
        const val CLOCK_STEP_MS = 500L
        const val MIN = 60_000L
        const val FILM_MS = 100 * MIN
    }
}
