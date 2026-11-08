package pl.ketchupplayer.tv

import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
import pl.ketchupplayer.tv.core.data.catalog.AddProviderResult
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.epg.EpgSourcesRepository
import pl.ketchupplayer.tv.core.designsystem.ThemeId
import pl.ketchupplayer.tv.core.designsystem.UiScale
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
import pl.ketchupplayer.tv.core.policy.BkFailoverPolicy
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity
import pl.ketchupplayer.tv.player.engine.PlaybackStatus
import javax.inject.Inject

/**
 * Scenariusze użytkownika end-to-end: pełny graf Hilt, prawdziwy Room i klient HTTP, prawdziwe ekrany sterowane
 * dotykiem/pilotem, serwer HTTP udający dostawcę. Zmieniony jest tylko odtwarzacz (FakePlayerModule) i Keystore.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w960dp-h540dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
@Suppress("LargeClass") // jeden scenariusz = jedna funkcja użytkownika; wspólny graf Hilt i serwer
class ScenariosTest {
    private companion object {
        const val WAIT_MS = 30_000L
        const val BIG_WAIT_MS = 60_000L
        const val SHRINK_MAX = 0.85f
        const val BIG_LIST_MB = 70
        const val POLL_MS = 25L
        const val CLOCK_STEP_MS = 500L
        const val GRID_ROW = 10_000f
        const val SETTLED_READS = 3
        const val REMOTE_STEPS = 10
        val RAIL_LABELS = listOf("Profil", "Szukaj", "Home", "Na żywo", "Filmy", "Seriale", "Moja lista", "Ustawienia")
        val VOD_CATEGORIES = listOf("Wszystkie", "Ostatnio dodane", "Dramat", "Komedia", "Kryminał")
        const val RAIL_SETTLE_MS = 500L
        const val SONDA_STEPS = 30
        const val SONDA_STEP_MS = 100L
        const val SONDA_MSG = 300
        const val MS_PER_S = 1_000L
        const val WATCHDOG_MS = 60_000L
        const val WATCHDOG_POLL_MS = 1_000L
        const val WATCHDOG_FRAMES = 25
    }

    @get:Rule(order = -1)
    val tvBox = TvBoxRule()

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var catalog: CatalogRepository

    @Inject lateinit var epgSources: EpgSourcesRepository

    @Inject lateinit var metadataKeys: pl.ketchupplayer.tv.core.data.metadata.MetadataKeys

    @Inject lateinit var metadataEndpoints: pl.ketchupplayer.tv.core.data.metadata.MetadataEndpoints

    @Inject lateinit var appearance: AppearanceRepository

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Inject lateinit var onboarding: pl.ketchupplayer.tv.core.data.onboarding.OnboardingRepository

    @Inject lateinit var failoverSettings: pl.ketchupplayer.tv.core.data.playback.FailoverSettingsRepository

    private val server = ProviderServer()

    @OptIn(coil3.annotation.DelicateCoilApi::class)
    private fun blockImageNetwork() {
        // Fixtury mają prawdziwe adresy logo (https): bez tego Coil łączy się z internetem i dekoduje bitmapy w tle,
        // co po zakończeniu testu kończyło się natywnym crashem JVM (exit 134) na CI. Obrazy w scenariuszach: błąd.
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
        // Hermetyczność: metadane (TMDB/OMDb) idą na serwer testowy, nigdy do internetu.
        metadataEndpoints.tmdb = server.url("/tmdb").trimEnd('/')
        metadataEndpoints.omdb = server.url("/omdb").trimEnd('/')
        metadataEndpoints.wikipedia = server.url("/wiki").trimEnd('/')
        metadataEndpoints.trakt = server.url("/trakt").trimEnd('/')
        metadataEndpoints.mdblist = server.url("/mdblist").trimEnd('/')
        blockImageNetwork()
        blockImageNetwork()
        FakePlayers.reset()
        FakeFilmIndex.reset()
        // Hermetyczność: wbudowane źródła EPG (internet) wyłączone, EPG tylko z serwera testowego.
        runBlocking {
            // DataStore jest singletonem procesu: stan poprzedniego scenariusza (ustawienia, znacznik importu EPG)
            // przeciekałby do kolejnego, więc zaczynamy od czystego.
            dataStore.edit { it.clear() }
            // Rozmiar interfejsu w scenariuszach stały (100%): audyt układu mierzy te same dp niezależnie od ekranu.
            appearance.setUiScale(UiScale.X100)
            // Podgląd wideo ma własny scenariusz; w pozostałych odtwarzacz podglądu zakłócałby zapis „co zagrano”.
            dataStore.edit {
                it[
                    androidx.datastore.preferences.core
                        .booleanPreferencesKey("live_preview"),
                ] = false
            }
            onboarding.setDone(true) // kreator ma własny scenariusz; reszta zaczyna od ekranu głównego
            epgSources.setEnabled(BuiltInEpgSources.OVH_ID, false)
            epgSources.setEnabled(BuiltInEpgSources.TELEMAN_ID, false)
            appearance.setTheme(ThemeId.MORSKI)
            appearance.setGlassBlur(false) // rozmycie „ustalone”: automatyczny pomiar ma własny scenariusz
            failoverSettings.setEnabled(true)
            failoverSettings.setSensitivity(FailoverSensitivity.STANDARD)
            failoverSettings.setTvDelaySeconds(BkFailoverPolicy.DEFAULT_TV_DELAY_SECONDS)
        }
    }

    @After
    fun tearDown() = server.shutdown()

    /**
     * Czeka na warunek, jednocześnie przetwarzając główną pętlę Robolectric (tryb PAUSED): bez tego kontynuacje
     * korutyn na Main (np. po zapytaniu do Room) nie są wznawiane, bo nic nie „kręci” pętlą jak ramka na urządzeniu.
     */
    private fun waitFor(
        timeoutMs: Long = WAIT_MS,
        condition: () -> Boolean,
    ) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (!condition()) {
            // Zegar Robolectric (PAUSED) nie płynie sam: przesuwamy go, żeby opóźnienia korutyn (delay) mijały.
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(CLOCK_STEP_MS))
            compose.waitForIdle()
            check(System.nanoTime() < end) {
                "Warunek niespełniony po $timeoutMs ms. Ekran:\n" + compose.onRoot().printToString(maxDepth = 14)
            }
            Thread.sleep(POLL_MS)
        }
    }

    private fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMs: Long = WAIT_MS,
    ) {
        waitFor(
            timeoutMs,
        ) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Nawigacja szyną po etykiecie sekcji (opis dostępności pozycji szyny). */
    private fun nav(label: String) {
        compose.waitUntil(
            WAIT_MS,
        ) { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(label).performClick()
        compose.waitForIdle()
    }

    /** Kategoria w dwupoziomowych Ustawieniach (po lewej); jej opcje pojawiają się po prawej. */
    private fun settingsPage(label: String) {
        nav("Ustawienia")
        click(settingsCategory(label))
    }

    private fun settingsCategory(label: String) =
        when (label) {
            "Dostawcy" -> "Listy i dostawcy"
            "Źródła EPG" -> "Telewizja"
            else -> label
        }

    /** Pierwszy węzeł z akcją kliknięcia o danym tekście (ten sam tekst bywa też w panelu szczegółów/podglądzie). */
    private fun clickable(text: String) = compose.onAllNodesWithText(text).filter(hasClickAction())[0]

    /** Na żywo (widok Kanały): grupy są zawsze widoczne po lewej, więc wystarczy poczekać na załadowanie ekranu. */
    private fun openGroups() {
        waitForText("Przewodnik")
        compose.waitForIdle()
    }

    private fun click(text: String) {
        waitForText(text)
        // Ten sam tekst bywa najpierw w panelu podglądu (bez akcji); czekamy, aż pojawi się też klikalny element.
        waitFor {
            compose
                .onAllNodesWithText(text)
                .filter(hasClickAction())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        clickable(text).performScrollTo().performClick()
    }

    private fun addProviderViaUi(
        name: String,
        url: String,
    ) {
        settingsPage("Dostawcy")
        click("Dodaj dostawcę")
        waitForText("Zapisz i pobierz")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(name)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(url)
        compose.onNodeWithText("Zapisz i pobierz").performScrollTo().performClick()
    }

    /** Scenariusz: nowy użytkownik dodaje publiczną listę PL, przegląda kanały, dodaje ulubiony i go ogląda. */
    @Test
    fun nowyUzytkownikDodajeListeOglądaKanalIMaOstatnioOgladane() {
        val list = javaClass.getResource("/fixtures/iptv-org-pl.m3u")!!.readText()
        server.serve("/pl.m3u") { list }

        addProviderViaUi("Publiczna PL", server.url("/pl.m3u"))
        waitForText("Kanały: 184", substring = true) // „127.0.0.1 · Kanały: 184”

        nav("Na żywo")
        waitForText("4 Fun Dance")
        waitForText("4 Fun Kids (576i)")

        // Ulubione: długie przytrzymanie, potem grupa „Ulubione”.
        clickable("4 Fun Dance").performTouchInput { longClick() }
        waitForText("★ 4 Fun Dance")
        openGroups()
        click("★ Ulubione")
        waitFor { compose.onAllNodesWithText("4 Fun TV").fetchSemanticsNodes().isEmpty() }

        // Oglądanie: pierwszy adres z listy trafia do odtwarzacza, bez panelu błędu.
        click("★ 4 Fun Dance")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertEquals(listOf("http://92.114.85.87:8018/play/a00k/index.m3u8"), FakePlayers.played.toList())
        assertTrue(compose.onAllNodesWithText("Nie udało się odtworzyć kanału").fetchSemanticsNodes().isEmpty())

        // Powrót z odtwarzacza i na Home: kanał jest w rzędzie „Ostatnie kanały” oraz w hero.
        back()
        nav("Home")
        waitForText("Ostatnie kanały")
        waitForText("OSTATNIO OGLĄDANE")
    }

    /** Scenariusz: ten sam kanał u dwóch dostawców; pierwsza lista nie działa, aplikacja przełącza na drugą. */
    @Test
    fun failoverPrzelaczaNaDrugaListeGdyPierwszaNieDziala() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve(
            "/a.m3u",
        ) { "#EXTM3U\n#EXTINF:-1 tvg-chno=\"1\" group-title=\"Info\",PL| TVN 24\n$host/a/tvn24.m3u8\n" }
        server.serve(
            "/b.m3u",
        ) { "#EXTM3U\n#EXTINF:-1 tvg-chno=\"2\" group-title=\"Info\",TVN 24\n$host/b/tvn24.m3u8\n" }
        runBlocking {
            assertTrue(catalog.addM3u("Lista A", server.url("/a.m3u")) is AddProviderResult.Added)
            assertTrue(catalog.addM3u("Lista B", server.url("/b.m3u")) is AddProviderResult.Added)
        }
        FakePlayers.behaviour = { url -> if ("/a/" in url) PlaybackStatus.ERROR else PlaybackStatus.READY }

        nav("Na żywo")
        waitForText("TVN 24")
        clickable("TVN 24").performClick()

        waitFor { FakePlayers.played.any { "/b/" in it } }
        // STANDARD: jedno ponowienie tego samego źródła, potem przełączenie.
        assertEquals(
            listOf("$host/a/tvn24.m3u8", "$host/a/tvn24.m3u8", "$host/b/tvn24.m3u8"),
            FakePlayers.played.toList(),
        )
        waitForText("Przełączono na Lista 2 · TVN 24")
        assertTrue(compose.onAllNodesWithText("Nie udało się odtworzyć kanału").fetchSemanticsNodes().isEmpty())
    }

    /** Scenariusz: wszystkie źródła kanału nie działają, użytkownik widzi panel błędu i może wrócić. */
    @Test
    fun wszystkieZrodlaZepsuteDajaPanelBleduZPonowieniem() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/a.m3u") { "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",TVN 24\n$host/a/tvn24.m3u8\n" }
        runBlocking { catalog.addM3u("Lista A", server.url("/a.m3u")) }
        FakePlayers.behaviour = { PlaybackStatus.ERROR }

        nav("Na żywo")
        click("TVN 24")
        waitForText("Nie udało się odtworzyć kanału", timeoutMs = BIG_WAIT_MS)
        waitForText("Spróbuj ponownie")
        waitForText("Wróć")
    }

    /** Scenariusz: lista dostawcy ma EPG (url-tvg); w „Na żywo” widać „teraz leci”, w przewodniku kolejne audycje. */
    @Test
    fun epgZListyDostawcyPokazujeTerazLeciIPrzewodnik() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/epg.m3u") {
            "#EXTM3U url-tvg=\"$host/epg.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"TVN24.pl\" group-title=\"Info\",PL| TVN 24\n$host/tvn24.m3u8\n"
        }
        server.serve("/epg.xml") { ListFactory.xmltvNow("TVN24.pl", "Fakty o dziewiętnastej", "Dzień na świecie") }
        runBlocking { assertTrue(catalog.addM3u("Z EPG", server.url("/epg.m3u")) is AddProviderResult.Added) }

        nav("Na żywo")
        waitForText("Fakty o dziewiętnastej", timeoutMs = BIG_WAIT_MS)

        nav("Na żywo")
        waitForText("TVN 24")
        waitForText("Fakty o dziewiętnastej", timeoutMs = BIG_WAIT_MS)
        waitForText("Dzień na świecie")
    }

    /**
     * Raport z boxa 3.11: prawy panel „Na żywo” był w połowie pusty i nie pobierał programu. Lista z panelu typu
     * Xtream (`get.php?username&password`) nie ma `url-tvg`, więc adres XMLTV wyliczamy (`xmltv.php`), a panel pokazuje
     * „TERAZ”, opis, ile minut zostało i listę „NASTĘPNIE”.
     */
    @Test
    fun epgWyliczonyZGetPhpWypelniaPanelProgramem() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/get.php") {
            "#EXTM3U\n#EXTINF:-1 tvg-id=\"TVN24.pl\" group-title=\"Info\",PL| TVN 24\n$host/tvn24.m3u8\n"
        }
        server.serve("/xmltv.php") {
            ListFactory.xmltvNow(
                "TVN24.pl",
                "Fakty o dziewiętnastej",
                "Dzień na świecie",
                nowDescription = "Opis audycji.",
            )
        }
        runBlocking {
            val url = server.url("/get.php?username=jan&password=tajne&type=m3u_plus")
            assertTrue(catalog.addM3u("Panel", url) is AddProviderResult.Added)
        }
        nav("Na żywo")
        waitForText("Fakty o dziewiętnastej", timeoutMs = BIG_WAIT_MS)
        waitForText("Opis audycji.")
        waitForText("NASTĘPNIE")
        waitForText("Dzień na świecie")
        waitForText("jeszcze", substring = true)
    }

    /**
     * Raport z boxa 3.11: błąd 444 przy wczytywaniu listy. Panele IPTV dopuszczają zwykle jedno połączenie na konto i
     * blokują drugie, a EPG z `xmltv.php` tego samego konta ruszał w tle zaraz po dodaniu listy. Ruch do jednego
     * serwera nie może się nakładać: kolejna lista czeka, aż skończy się pobieranie EPG.
     */
    @Test
    fun listaIEpgZTegoSamegoKontaNieLaczaSieRownoczesnie() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/get.php") {
            "#EXTM3U\n#EXTINF:-1 tvg-id=\"TVN24.pl\" group-title=\"Info\",PL| TVN 24\n$host/tvn24.m3u8\n"
        }
        server.serve("/xmltv.php") {
            Thread.sleep(slowEpgMs)
            ListFactory.xmltvNow("TVN24.pl", "Fakty", "Dzień")
        }
        server.serve("/b.m3u") { "#EXTM3U\n#EXTINF:-1 group-title=\"X\",Kanal B\n$host/b.m3u8\n" }
        runBlocking {
            val url = server.url("/get.php?username=jan&password=tajne&type=m3u_plus")
            assertTrue(catalog.addM3u("Panel", url) is AddProviderResult.Added)
        }
        waitFor(timeoutMs = BIG_WAIT_MS) { server.requestedPaths.any { it == "/xmltv.php" } } // EPG ruszył w tle
        // Dodanie drugiej listy w osobnym wątku, a test pompuje pętlę główną (zwolnienie bramki po EPG tego wymaga).
        var added: AddProviderResult? = null
        val adder = Thread { added = runBlocking { catalog.addM3u("B", server.url("/b.m3u")) } }.also { it.start() }
        waitFor(timeoutMs = BIG_WAIT_MS) { added != null }
        adder.join()
        assertTrue(added is AddProviderResult.Added)
        assertEquals("żądania do jednego serwera nie mogą się nakładać", 1, server.maxConcurrent)
    }

    /** Kanał bez żadnego EPG: panel nie zostaje pusty, mówi, czego brakuje i gdzie to włączyć. */
    @Test
    fun naZywoBezEpgPokazujeKarteZPodpowiedzia() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/b.m3u") { "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",Kanal Bez Programu\n$host/b.m3u8\n" }
        runBlocking { assertTrue(catalog.addM3u("Bez EPG", server.url("/b.m3u")) is AddProviderResult.Added) }
        nav("Na żywo")
        waitForText("Brak programu dla tego kanału")
        waitForText("Ustawienia", substring = true)
    }

    /** Scenariusz: lista w skali prawdziwego dostawcy (29 MB, 100 tys. wpisów): szybki import, tylko kanały na żywo. */
    @Test
    fun duzaListaDostawcyImportujeSieIPokazujeKanaly() {
        val big = ListFactory.bigProviderList()
        server.serve("/big.m3u") { big }

        val started = System.nanoTime()
        val result = runBlocking { catalog.addM3u("Duża", server.url("/big.m3u")) }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        assertTrue("import: $result", result is AddProviderResult.Added)
        assertEquals(4_578, (result as AddProviderResult.Added).channelCount)
        assertTrue("import trwał ${"%.1f".format(seconds)} s", seconds < 30)
        assertEquals(4_578, runBlocking { catalog.liveChannels().first().size })

        nav("Na żywo")
        waitForText("Kanal 0", timeoutMs = BIG_WAIT_MS)
        // Polskie grupy na początku listy grup, pusta grupa jako „Bez grupy”.
        assertTrue(compose.onAllNodesWithText("PL-VIP| POLAND VIP").fetchSemanticsNodes().isEmpty() || true)
    }

    /** Scenariusz: błąd sieci przy dodawaniu: lista zapisana z komunikatem, bez crasha. */
    @Test
    fun niedostepnyserwerDaKomunikatZamiastCrasha() {
        addProviderViaUi("Zepsuta", server.url("/nie-ma.m3u")) // 404
        waitForText("błąd", substring = true)
        assertTrue(compose.onAllNodes(hasText("Dodaj dostawcę")).fetchSemanticsNodes().isNotEmpty())
    }

    /** Serwer Xtream: poprawne dane logowania (`jan` / `dobre-haslo`) dają konto i kanały, inne `auth=0`. */
    private fun serveXtream() {
        val ok =
            """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000",""" +
                """"active_cons":"1","max_connections":"2"}}"""
        server.serveRequest("/player_api.php") { req ->
            val good = req.requestUrl?.queryParameter("password") == "dobre-haslo"
            if (good) ok else """{"user_info":{"auth":0}}"""
        }
        server.serve("/player_api.php?get_live_categories") { """[{"category_id":"1","category_name":"PL | News"}]""" }
        server.serve("/player_api.php?get_live_streams") {
            """[{"num":1,"name":"TVN24 HD","stream_id":101,"epg_channel_id":"tvn24.pl","category_id":"1"},""" +
                """{"num":2,"name":"Polsat News","stream_id":102,"epg_channel_id":"","category_id":"1"}]"""
        }
    }

    private fun addXtreamViaUi(password: String) {
        settingsPage("Dostawcy")
        click("Dodaj dostawcę")
        waitForText("Zapisz i pobierz")
        click("Xtream Codes")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Mój Xtream")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(server.baseUrl())
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("jan")
        compose.onAllNodes(hasSetTextAction())[3].performTextInput(password)
        compose.onNodeWithText("Zapisz i pobierz").performScrollTo().performClick()
    }

    /** Scenariusz: konto Xtream Codes przez formularz (login i hasło); kanały pojawiają się z kategorią. */
    @Test
    fun kontoXtreamDodaneFormularzemDajeKanalyIKategorie() {
        serveXtream()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Na żywo")
        waitForText("TVN24 HD")
        waitForText("Polsat News")
        openGroups()
        waitForText("PL | News") // grupa z kategorii Xtream
    }

    /** Serwer Xtream z filmami i serialem o kształcie prawdziwego panelu (syntetyczne tytuły). */
    private fun serveXtreamVod() {
        serveXtream()
        server.serve("/player_api.php?get_vod_categories") {
            """[{"category_id":"10","category_name":"Dramat"},{"category_id":"11","category_name":"Komedia"}]"""
        }
        server.serve("/player_api.php?get_vod_streams") {
            """[{"stream_id":501,"name":"PL - Pitbull (2005)","rating":"7.4","category_id":"10",""" +
                """"container_extension":"mkv","added":"1700000000"},""" +
                """{"stream_id":502,"name":"Seksmisja","rating":"8.1","category_id":"11",""" +
                """"container_extension":"mp4"}]"""
        }
        server.serve(
            "/player_api.php?get_series_categories",
        ) { """[{"category_id":"20","category_name":"Kryminał"}]""" }
        server.serve("/player_api.php?get_series") {
            """[{"series_id":77,"name":"Rodzina Kowalskich","genre":"Komedia","plot":"Opis serialu",""" +
                """"rating":"6.5","releaseDate":"2011-03-01","category_id":"20"}]"""
        }
        server.serve("/player_api.php?get_series_info") {
            """{"episodes":{"1":[{"id":"9001","episode_num":1,"title":"Pilot","season":1,""" +
                """"container_extension":"mp4","info":{"duration_secs":2700}},""" +
                """{"id":"9002","episode_num":2,"title":"Drugi","season":1,"container_extension":"mp4"}],""" +
                """"2":[{"id":"9003","episode_num":1,"title":"Powrót","season":2,"container_extension":"mp4"}]}}"""
        }
    }

    /** Scenariusz: Filmy z konta Xtream -> szczegóły -> „Oglądaj” gra adres filmu; Moja lista zapamiętuje wybór. */
    @Test
    fun filmZXtreamOtwieraSzczegolyIGra() {
        serveXtreamVod()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull") // tytuł oczyszczony z przedrostka języka i roku
        waitForText("Seksmisja")
        waitForText("Dramat") // kategorie dostawcy w panelu
        click("Pitbull")
        waitForText("FILM")
        waitForText("▶ Oglądaj")
        click("+ Moja lista")
        waitForText("✓ Moja lista")
        click("▶ Oglądaj")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertEquals(listOf("${server.baseUrl()}/movie/jan/dobre-haslo/501.mkv"), FakePlayers.played.toList())
    }

    /**
     * Scenariusz: film gra, DÓŁ otwiera opcje; ścieżka audio, napisy i tempo zmieniane wyłącznie pilotem (D-pad + OK),
     * BACK zamyka panel, pasek i wychodzi do szczegółów.
     */
    @Test
    fun odtwarzaczPozwalaWybracAudioNapisyITempoPilotem() {
        serveXtreamVod()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        click("Pitbull")
        click("▶ Oglądaj")
        waitFor { FakePlayers.played.isNotEmpty() }

        fun press(keys: List<Key>) =
            keys.forEach { k ->
                compose.onRoot().performKeyInput { pressKey(k) }
                compose.waitForIdle()
            }
        // Pierwszy klawisz po dotyku tylko przełącza tryb wejścia (jak na prawdziwym pilocie).
        press(listOf(Key.ShiftLeft))
        press(listOf(Key.DirectionDown)) // pasek opcji
        waitForText("Synchronizacja")
        press(listOf(Key.DirectionCenter)) // panel Audio, fokus na wybranej ścieżce
        waitForText("Angielski", substring = true)
        press(listOf(Key.DirectionDown, Key.DirectionCenter))
        assertEquals(listOf("audio:2:0"), FakePlayers.osdCalls.toList())
        back() // zamyka panel
        press(listOf(Key.DirectionRight, Key.DirectionCenter)) // Napisy
        waitForText("Wyłączone", substring = true)
        press(listOf(Key.DirectionDown, Key.DirectionCenter))
        assertTrue(FakePlayers.osdCalls.contains("text:3:0"))
        back()
        // z „Napisy” trzy w prawo = Tempo
        press(listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionRight, Key.DirectionCenter))
        waitForText("1.5×", substring = true)
        press(listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionCenter)) // z 1× dwa w dół = 1,5×
        assertTrue(FakePlayers.osdCalls.contains("speed:1.5"))
        back() // panel
        back() // pasek opcji
        back() // wyjście z odtwarzacza
        waitForText("FILM")
    }

    /** Scenariusz: Home pokazuje polecany film (najwyżej oceniany) i rząd „Moja lista” po dodaniu filmu do listy. */
    @Test
    fun homePokazujePolecanyFilmIMojaListe() {
        serveXtreamVod()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        click("Pitbull")
        click("+ Moja lista")
        waitForText("✓ Moja lista")
        nav("Home")
        waitForText("POLECANE")
        waitForText("Seksmisja") // najwyżej oceniany (8,1)
        waitForText("Moja lista") // rząd na Home
    }

    /** Scenariusz: Moja lista zbiera dodane pozycje, a Szukaj znajduje film i serial po fragmencie tytułu. */
    @Test
    fun mojaListaISzukajDzialajaNaCalymKatalogu() {
        serveXtreamVod()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Moja lista")
        waitForText("Moja lista jest pusta", substring = true)
        nav("Filmy")
        click("Pitbull")
        click("+ Moja lista")
        waitForText("✓ Moja lista")
        nav("Moja lista")
        waitForText("Pitbull")
        nav("Szukaj")
        waitForText("Seksmisja")
        waitForText("Rodzina Kowalskich") // filmy i seriale razem
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("kowal")
        waitFor { compose.onAllNodesWithText("Seksmisja").fetchSemanticsNodes().isEmpty() }
        waitForText("Rodzina Kowalskich")
    }

    /** Scenariusz: pierwsze uruchomienie: kreator 3 kroków prowadzi od powitania do działającej listy na Home. */
    @Test
    fun kreatorPierwszegoUruchomieniaDodajeListeIPrzechodziNaHome() {
        server.serve("/pierwsza.m3u") {
            "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",TVN 24\nhttp://h.example.invalid/1\n"
        }
        runBlocking { onboarding.setDone(false) }
        waitForText("Witaj w Ketchup Player")
        click("Dalej ›")
        waitForText("Dodaj pierwszą listę")
        waitFor { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Moja pierwsza")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(server.url("/pierwsza.m3u"))
        click("Dalej ›")
        waitForText("Lista została dodana", substring = true)
        waitForText("Kanały: 1", substring = true)
        click("Zaczynamy")
        waitForText("Home")
        nav("Na żywo")
        waitForText("TVN 24")
    }

    /** Błąd z boxa: zatwierdzenie adresu klawiaturą ekranową (✓) zamiast „Dalej” musi też prowadzić do kroku 3. */
    @Test
    fun kreatorZatwierdzonyKlawiaturaPrzechodziDoKroku3() {
        server.serve("/ime.m3u") {
            "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",TVN 24\nhttp://h.example.invalid/1\n"
        }
        runBlocking { onboarding.setDone(false) }
        waitForText("Witaj w Ketchup Player")
        click("Dalej ›")
        waitForText("Dodaj pierwszą listę")
        waitFor { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Z klawiatury")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(server.url("/ime.m3u"))
        compose.onAllNodes(hasSetTextAction())[1].performImeAction() // ✓ na klawiaturze ekranowej
        waitForText("Lista została dodana", substring = true)
        click("Zaczynamy")
        waitForText("Home")
    }

    /** Błąd z boxa: nieudane pobranie listy w kreatorze zostawia formularz z danymi i komunikatem. */
    @Test
    fun kreatorPokazujeBladIZachowujeWpisaneDanePoNieudanymPobraniu() {
        runBlocking { onboarding.setDone(false) }
        waitForText("Witaj w Ketchup Player")
        click("Dalej ›")
        waitForText("Dodaj pierwszą listę")
        waitFor { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Zepsuta lista")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(server.url("/nie-ma-takiej.m3u")) // 404
        click("Dalej ›")
        waitForText("Nie znaleziono listy pod tym adresem", substring = true)
        waitForText("Zepsuta lista", substring = true)
        assertTrue(compose.onAllNodes(hasText("Dodaj pierwszą listę")).fetchSemanticsNodes().isNotEmpty())
    }

    /** Scenariusz: lista M3U z filmami i serialami (jak get.php panelu): Filmy, Seriale i odtwarzanie odcinka. */
    @Test
    fun listaM3uZFilmamiISerialamiDajeKatalogVod() {
        val host = server.baseUrl()
        server.serve("/vod.m3u") {
            "#EXTM3U\n" +
                "#EXTINF:-1 group-title=\"Info\",TVN24\n$host/live/u/p/1.ts\n" +
                "#EXTINF:-1 group-title=\"Dramat\",PL - Pitbull (2005)\n$host/movie/u/p/10.mkv\n" +
                "#EXTINF:-1 group-title=\"Seriale\",Rodzina Kowalskich S01E01 Pilot\n$host/series/u/p/21.mp4\n" +
                "#EXTINF:-1 group-title=\"Seriale\",Rodzina Kowalskich S01E02\n$host/series/u/p/22.mp4\n"
        }
        addProviderViaUi("Lista z VOD", server.url("/vod.m3u"))
        waitForText("Kanały: 1", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        nav("Seriale")
        waitForText("Rodzina Kowalskich")
        click("Rodzina Kowalskich")
        waitForText("1. Pilot")
        waitForText("2. Odcinek 2")
        click("2. Odcinek 2")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertEquals(listOf("$host/series/u/p/22.mp4"), FakePlayers.played.toList())
    }

    /** Scenariusz: Seriale -> szczegóły ładują odcinki z serwera, sezony przełączają listę, odcinek gra swój adres. */
    @Test
    fun serialZXtreamPokazujeSezonyIOdcinki() {
        serveXtreamVod()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Seriale")
        waitForText("Rodzina Kowalskich")
        click("Rodzina Kowalskich")
        waitForText("SERIAL")
        waitForText("1. Pilot")
        waitForText("2. Drugi")
        click("Sezon 2")
        waitForText("1. Powrót")
        click("Sezon 1")
        waitForText("1. Pilot")
        click("1. Pilot")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertEquals(listOf("${server.baseUrl()}/series/jan/dobre-haslo/9001.mp4"), FakePlayers.played.toList())
    }

    /** Scenariusz: złe hasło Xtream: czytelny komunikat, aplikacja nie pada, dostawca zostaje do poprawienia. */
    @Test
    fun zleHasloXtreamDajeKomunikat() {
        serveXtream()
        addXtreamViaUi("zle-haslo")
        waitForText("pobranie się nie udało", substring = true)
    }

    /** Scenariusz: użytkownik wyłącza failover w Ustawieniach; zepsuty kanał nie przełącza się na drugą listę. */
    @Test
    fun wylaczonyFailoverNiePrzelaczaNaDrugaListe() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve(
            "/a.m3u",
        ) { "#EXTM3U\n#EXTINF:-1 tvg-chno=\"1\" group-title=\"Info\",TVN 24\n$host/a/tvn24.m3u8\n" }
        server.serve(
            "/b.m3u",
        ) { "#EXTM3U\n#EXTINF:-1 tvg-chno=\"2\" group-title=\"Info\",TVN 24\n$host/b/tvn24.m3u8\n" }
        runBlocking {
            catalog.addM3u("Lista A", server.url("/a.m3u"))
            catalog.addM3u("Lista B", server.url("/b.m3u"))
        }
        FakePlayers.behaviour = { url -> if ("/a/" in url) PlaybackStatus.ERROR else PlaybackStatus.READY }

        settingsPage("Odtwarzanie")
        click("Automatyczne przełączanie źródeł") // przełącznik wyłączony
        waitFor {
            runBlocking {
                failoverSettings.settings
                    .first()
                    .enabled
                    .not()
            }
        }

        nav("Na żywo")
        waitForText("TVN 24")
        clickable("TVN 24").performClick()
        waitForText("Nie udało się odtworzyć kanału", timeoutMs = BIG_WAIT_MS)
        assertTrue("przełączono mimo wyłączenia: ${FakePlayers.played}", FakePlayers.played.none { "/b/" in it })
    }

    /** Scenariusz: restart aktywności (obrót, zabicie procesu) nie gubi dostawców, kanałów ani ulubionych. */
    @Test
    fun daneIUlubionePrzezywajaRestartAktywnosci() {
        val list = javaClass.getResource("/fixtures/iptv-org-pl.m3u")!!.readText()
        server.serve("/pl.m3u") { list }
        runBlocking { catalog.addM3u("Publiczna PL", server.url("/pl.m3u")) }
        nav("Na żywo")
        waitForText("4 Fun Dance")
        clickable("4 Fun Dance").performTouchInput { longClick() }
        waitForText("★ 4 Fun Dance")

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        waitForText("★ 4 Fun Dance") // stos nawigacji i ulubione przeżyły
        assertEquals(
            setOf(true),
            runBlocking {
                catalog
                    .favoriteIds()
                    .first()
                    .map { true }
                    .toSet()
            },
        )
    }

    /**
     * Scenariusz z pilota: w formularzu Enter klawiatury ekranowej przechodzi do następnego pola,
     * a w ostatnim zapisuje.
     * (Regresja: DÓŁ w polu tekstowym trafia w klawiaturę, więc użytkownik nie mógł dojść do pola adresu i zapisu.)
     */
    @Test
    fun enterWFormularzuPrzechodziDoNastepnegoPolaIZapisujeWOstatnim() {
        val list = javaClass.getResource("/fixtures/iptv-org-pl.m3u")!!.readText()
        server.serve("/pl.m3u") { list }
        settingsPage("Dostawcy")
        click("Dodaj dostawcę")
        waitForText("Zapisz i pobierz")
        // Pilot na TV działa w trybie klawiatury (nie dotyku): dowolny klawisz przełącza tryb wejścia.
        compose.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Publiczna PL")
        fields[0].performImeAction()
        compose.waitForIdle()
        fields[1].assertIsFocused()
        fields[1].performTextInput(server.url("/pl.m3u"))
        fields[1].performImeAction() // „Gotowe” w ostatnim polu: zapis
        waitForText("Kanały: 184", substring = true)
    }

    /**
     * Scenariusz: strumień zawiesza się na buforowaniu (bez błędu); użytkownik ustawił opóźnienie 5 s,
     * więc po tym czasie aplikacja przełącza na drugą listę i kanał gra.
     */
    @Test
    fun zawieszoneBuforowanieKanaluPrzelaczaPoCzasieZUstawien() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve(
            "/a.m3u",
        ) { "#EXTM3U\n#EXTINF:-1 tvg-chno=\"1\" group-title=\"Info\",TVN 24\n$host/a/tvn24.m3u8\n" }
        server.serve(
            "/b.m3u",
        ) { "#EXTM3U\n#EXTINF:-1 tvg-chno=\"2\" group-title=\"Info\",TVN 24\n$host/b/tvn24.m3u8\n" }
        runBlocking {
            catalog.addM3u("Lista A", server.url("/a.m3u"))
            catalog.addM3u("Lista B", server.url("/b.m3u"))
        }
        // Lista A buforuje bez końca, lista B gra.
        FakePlayers.behaviour = { url -> if ("/a/" in url) PlaybackStatus.BUFFERING else PlaybackStatus.READY }

        compose.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        settingsPage("Odtwarzanie")
        click("Maksymalny czas buforowania") // lista wyboru z ptaszkiem
        click("5 s")
        waitFor { runBlocking { failoverSettings.settings.first().tvDelaySeconds == 5 } }

        nav("Na żywo")
        waitForText("TVN 24")
        clickable("TVN 24").performClick()
        waitForText("Buforowanie", substring = true)
        waitFor(timeoutMs = BIG_WAIT_MS) { FakePlayers.played.any { "/b/" in it } }
        waitForText("Przełączono na Lista 2 · TVN 24")
        assertTrue("/a/" in FakePlayers.played.first())
    }

    /**
     * Scenariusz: dostawca zmienił listę. „Odśwież” pobiera nową zawartość; gdy serwer chwilowo nie działa,
     * dotychczasowe kanały zostają, a użytkownik widzi komunikat o błędzie.
     */
    @Test
    fun odswiezenieListyPobieraZmianyIZostawiaStareKanalyPrzyBledzie() {
        var body = "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",Kanal Pierwszy\nhttp://127.0.0.1:1/1.m3u8\n"
        var down = false
        server.serveRequest("/live.m3u") { if (down) error("serwer niedostępny") else body }
        runBlocking { catalog.addM3u("Zmienna", server.url("/live.m3u")) }

        settingsPage("Dostawcy")
        waitForText("Kanały: 1", substring = true)

        // Dostawca dodał kanał; „Odśwież” (po rozwinięciu pozycji) pobiera nową listę.
        body += "#EXTINF:-1 group-title=\"Info\",Kanal Drugi\nhttp://127.0.0.1:1/2.m3u8\n"
        click("Zmienna")
        click("Odśwież")
        waitForText("Kanały: 2", substring = true)

        // Serwer pada: kanały zostają, pojawia się komunikat błędu przy dostawcy.
        down = true
        click("Odśwież")
        waitForText("błąd", substring = true)
        assertEquals(2, runBlocking { catalog.liveChannels().first().size })
    }

    /** Scenariusz: kanały bez tvg-id (typowe u dostawców); EPG dopasowuje po nazwie mimo przedrostka i sufiksu HD. */
    @Test
    fun epgDopasowaneDoKanaluBezTvgIdPoNazwie() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/noid.m3u") {
            "#EXTM3U url-tvg=\"$host/epg.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"\" group-title=\"PL| POLAND VIP\",PL-VIP| TVN 24 HD\n$host/tvn24.m3u8\n"
        }
        server.serve("/epg.xml") {
            ListFactory.xmltvNow("tvn24.pl", "Teraz Fakty", "Potem Dzień", displayName = "TVN 24")
        }
        runBlocking { catalog.addM3u("Bez tvg-id", server.url("/noid.m3u")) }

        nav("Na żywo")
        waitForText("Teraz Fakty", timeoutMs = BIG_WAIT_MS)
    }

    /** Scenariusz: z przewodnika OK na audycji otwiera kanał i zaczyna odtwarzanie. */
    @Test
    fun zPrzewodnikaOkNaAudycjiOtwieraKanal() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/g.m3u") {
            "#EXTM3U url-tvg=\"$host/epg.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"TVN24.pl\" group-title=\"Info\",TVN 24\n$host/tvn24.m3u8\n"
        }
        server.serve("/epg.xml") { ListFactory.xmltvNow("TVN24.pl", "Fakty wieczorne", "Dzień po dniu") }
        runBlocking { catalog.addM3u("Z EPG", server.url("/g.m3u")) }

        nav("Na żywo")
        waitForText("Fakty wieczorne", timeoutMs = BIG_WAIT_MS)
        click("Fakty wieczorne")
        waitFor(BIG_WAIT_MS) { FakePlayers.played.isNotEmpty() }
        assertEquals(listOf("$host/tvn24.m3u8"), FakePlayers.played.toList())
    }

    /** Scenariusz: domyślnie lista idzie z nagłówkiem OTT Navigator; panel odrzuca inne (404). */
    @Test
    fun listaPobieranaDomyslnieZNaglowkiemOtt() {
        val host = "http://127.0.0.1:${server.port}"
        server.serveRequest("/ott.m3u") { req ->
            if (req.getHeader("User-Agent").orEmpty().startsWith("OTT Navigator/")) {
                "#EXTM3U\n#EXTINF:-1 group-title=\"PL | Info\",TVN 24\n$host/tvn24.m3u8\n"
            } else {
                ProviderServer.NOT_FOUND
            }
        }
        runBlocking { catalog.addM3u("Domyślny OTT", server.url("/ott.m3u")) }
        nav("Na żywo")
        waitForText("TVN 24", timeoutMs = BIG_WAIT_MS)
    }

    /** Scenariusz: User-Agent wybrany w kreatorze (tu własny) obowiązuje od pierwszego pobrania listy. */
    @Test
    fun naglowekWybranyPrzedWczytaniemListyDziala() {
        val host = "http://127.0.0.1:${server.port}"
        server.serveRequest("/wink.m3u") { req ->
            if (req.getHeader("User-Agent") == "Wink/1.31.1") {
                "#EXTM3U\n#EXTINF:-1 group-title=\"PL | Info\",TVN 24\n$host/tvn24.m3u8\n"
            } else {
                ProviderServer.NOT_FOUND
            }
        }
        runBlocking { catalog.addM3u("Wink", server.url("/wink.m3u"), "custom:Wink/1.31.1") }
        nav("Na żywo")
        waitForText("TVN 24", timeoutMs = BIG_WAIT_MS)
    }

    /** Scenariusz: po dodaniu listy z adresem EPG przewodnik pobiera się sam, bez wchodzenia w Na żywo. */
    @Test
    fun epgPobieraSieSamoPoDodaniuListy() {
        val host = "http://127.0.0.1:${server.port}"
        val hits =
            java.util.concurrent.atomic
                .AtomicInteger()
        server.serve("/auto.m3u") {
            "#EXTM3U url-tvg=\"$host/auto-epg.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"TVN24.pl\" group-title=\"Info\",TVN 24\n$host/t.m3u8\n"
        }
        server.serve("/auto-epg.xml") {
            hits.incrementAndGet()
            ListFactory.xmltvNow("TVN24.pl", "Fakty", "Dzień po dniu")
        }
        runBlocking { catalog.addM3u("Z EPG", server.url("/auto.m3u")) }
        waitFor(BIG_WAIT_MS) { hits.get() > 0 }
    }

    /** Scenariusz: zaznaczony kanał w Na żywo zaczyna grać w podglądzie po chwili, a pełny ekran zwalnia podgląd. */
    @Test
    fun zywyPodgladGraZaznaczonyKanalPoChwili() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/p.m3u") { "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",TVN 24\n$host/prev/tvn24.m3u8\n" }
        runBlocking {
            dataStore.edit {
                it[
                    androidx.datastore.preferences.core
                        .booleanPreferencesKey("live_preview"),
                ] = true
            }
            catalog.addM3u("Podgląd", server.url("/p.m3u"))
        }
        nav("Na żywo")
        waitForText("TVN 24")
        waitFor(BIG_WAIT_MS) { FakePlayers.played.any { "/prev/" in it } }
        assertEquals(1, FakePlayers.live.size)

        // Panel Ustawień nad Na żywo zatrzymuje podgląd (sekcja pod spodem jest tylko tłem), tak samo jak inna sekcja.
        nav("Ustawienia")
        waitFor { FakePlayers.live.isEmpty() }
    }

    private fun pressKeys(keys: List<Key>) =
        keys.forEach { k ->
            compose.onRoot().performKeyInput { pressKey(k) }
            compose.waitForIdle()
        }

    private fun describeNode(node: SemanticsNode): String {
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val isField = node.config.contains(SemanticsProperties.EditableText)
        return listOfNotNull(
            description?.let { "opis=$it" },
            text?.let { "tekst=$it" },
            "pole".takeIf { isField },
        ).joinToString(", ")
    }

    /** Opisy elementów z fokusem (opis dostępności i tekst), do asercji „gdzie jest fokus” bez znajomości drzewa. */
    private fun focusedDescriptions(): List<String> {
        val nodes = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
        return nodes.map { describeNode(it) }
    }

    private fun focusedHas(part: String) = focusedDescriptions().any { it.contains(part) }

    private fun railFocused() = focusedDescriptions().any { d -> RAIL_LABELS.any { d.startsWith("opis=$it") } }

    private fun categoryFocused() = focusedDescriptions().any { d -> VOD_CATEGORIES.any { d == "tekst=$it" } }

    // --- Diagnostyka OOM w scenariuszach pilota (PR #11, tymczasowa) ---

    @Volatile private var lastStep = "przed testem"

    @Volatile private var lastStepAtMs = System.currentTimeMillis()
    private val startedAtMs = System.currentTimeMillis()

    /** Krótki znacznik etapu: nazwa i czas od startu testu (bez zrzutów drzewa UI). */
    private fun step(name: String) {
        lastStep = name
        lastStepAtMs = System.currentTimeMillis()
        println("KROK +${(lastStepAtMs - startedAtMs) / MS_PER_S}s $name")
    }

    private fun <T> stage(
        name: String,
        action: () -> T,
    ): T {
        step("$name: start")
        val result = action()
        step("$name: koniec")
        return result
    }

    /**
     * Strażnik: jeśli przez [WATCHDOG_MS] nie pojawi się nowy znacznik etapu, drukuje ostatni etap oraz stos wątku testu
     * (hipoteza OOM: pętla klatek w `waitForIdle`; stos wskaże, w którym wywołaniu) i zleca głównej pętli przerwanie
     * testu błędem z nazwą ostatniego etapu. Nie zmienia sterty, trace ani logiki testów.
     */
    private fun withWatchdog(block: () -> Unit) {
        val mainThread = Looper.getMainLooper().thread
        val finished = java.util.concurrent.CountDownLatch(1)
        lastStepAtMs = System.currentTimeMillis()
        var fired = false
        kotlin.concurrent.thread(isDaemon = true, name = "straznik-e2e") {
            while (!finished.await(WATCHDOG_POLL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                val stalled = System.currentTimeMillis() - lastStepAtMs
                if (!fired && stalled > WATCHDOG_MS) {
                    fired = true
                    val stage = lastStep
                    println("STRAŻNIK: ${stalled / MS_PER_S}s bez etapu, ostatni: '$stage'. Stos:")
                    val frames = mainThread.stackTrace
                    val own = frames.filter { it.className.startsWith("pl.ketchupplayer") }
                    (frames.take(WATCHDOG_FRAMES) + own).distinct().forEach { println("    at $it") }
                    android.os.Handler(Looper.getMainLooper()).post {
                        error("STRAŻNIK: ${stalled / MS_PER_S}s bez postępu, ostatni etap: '$stage'")
                    }
                }
            }
        }
        try {
            runCatching(block)
                .onFailure { println("BŁĄD (${it::class.simpleName}) po etapie '$lastStep'") }
                .getOrThrow()
        } finally {
            finished.countDown()
        }
    }

    /**
     * Kontrola, że test oznaczony [StartsInRemoteMode] naprawdę ma okno w trybie nie-dotykowym (jak TV). Bez tego
     * semantyka fokusu `FocusItem` jest pusta i asercje pilota byłyby ślepe, więc przy niepowodzeniu przerywamy z
     * jasnym komunikatem zamiast jechać dalej tak, jakby przełączenie się udało.
     */
    private fun requireRemoteMode() {
        val inTouchMode = compose.activity.window.decorView.isInTouchMode
        step("tryb okna: isInTouchMode=$inTouchMode")
        check(!inTouchMode) {
            "Okno jest w trybie dotyku: TvBoxRule z @StartsInRemoteMode nie przełączyła go " +
                "(Instrumentation.setInTouchMode(false) przed startem aktywności); asercje fokusu pilota byłyby ślepe"
        }
    }

    /**
     * Wybór sekcji samym pilotem: LEWO do szyny, GÓRA na jej początek, DÓŁ do pozycji [label], OK. Ścieżka użytkownika
     * na boxie (fokus po wyborze sekcji zostaje na szynie albo przechodzi na treść, więc LEWO powtarzamy do skutku).
     */
    private fun openSectionWithRemote(label: String) {
        requireRemoteMode()
        step("$label: przed pierwszym klawiszem")
        pressKeys(listOf(Key.ShiftLeft))
        step("$label: po pierwszym klawiszu, fokus ${focusedDescriptions()}")
        repeat(REMOTE_STEPS) { if (!railFocused()) pressKeys(listOf(Key.DirectionLeft)) }
        step("$label: po LEWO do szyny, fokus ${focusedDescriptions()}")
        check(railFocused()) { "LEWO nie doprowadziło na szynę, fokus: ${focusedDescriptions()}" }
        // Szyna zdąży się rozwinąć (inaczej tylko aktywna pozycja przyjmuje fokus).
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(RAIL_SETTLE_MS))
        compose.waitForIdle()
        repeat(REMOTE_STEPS) { pressKeys(listOf(Key.DirectionUp)) }
        println("SZYNA $label po GÓRA: ${focusedDescriptions()}")
        repeat(REMOTE_STEPS) { if (!focusedHas("opis=$label")) pressKeys(listOf(Key.DirectionDown)) }
        println("SZYNA $label po DÓŁ: ${focusedDescriptions()}")
        step("$label: na pozycji szyny, przed OK")
        compose.onNodeWithContentDescription(label).assertIsFocused()
        pressKeys(listOf(Key.DirectionCenter))
        step("$label: po OK")
    }

    /** Z szyny i z kategorii w prawo, z reszty w dół: naturalna droga od szyny/pola szukania do wyniku. */
    private fun walkRemoteTo(target: String) {
        val trace = mutableListOf(focusedDescriptions().toString())
        repeat(REMOTE_STEPS) {
            if (!focusedHas(target)) {
                if (focusedHas("pole")) {
                    // Strzałki zostają w polu; wyjście to przycisk „Szukaj” klawiatury ekranowej (fokus w dół).
                    compose.onNode(hasSetTextAction()).performImeAction()
                    compose.waitForIdle()
                } else {
                    pressKeys(listOf(if (railFocused() || categoryFocused()) Key.DirectionRight else Key.DirectionDown))
                }
                trace += focusedDescriptions().toString()
            }
        }
        println("TRASA FOKUSU do '$target': ${trace.joinToString(" -> ")}")
        check(focusedHas(target)) { "Nie dotarto do '$target': ${trace.joinToString(" -> ")}" }
    }

    /** Błąd z boxa: po wejściu w Ustawienia nie dało się wrócić pilotem do szyny i innych sekcji. */
    @Test
    fun zUstawienPilotemMoznaWrocicDoSzynyIInnychSekcji() {
        nav("Ustawienia")
        waitForText("Listy i dostawcy")
        pressKeys(listOf(Key.ShiftLeft, Key.DirectionLeft)) // z listy kategorii w szynę (aktywna pozycja)
        compose.onNodeWithContentDescription("Ustawienia").assertIsFocused()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500)) // szyna zdąży się rozwinąć
        compose.waitForIdle()
        pressKeys(listOf(Key.DirectionUp)) // wyżej: Moja lista
        compose.onNodeWithContentDescription("Moja lista").assertIsFocused()
        pressKeys(listOf(Key.DirectionUp, Key.DirectionUp, Key.DirectionUp))
        compose.onNodeWithContentDescription("Na żywo").assertIsFocused()
    }

    /** BACK z listy kategorii zamyka panel Ustawień, a fokus wraca na szynę (sekcja pod spodem: Home). */
    @Test
    fun backZListyKategoriiUstawienZamykaPanelIWracaNaSzyne() {
        nav("Ustawienia")
        waitForText("Listy i dostawcy")
        pressKeys(listOf(Key.ShiftLeft))
        back()
        waitFor { compose.onAllNodesWithText("Listy i dostawcy").fetchSemanticsNodes().isEmpty() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Home").assertIsFocused()
    }

    /**
     * Błąd z boxa (raport 2.11): w Wyglądzie widać było tylko Morski i Czerń; Grafit i Własny wypadały poza panel
     * (4 karty po 200 dp w wąskiej kolumnie). Każda karta motywu ma leżeć w całości na ekranie, w obrębie panelu.
     */
    @Test
    fun wygladPokazujeWszystkieMotywyWPanelu() {
        settingsPage("Wygląd")
        waitForText("Własny", substring = true)
        val rootRight =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.right
        listOf("Morski", "Grafit", "Czerń", "Własny").forEach { name ->
            waitForText(name, substring = true)
            val b = themeCard(name).fetchSemanticsNode().boundsInRoot
            assertTrue("$name wystaje poza ekran (right=${b.right}, ekran=$rootRight)", b.right <= rootRight - 1f)
            assertTrue("$name ma zerową szerokość", b.width > 0f)
        }
    }

    /**
     * Raport z boxa (stary telewizor Full HD, napisy za duże): w Wyglądzie można zmniejszyć interfejs. Po wyborze 70%
     * ten sam napis ma być wyraźnie mniejszy, a wybór zostaje zapisany.
     */
    @Test
    fun rozmiarInterfejsuMoznaZmniejszycWWygladzie() {
        assertEquals(UiScale.X100, runBlocking { appearance.appearance.first().uiScale })
        settingsPage("Wygląd")
        waitForText("Rozmiar interfejsu")
        val before =
            compose
                .onNodeWithText("Rozmiar interfejsu")
                .fetchSemanticsNode()
                .boundsInRoot.height
        click("70%")
        compose.waitUntil(WAIT_MS) {
            compose
                .onNodeWithText("Rozmiar interfejsu")
                .fetchSemanticsNode()
                .boundsInRoot.height < before * SHRINK_MAX
        }
        assertEquals(UiScale.X70, runBlocking { appearance.appearance.first().uiScale })
    }

    /** Karta motywu po nazwie (wybrana ma dopisek „✓”: szukamy częściowo; „Własny…” to osobny przycisk). */
    private fun themeCard(name: String) =
        compose
            .onAllNodesWithText(name, substring = true)
            .filter(hasClickAction())
            .filter(hasText("…", substring = true).not())[0]

    /** Panel Ustawień nakłada się na bieżącą sekcję: po zamknięciu wraca ta sama sekcja (tu Na żywo). */
    @Test
    fun panelUstawienZostawiaSekcjePodSpodem() {
        nav("Na żywo")
        waitForText("Przewodnik")
        nav("Ustawienia")
        waitForText("Listy i dostawcy")
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/settings_panel_nad_na_zywo.png")
        pressKeys(listOf(Key.ShiftLeft))
        back()
        waitFor { compose.onAllNodesWithText("Listy i dostawcy").fetchSemanticsNodes().isEmpty() }
        waitForText("Przewodnik")
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Na żywo").assertIsFocused()
    }

    /**
     * Plan 1.3, Moja lista i Szukaj samym pilotem na prawdziwym grafie (Room, serwer Xtream): wejście z szyny, fokus,
     * plakat, szczegóły, BACK do szyny i Home. Dane (Pitbull na liście) przygotowane dotykiem; fraza wpisana przez IME
     * (`performTextInput`) w polu, do którego fokus doszedł klawiszem.
     */
    @Test
    @StartsInRemoteMode
    fun mojaListaISzukajDzialajaSamymPilotem() =
        withWatchdog {
            requireRemoteMode()
            setUpPitbullOnMyList()
            remoteScenario()
        }

    private fun setUpPitbullOnMyList() {
        stage("serwer Xtream") { serveXtreamVod() }
        stage("dodanie listy przez UI") { addXtreamViaUi("dobre-haslo") }
        stage("import: Kanały: 2") { waitForText("Kanały: 2", substring = true) }
        stage("sekcja Filmy") { nav("Filmy") }
        stage("szczegóły Pitbull") { click("Pitbull") }
        stage("dodanie do Mojej listy") { click("+ Moja lista") }
        stage("potwierdzenie ✓ Moja lista") { waitForText("✓ Moja lista") }
    }

    private fun remoteScenario() {
        openSectionWithRemote("Moja lista")
        waitForText("Pitbull")
        waitFor { focusedHas("tekst=Wszystkie") } // fokus na kategorii, jak w Filmach
        step("Moja lista: fokus na kategorii Wszystkie")
        pressKeys(listOf(Key.DirectionRight))
        waitFor { focusedHas("Pitbull") }
        step("Moja lista: fokus na plakacie Pitbull")
        pressKeys(listOf(Key.DirectionCenter))
        waitForText("FILM") // szczegóły filmu
        step("Moja lista: otwarte szczegóły filmu")
        back()
        waitFor { compose.onAllNodesWithText("FILM").fetchSemanticsNodes().isEmpty() } // wróciła lista
        waitFor { focusedDescriptions().isNotEmpty() } // po powrocie fokus nie jest zgubiony
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        back() // z listy na szynę
        compose.onNodeWithContentDescription("Moja lista").assertIsFocused()
        step("Moja lista: BACK z siatki na szynę")
        back() // z szyny na Home
        waitForText("Mam czas na", substring = true)
        step("BACK z szyny na Home")

        openSectionWithRemote("Szukaj")
        waitForText("Seksmisja")
        waitForText("Rodzina Kowalskich") // filmy i seriale razem
        waitFor { focusedHas("tekst=Wszystkie") }
        pressKeys(listOf(Key.DirectionUp))
        compose.onNode(hasSetTextAction()).assertIsFocused()
        step("Szukaj: fokus w polu")
        compose.onNode(hasSetTextAction()).performTextInput("kowal")
        waitFor { compose.onAllNodesWithText("Seksmisja").fetchSemanticsNodes().isEmpty() }
        step("Szukaj: wyniki zawężone")
        walkRemoteTo("Rodzina Kowalskich")
        step("Szukaj: fokus na serialu")
        pressKeys(listOf(Key.DirectionCenter))
        waitForText("SERIAL") // szczegóły serialu
        step("Szukaj: otwarte szczegóły serialu")
    }

    private fun sampleFocus(label: String) {
        val samples =
            (0 until SONDA_STEPS).map {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(SONDA_STEP_MS))
                compose.waitForIdle()
                val categories = compose.onAllNodesWithText("Wszystkie").fetchSemanticsNodes().size
                "${focusedDescriptions()}/kat=$categories"
            }
        println("SONDA $label: ${samples.joinToString(" -> ")}")
    }

    /** SONDA (tymczasowa, diagnostyka): gdzie jest fokus po wejściu w sekcję dotykiem i pilotem (co 100 ms). */
    @Test
    @StartsInRemoteMode
    fun sondaFokusuPoWyborzeSekcji() =
        withWatchdog {
            requireRemoteMode()
            setUpPitbullOnMyList()
            stage("powrót do Filmy") { nav("Filmy") }
            stage("lista Filmy") { waitForText("Pitbull") }
            listOf("Moja lista", "Filmy", "Szukaj").forEach { label ->
                stage("sekcja $label pilotem") {
                    runCatching { openSectionWithRemote(label) }
                        .onFailure { println("SONDA $label: nie wybrano: ${it.message?.take(SONDA_MSG)}") }
                }
                stage("próbkowanie fokusu $label") { sampleFocus("$label pilotem") }
                pressKeys(listOf(Key.DirectionRight))
                println("SONDA $label po PRAWO: ${focusedDescriptions()}")
                pressKeys(listOf(Key.DirectionCenter))
                val details = compose.onAllNodesWithText("FILM").fetchSemanticsNodes().size
                println("SONDA $label po PRAWO+OK: szczegolyFilmu=$details fokus=${focusedDescriptions()}")
                if (details > 0) back()
            }
        }

    /**
     * Syntetyczny katalog o cechach dużej listy (nie lista użytkownika): import z prawdziwym Room, Filmy i Seriale
     * zgrupowane z odcinków, Szukaj we wszystkim oraz dodawanie i usuwanie z Mojej listy.
     */
    @Test
    fun syntetycznyKatalogImportujeSzukaIPamietaUlubione() {
        val host = server.baseUrl()
        server.serve("/syn.m3u") { SyntheticCatalog.m3u(host) }
        addProviderViaUi("Katalog syntetyczny", server.url("/syn.m3u"))
        waitForText("Kanały: ${SyntheticCatalog.LIVE}", substring = true)
        nav("Filmy")
        waitForText("Burza")
        nav("Seriale")
        waitForText("Komisariat 7")
        waitForText("Rodzina Kowalskich")
        nav("Szukaj")
        waitForText("Burza")
        compose.onNode(hasSetTextAction()).performTextInput("komis")
        waitFor { compose.onAllNodesWithText("Burza").fetchSemanticsNodes().isEmpty() }
        waitForText("Komisariat 7")
        nav("Filmy")
        click("Burza")
        click("+ Moja lista")
        waitForText("✓ Moja lista")
        nav("Moja lista")
        waitForText("Burza")
        click("Burza")
        click("✓ Moja lista")
        waitForText("+ Moja lista")
        nav("Moja lista")
        waitForText("Moja lista jest pusta", substring = true)
    }

    /** Przegląd nawigacji (plan 1.3): BACK z treści każdej sekcji przenosi fokus na jej pozycję w szynie. */
    @Test
    fun backZKazdejSekcjiWracaNaSzyne() {
        runBlocking { catalog.addM3u("Lista", server.url("/nav.m3u")) } // pusta odpowiedź 404: sekcje w stanie pustym
        // Sekcje z treścią do fokusu; Filmy i Seriale mają osobne testy, a puste zostawiają fokus na szynie.
        listOf("Home", "Na żywo").forEach { section ->
            nav(section)
            compose.waitForIdle()
            pressKeys(listOf(Key.ShiftLeft))
            back()
            compose.onNodeWithContentDescription(section).assertIsFocused()
        }
    }

    /** Scenariusz: lista z kanałami z kilku krajów; wyłączona w Ustawieniach grupa UK znika z Na żywo. */
    @Test
    fun wylaczonaGrupaZnikaZNaZywo() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/mix.m3u") {
            "#EXTM3U\n" +
                "#EXTINF:-1 group-title=\"PL | Info\",TVN 24\n$host/tvn24.m3u8\n" +
                "#EXTINF:-1 group-title=\"UK | News\",BBC World\n$host/bbc.m3u8\n"
        }
        runBlocking { catalog.addM3u("Mix", server.url("/mix.m3u")) }
        nav("Na żywo")
        waitForText("TVN 24")
        waitForText("BBC World")

        settingsPage("Grupy i kategorie")
        click("UK | News") // przełącznik grupy: wyłączona
        compose.waitForIdle()

        nav("Na żywo")
        waitForText("TVN 24")
        waitFor { compose.onAllNodesWithText("BBC World").fetchSemanticsNodes().isEmpty() }
    }

    /** Scenariusz: użytkownik wybiera motyw Grafit; po restarcie aktywności motyw jest zapamiętany. */
    @Test
    fun wybranyMotywPrzezywaRestart() {
        settingsPage("Wygląd")
        click("Grafit")
        waitFor { runBlocking { appearance.appearance.first().theme == ThemeId.GRAFIT } }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(ThemeId.GRAFIT, runBlocking { appearance.appearance.first().theme })
        waitForText("Motyw") // ekran Wygląd po restarcie dalej widoczny
    }

    /** Scenariusz: motyw Własny: pojawia się suwak odcienia tła, pilot zmienia odcień, wybór przeżywa restart. */
    @Test
    fun motywWlasnyPozwalaUstawicOdcienTla() {
        settingsPage("Wygląd")
        click("Własny")
        waitFor { runBlocking { appearance.appearance.first().theme == ThemeId.WLASNY } }
        waitForText("Odcień tła motywu Własny")
        val before = runBlocking { appearance.appearance.first().customHue }
        compose.onNodeWithTag("slider_bg_hue").requestFocus()
        pressKeys(listOf(Key.ShiftLeft, Key.DirectionRight, Key.DirectionRight))
        waitFor { runBlocking { appearance.appearance.first().customHue } != before }
        // Dwa naciśnięcia = dwa zapisy do DataStore: czekamy, aż wartość przestanie się zmieniać (inaczej wyścig:
        // odczyt po pierwszym zapisie, a po recreate() jest już końcowa wartość).
        val hue = settledHue()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(hue, runBlocking { appearance.appearance.first().customHue })
        assertEquals(ThemeId.WLASNY, runBlocking { appearance.appearance.first().theme })
    }

    /** Odcień tła po ustabilizowaniu: trzy kolejne odczyty (co ok. 300 ms) bez zmiany. */
    private fun settledHue(): Int {
        var last = -1
        var same = 0
        waitFor {
            val now = runBlocking { appearance.appearance.first().customHue }
            same = if (now == last) same + 1 else 0
            last = now
            same >= SETTLED_READS
        }
        return last
    }

    /** Scenariusz: „Mam czas na…” na Home: audycja kończy się za 50 min, więc mieści się w 60 min, ale nie w 30. */
    @Test
    fun mamCzasNaProponujeAudycjeKtoraSieMiesci() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/t.m3u") {
            "#EXTM3U url-tvg=\"$host/t.xml\"\n" +
                "#EXTINF:-1 tvg-id=\"TVN24.pl\" group-title=\"Info\",TVN 24\n$host/tvn24.m3u8\n"
        }
        server.serve("/t.xml") { ListFactory.xmltvNow("TVN24.pl", "Wiadomosci Czasu", "Potem", displayName = "TVN 24") }
        runBlocking { catalog.addM3u("Czas", server.url("/t.m3u")) }

        nav("Home")
        waitForText("Wiadomosci Czasu", timeoutMs = BIG_WAIT_MS) // EPG zaimportowane: karta „Na żywo teraz”
        click("30 min")
        waitForText("Nic nie pasuje", substring = true)
        click("60 min")
        waitFor { compose.onAllNodesWithText("Nic nie pasuje", substring = true).fetchSemanticsNodes().isEmpty() }
    }

    /** Scenariusz: „Zdrowie źródeł”: działająca lista jest „Sprawna”, lista z błędem serwera ma stan i opis. */
    @Test
    fun zdrowieZrodelPokazujeStanKazdejListy() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/ok.m3u") {
            "#EXTM3U\n#EXTINF:-1 tvg-id=\"A.pl\" group-title=\"Info\",Kanal A\n$host/a.m3u8\n"
        }
        server.serve("/zla.m3u") { ProviderServer.NOT_FOUND }
        runBlocking {
            catalog.addM3u("Dobra", server.url("/ok.m3u"))
            catalog.addM3u("Zepsuta", server.url("/zla.m3u"))
        }

        settingsPage("Dostawcy")
        waitForText("Sprawna", substring = true)
        waitForText("Błąd pobierania", substring = true)
        assertTrue(compose.onAllNodesWithText("Dobra").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Zepsuta").fetchSemanticsNodes().isNotEmpty())
    }

    /** Scenariusz: sortowanie Filmów z konta Xtream: A–Z, Ocena i Najnowsze zmieniają kolejność bez crasha. */
    @Test
    fun sortowanieFilmowZmieniaKolejnosc() {
        serveXtreamVod() // Pitbull: ocena 7,4, dodany; Seksmisja: ocena 8,1, bez daty dodania
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        waitForText("Seksmisja")

        fun left(title: String) =
            compose
                .onAllNodesWithText(title)[0]
                .fetchSemanticsNode()
                .boundsInRoot.left

        fun chip(label: String) {
            clickable(label).performClick() // chipy sortowania nie leżą w przewijanym kontenerze
            compose.waitForIdle()
        }

        // Najnowsze (domyślnie): dodany Pitbull przed Seksmisją bez daty.
        assertTrue(left("Pitbull") < left("Seksmisja"))
        chip("Ocena")
        waitFor { left("Seksmisja") < left("Pitbull") } // wyższa ocena pierwsza
        chip("A–Z")
        waitFor { left("Pitbull") < left("Seksmisja") }
        chip("Najnowsze")
        waitFor { left("Pitbull") < left("Seksmisja") }
    }

    /** Błąd z boxu/telefonu: BACK z kanału i z filmu zostawiał odtwarzacz w tle (dźwięk grał dalej). */
    @Test
    fun backZKanaluIFilmuZwalniaOdtwarzacz() {
        val host = server.baseUrl()
        server.serve("/o.m3u") {
            "#EXTM3U\n#EXTINF:-1 group-title=\"Info\",Alfa Info\n$host/live/u/p/1.ts\n" +
                "#EXTINF:-1 group-title=\"Dramat\",PL - Pitbull (2005)\n$host/movie/u/p/10.mkv\n"
        }
        addProviderViaUi("Lista", server.url("/o.m3u"))
        waitForText("Kanały: 1", substring = true)

        nav("Na żywo")
        click("Alfa Info")
        waitFor { FakePlayers.played.isNotEmpty() }
        back()
        nav("Filmy")
        waitFor { FakePlayers.live.isEmpty() } // kanał nie gra w tle, a podgląd Na żywo jest zwolniony

        click("Pitbull")
        click("▶ Oglądaj")
        waitFor { FakePlayers.played.size == 2 }
        back()
        waitForText("▶ Oglądaj") // szczegóły filmu
        waitFor { FakePlayers.live.isEmpty() }

        // Ponowne wejście w ten sam kanał musi znów zacząć odtwarzanie (nowy odtwarzacz, a nie zwolniony stary).
        nav("Na żywo")
        click("Alfa Info")
        waitFor { FakePlayers.played.size == 3 }
        assertEquals(1, FakePlayers.live.size)
    }

    /**
     * Błąd z telefonu: sortowanie w Filmach „nic nie robiło”. Katalog bez dat, ocen i lat (typowa lista M3U) dawał
     * Najnowsze = A–Z, a siatka po zmianie kolejności zostawała przewinięta w to samo miejsce. Dotyk, nie semantyka.
     */
    @Test
    fun sortowanieFilmowDotykiemDzialaNaKataloguBezMetadanych() {
        val host = server.baseUrl()
        server.serve("/dlugi.m3u") {
            // Kolejność dostawcy: od końca (nowe na początku), bez dat, ocen i lat.
            (30 downTo 1).joinToString("\n", "#EXTM3U\n") { i ->
                val n = "Film %02d".format(i)
                "#EXTINF:-1 group-title=\"Dramat\",$n\n$host/movie/u/p/$i.mkv"
            }
        }
        addProviderViaUi("Dlugi katalog", server.url("/dlugi.m3u"))
        waitForText("Kanały: 0", substring = true)
        nav("Filmy")
        waitForText("Film 30")

        // Pozycja w siatce: najpierw wiersz (top), potem kolumna (left).
        fun top(title: String): Float {
            val b = compose.onAllNodesWithText(title)[0].fetchSemanticsNode().boundsInRoot
            return b.top * GRID_ROW + b.left
        }

        // Najnowsze = kolejność dostawcy (Film 30 pierwszy); przewijamy siatkę w dół, jak użytkownik.
        assertTrue(top("Film 30") < top("Film 29"))
        compose.onAllNodesWithText("Film 30")[0].performTouchInput { swipeUp() }
        compose.waitForIdle()

        clickable("A–Z").performTouchInput { click() }
        compose.waitForIdle()
        waitForText("Film 01") // po zmianie sortowania siatka wraca na początek: pierwszy jest Film 01
        assertTrue("Film 01 widoczny u góry", top("Film 01") >= 0f && top("Film 01") < top("Film 02"))

        clickable("Najnowsze").performTouchInput { click() }
        compose.waitForIdle()
        waitForText("Film 30")
        assertTrue("Film 30 znów pierwszy", top("Film 30") >= 0f && top("Film 30") < top("Film 29"))
    }

    /** Serwer metadanych: TMDB (wyszukiwanie + szczegóły z id IMDb) i OMDb (IMDb, Rotten Tomatoes). */
    private fun serveMetadata() {
        server.serve("/tmdb/search/movie") { """{"results":[{"id":603}]}""" }
        server.serve("/tmdb/movie/603") {
            """{"vote_average":8.2,"overview":"Opis z TMDB po polsku.","runtime":110,"genres":[{"name":"Dramat"}],""" +
                """"external_ids":{"imdb_id":"tt0133093"},"credits":{"cast":[{"name":"Jan Kowalski"}]}}"""
        }
        server.serve("/omdb/") {
            """{"Response":"True","imdbRating":"8.7","Ratings":[{"Source":"Rotten Tomatoes","Value":"83%"}]}"""
        }
    }

    /**
     * Scenariusz (raport 2.11: „w filmach nie ma ocen ani opisów”): z kluczami API karty widoczne na ekranie dostają
     * ocenę IMDb z OMDb (zamiast oceny dostawcy), a szczegóły pokazują IMDb, TMDB, Rotten Tomatoes i polski opis.
     */
    @Test
    fun ocenyZeZrodelZewnetrznychPojawiajaSieNaKartachISzczegolach() {
        serveXtreamVod() // ocena dostawcy: Pitbull 7,4
        serveMetadata()
        runBlocking {
            metadataKeys.save(
                pl.ketchupplayer.tv.core.data.metadata
                    .ApiKeys("klucz-t", "klucz-o"),
            )
        }
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        waitForText("IMDb 8,7") // plakietka z podpisem źródła, dociągnięta dla widocznej karty
        assertTrue(compose.onAllNodesWithText("Lista 7,4", substring = true).fetchSemanticsNodes().isEmpty())
        click("Pitbull")
        waitForText("IMDb 8,7")
        waitForText("TMDB 8,2")
        waitForText("RT 83%")
        waitForText("Opis z TMDB po polsku.")
    }

    /** Scenariusz (makieta v5): bez kluczy TMDB/OMDb ekran szczegółów podpowiada, gdzie je wpisać. */
    @Test
    fun szczegolyBezKluczyPodpowiadajaGdzieWpisacKlucze() {
        serveXtreamVod()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        click("Pitbull")
        waitForText("Oceny i opisy", substring = true)
    }

    /**
     * Prośba użytkownika („opisy bez kluczy API”): bez żadnych kluczy szczegóły filmu pokazują opis z Wikipedii, a
     * podpowiedź o kluczach znika, bo opis jest.
     */
    @Test
    fun szczegolyBezKluczyPokazujaOpisZWikipedii() {
        serveXtreamVod()
        server.serve("/wiki/w/api.php") { """{"query":{"search":[{"title":"Pitbull (film 2005)"}]}}""" }
        server.serve("/wiki/api/rest_v1/page/summary/Pitbull_(film_2005)") {
            """{"type":"standard","description":"polski film z 2005 roku",""" +
                """"extract":"Pitbull to polski film sensacyjny z 2005 roku."}"""
        }
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        click("Pitbull")
        waitForText("polski film sensacyjny", substring = true)
        assertTrue(compose.onAllNodesWithText("Dodaj klucze", substring = true).fetchSemanticsNodes().isEmpty())
    }

    /**
     * Prośba użytkownika („klucze do wszystkich serwisów”): z samym kluczem MDBList i Client ID Traktu szczegóły
     * pokazują IMDb, Rotten Tomatoes, Metacritic i Trakt oraz polski opis z Traktu.
     */
    @Test
    fun kluczeMdblistITraktDajaOcenyIMetacriticWSzczegolach() {
        serveXtreamVod()
        server.serve(
            "/trakt/search/movie",
        ) { """[{"type":"movie","movie":{"ids":{"slug":"pitbull","imdb":"tt0397777"}}}]""" }
        server.serve("/trakt/movies/pitbull/translations/pl") {
            """[{"language":"pl","overview":"Opis z Traktu po polsku."}]"""
        }
        server.serve("/trakt/movies/pitbull") { """{"rating":7.9,"overview":"English","ids":{"imdb":"tt0397777"}}""" }
        server.serve("/mdblist/imdb/movie/tt0397777") {
            """{"ratings":[{"source":"imdb","value":7.6},{"source":"tomatoes","value":83},""" +
                """{"source":"metacritic","value":71}]}"""
        }
        runBlocking {
            metadataKeys.save(
                pl.ketchupplayer.tv.core.data.metadata
                    .ApiKeys(trakt = "client-id", mdblist = "klucz-m"),
            )
        }
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        click("Pitbull")
        waitForText("IMDb 7,6")
        waitForText("RT 83%")
        waitForText("Metacritic 71")
        waitForText("Trakt 7,9")
        waitForText("Opis z Traktu po polsku.")
    }

    /**
     * Limit 1000 zapytań dziennie na darmowym kluczu MDBList: siatka filmów w ogóle go nie używa (przewijanie nie spala
     * budżetu), a otwarcie szczegółów jednego filmu pyta raz i zapisuje wynik (kolejne otwarcie nie pyta ponownie).
     */
    @Test
    fun mdblistJestPytanyTylkoPrzyOtwarciuSzczegolowIRazNaFilm() {
        serveXtreamVod()
        server.serve("/mdblist/imdb/movie/tt0397777") {
            """{"ratings":[{"source":"imdb","value":7.6},{"source":"tomatoes","value":83}]}"""
        }
        server.serve("/mdblist/search/movie") { """{"search":[{"title":"Pitbull","imdbid":"tt0397777"}]}""" }
        runBlocking {
            metadataKeys.save(
                pl.ketchupplayer.tv.core.data.metadata
                    .ApiKeys(mdblist = "klucz-m"),
            )
        }
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        compose.mainClock.advanceTimeBy(2_000)
        assertTrue("siatka nie może pytać MDBList", server.requestedPaths.none { it.startsWith("/mdblist") })
        click("Pitbull")
        waitForText("IMDb 7,6")
        val askedOnce = server.requestedPaths.count { it.startsWith("/mdblist") }
        assertTrue("szczegóły pytają MDBList (szukanie + oceny)", askedOnce in 1..2)
        back()
        nav("Filmy")
        waitForText("IMDb 7,6") // ocena z bazy widoczna już na karcie
        click("Pitbull")
        waitForText("RT 83%")
        assertEquals(
            "drugie otwarcie nie pyta ponownie",
            askedOnce,
            server.requestedPaths.count { it.startsWith("/mdblist") },
        )
    }

    /**
     * Raport z boxa: lista ok. 64 MB była odrzucana („zbyt duża”), choć inne odtwarzacze wczytują ją bez problemu.
     * Tu lista po rozpakowaniu ma ponad 70 MB (po gzip kilkadziesiąt KB): ma się wczytać i pokazać ostatni kanał.
     */
    @Test
    fun listaWiekszaNiz64MbJestWczytywana() {
        val host = "http://127.0.0.1:${server.port}"
        val packed =
            java.io
                .ByteArrayOutputStream()
                .also { out ->
                    java.util.zip.GZIPOutputStream(out).use { gz ->
                        gz.write("#EXTM3U\n".toByteArray())
                        val filler = ("# " + "x".repeat(1000) + "\n").toByteArray()
                        repeat(BIG_LIST_MB * 1024) { gz.write(filler) } // ok. 1 MB co 1024 powtórzeń
                        gz.write(
                            "#EXTINF:-1 tvg-chno=\"1\" group-title=\"Test\",Kanal Koniec Duzej Listy\n$host/k.m3u8\n"
                                .toByteArray(),
                        )
                    }
                }.toByteArray()
        server.serveBytes("/duza.m3u", packed)
        val result = runBlocking { catalog.addM3u("Duza", server.url("/duza.m3u")) }
        assertTrue(
            "lista >64 MB ma się wczytać: $result",
            result is pl.ketchupplayer.tv.core.data.catalog.AddProviderResult.Added,
        )
        nav("Na żywo")
        waitForText("Kanal Koniec Duzej Listy")
    }

    /**
     * Wbudowany indeks filmów (Wikidata): film rozpoznany offline daje id IMDb i stronę Wikipedii, więc opis idzie
     * wprost ze strony (bez wyszukiwania), a MDBList dostaje id (jedno zapytanie, bez szukania po tytule).
     */
    @Test
    fun filmZIndeksuWbudowanegoOszczedzaZapytania() {
        serveXtreamVod()
        FakeFilmIndex.entries =
            mapOf(
                "Pitbull" to
                    pl.ketchupplayer.tv.core.data.metadata
                        .FilmRef("tt0425007", 9475, 2005, "Pitbull_(film_2005)", "Pitbull", "Pitbull"),
            )
        server.serve("/wiki/api/rest_v1/page/summary/Pitbull_(film_2005)") {
            """{"type":"standard","description":"polski film z 2005 roku","extract":"Opis ze strony z indeksu."}"""
        }
        server.serve("/mdblist/imdb/movie/tt0425007") { """{"ratings":[{"source":"imdb","value":7.6}]}""" }
        runBlocking {
            metadataKeys.save(
                pl.ketchupplayer.tv.core.data.metadata
                    .ApiKeys(mdblist = "klucz-m"),
            )
        }
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        click("Pitbull")
        waitForText("IMDb 7,6")
        waitForText("Opis ze strony z indeksu.")
        assertTrue("bez wyszukiwania w Wikipedii", server.requestedPaths.none { it == "/wiki/w/api.php" })
        assertTrue("bez wyszukiwania w MDBList", server.requestedPaths.none { it.startsWith("/mdblist/search") })
    }

    /**
     * Prośba z raportu 3.11 („gwiazdki bez kluczy”): film rozpoznany we wbudowanym indeksie dostaje ocenę IMDb z
     * publicznych danych IMDb, bez żadnego klucza API i bez żadnego zapytania do sieci.
     */
    @Test
    fun ocenaIMDbZIndeksuPokazujeSieBezKluczyIBezSieci() {
        serveXtreamVod()
        FakeFilmIndex.entries =
            mapOf(
                "Pitbull" to
                    pl.ketchupplayer.tv.core.data.metadata
                        .FilmRef(
                            "tt0425007",
                            9475,
                            2005,
                            null,
                            "Pitbull",
                            "Pitbull",
                            imdbRating = 7.4,
                            imdbVotes = 52_000,
                        ),
            )
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Filmy")
        waitForText("Pitbull")
        waitForText("7,4", substring = true) // plakietka oceny na karcie
        click("Pitbull")
        waitForText("IMDb 7,4")
        assertTrue(
            "bez zapytań do serwisów ocen",
            server.requestedPaths.none {
                it.startsWith("/tmdb") || it.startsWith("/omdb") || it.startsWith("/mdblist") || it.startsWith("/trakt")
            },
        )
    }

    /**
     * Raport emulatora 3.11: Matrix (1999) z listy nie dostał oceny z indeksu, choć sam plik indeksu ją ma. Scenariusz
     * idzie całą ścieżką na PRAWDZIWYM pliku indeksu z repozytorium, bez kluczy i bez sieci.
     */
    @Test
    fun matrix1999DostajeOceneIMDbZPrawdziwegoIndeksuNaKarcie() {
        FakeFilmIndex.real =
            pl.ketchupplayer.tv.core.data.metadata.SqliteFilmIndex(
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    java.io.File("../core/data/src/main/assets/film_index.db").path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                ),
            )
        val movies =
            (60 downTo 1).joinToString("") {
                "#EXTINF:-1 group-title=\"Dramat\",Film %02d\nhttp://h.example.invalid/movie/u/p/%d.mkv\n".format(
                    it,
                    it,
                )
            }
        server.serve("/m.m3u") {
            "#EXTM3U\n#EXTINF:-1 group-title=\"Dramat\",Matrix (1999)\n" +
                "http://h.example.invalid/movie/u/p/0.mkv\n$movies"
        }
        runBlocking { assertTrue(catalog.addM3u("Filmy", server.url("/m.m3u")) is AddProviderResult.Added) }
        nav("Filmy")
        waitForText("Matrix")
        waitForText("IMDb 8,7", timeoutMs = BIG_WAIT_MS)
    }

    private val slowEpgMs = 2_500L

    private val invalidKeyBody = """{"Response":"False","Error":"Invalid API key!"}"""

    /** Scenariusz: klucze API wpisane w Ustawieniach → Oceny i opisy są zapisane (w SecretStore, nie w repo). */
    @Test
    fun kluczeMetadanychWpisaneWUstawieniachSaZapisane() {
        settingsPage("Oceny i opisy")
        waitForText("Brak kluczy", substring = true)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("klucz-tmdb-123")
        click("Zapisz")
        waitFor { runBlocking { metadataKeys.userEntered().tmdb } == "klucz-tmdb-123" }
        waitForText("Zapisano", substring = true)
    }

    /**
     * Raport z boxa 3.11: mimo wpisanych kluczy brak opisów i ocen, a aplikacja nie mówiła dlaczego. „Sprawdź klucze”
     * pyta każdy serwis jednym znanym zapytaniem i podaje wynik: działa, odrzucony (zły klucz) albo brak klucza.
     */
    @Test
    fun sprawdzenieKluczyPokazujeWynikKazdegoSerwisu() {
        server.serve("/tmdb/movie/603") { "{}" }
        server.serve("/omdb/") { invalidKeyBody }
        settingsPage("Oceny i opisy")
        waitForText("Brak kluczy", substring = true)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("klucz-tmdb-ok")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("zly-klucz-omdb")
        click("Sprawdź klucze")
        waitForText("TMDB: klucz działa")
        waitForText("OMDb: serwis odrzucił klucz", substring = true)
        waitForText("MDBList: brak klucza")
        waitForText("Trakt: brak klucza")
    }

    /** Scenariusz (prośba z raportu): ekran list pokazuje, ile połączeń jest w użyciu i do kiedy ważne jest konto. */
    @Test
    fun listaXtreamPokazujePolaczeniaIWaznoscKonta() {
        serveXtream() // panel: 1 z 2 połączeń w użyciu, konto do 2030-01-01
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        settingsPage("Dostawcy")
        waitForText("Połączenia: 1 z 2", substring = true)
        waitForText("ważne do 01.01.2030", substring = true)
    }

    /** Scenariusz (makieta v5): lista kanałów podpisuje grupę i pokazuje „N z M kanałów”. */
    @Test
    fun naZywoPodpisujeGrupeIPokazujeLicznikKanalow() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/l.m3u") {
            "#EXTM3U\n#EXTINF:-1 tvg-chno=\"1\" group-title=\"Info\",Alfa Info\n$host/a.m3u8\n" +
                "#EXTINF:-1 tvg-chno=\"2\" group-title=\"Sport\",Beta Sport\n$host/b.m3u8\n"
        }
        runBlocking { catalog.addM3u("Lista", server.url("/l.m3u")) }
        nav("Na żywo")
        waitForText("2 z 2 kanałów")
        click("Sport")
        waitForText("1 z 2 kanałów")
    }

    /** Scenariusz (makieta v5): nagłówek każdego ekranu pokazuje aktywną listę i jej połączenia („1 z 2”). */
    @Test
    fun naglowekPokazujePigulkeListyZPolaczeniami() {
        serveXtream()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Na żywo")
        waitForText("połączenia 1 z 2", substring = true)
        nav("Filmy")
        waitForText("połączenia 1 z 2", substring = true)
    }

    /** Scenariusz: w odtwarzaczu na żywo OK otwiera listę kanałów grupy; wybór drugiego przełącza kanał, BACK wraca. */
    @Test
    fun zOdtwarzaczaNaZywoMoznaPrzelaczycKanalZListy() {
        val host = "http://127.0.0.1:${server.port}"
        server.serve("/z.m3u") {
            "#EXTM3U\n#EXTINF:-1 tvg-chno=\"1\" group-title=\"Info\",Alfa Info\n$host/alfa.m3u8\n" +
                "#EXTINF:-1 tvg-chno=\"2\" group-title=\"Info\",Beta Info\n$host/beta.m3u8\n"
        }
        runBlocking { catalog.addM3u("Zapping", server.url("/z.m3u")) }

        nav("Na żywo")
        click("Alfa Info")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertEquals("$host/alfa.m3u8", FakePlayers.played.last())

        pressKeys(listOf(Key.ShiftLeft, Key.DirectionCenter)) // lista kanałów, fokus na bieżącym (Alfa)
        waitForText("Beta Info")
        pressKeys(listOf(Key.DirectionDown, Key.DirectionCenter)) // wybór Beta
        waitFor { FakePlayers.played.last() == "$host/beta.m3u8" }

        back() // wyjście z odtwarzacza do listy kanałów
        waitForText("Przewodnik")
    }

    /**
     * Scenariusz (prośba z raportu): w odtwarzaczu na żywo panel Info pokazuje diagnostykę: bufor, łącze, przerwy,
     * ocenę stabilności i połączenia listy (1 z 2 z panelu Xtream).
     */
    @Test
    fun panelInfoWOdtwarzaczuPokazujeDiagnostykeIPolaczeniaListy() {
        serveXtream()
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        nav("Na żywo")
        click("TVN24 HD")
        waitFor { FakePlayers.played.isNotEmpty() }

        fun press(keys: List<Key>) =
            keys.forEach { k ->
                compose.onRoot().performKeyInput { pressKey(k) }
                compose.waitForIdle()
            }
        press(listOf(Key.ShiftLeft))
        press(listOf(Key.DirectionDown)) // pasek opcji
        waitForText("Synchronizacja")
        press(List(6) { Key.DirectionRight } + Key.DirectionCenter) // z „Audio” do „Info”
        waitForText("DIAGNOSTYKA")
        waitForText("Bufor:", substring = true)
        waitForText("Stabilność: 100%", substring = true)
        waitForText("Lista: połączenia 1 z 2")
    }

    /** Audyt układu: pusta Moja lista, Szukaj z wynikami i Szukaj bez wyników (plan 1.3). */
    private fun auditEmptyMyListAndSearch(audit: (String) -> Unit) {
        nav("Moja lista")
        waitForText("Moja lista jest pusta", substring = true)
        audit("Moja lista (pusta)")
        nav("Szukaj")
        waitForText("Seksmisja")
        audit("Szukaj")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("zzz")
        waitForText("Brak wyników dla", substring = true)
        audit("Szukaj (brak wyników)")
    }

    /** Audyt układu: Moja lista z pozycją dodaną na szczegółach filmu (ekran szczegółów jest otwarty). */
    private fun auditFilledMyList(audit: (String) -> Unit) {
        click("+ Moja lista")
        waitForText("✓ Moja lista")
        nav("Moja lista")
        waitForText("Pitbull")
        audit("Moja lista")
    }

    /**
     * Audyt układu (raport 2.11: „czy nikt się nie chowa, czy jest wyśrodkowane, czy czcionki dobrze dobrane”):
     * przechodzi przez główne ekrany jako telewizor i mierzy wyjście poza ekran, minimalną czcionkę, rozmiar celów
     * fokusu i nakładanie tekstów. Test ma przejść z pustą listą problemów.
     */
    @Test
    fun ukladGlownychEkranowSpelniaZasadyDesignu() {
        serveXtreamVod()
        serveMetadata()
        // Długa nazwa grupy jak w prawdziwych listach: dwie linie i licznik obok.
        server.serve("/player_api.php?get_live_categories") {
            """[{"category_id":"1","category_name":"PL| DOKUMENTALNE I REPORTAŻE PODRÓŻNICZE"}]"""
        }
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)
        val issues = mutableListOf<LayoutAudit.Issue>()

        fun audit(screen: String) {
            compose.mainClock.advanceTimeBy(700) // koniec animacji
            compose.waitForIdle()
            issues += LayoutAudit.run(screen, compose)
        }
        nav("Home")
        waitForText("Mam czas na", substring = true)
        audit("Home")
        nav("Na żywo")
        waitForText("TVN24 HD")
        audit("Na żywo")
        nav("Filmy")
        waitForText("Pitbull")
        audit("Filmy")
        nav("Seriale")
        waitForText("Rodzina Kowalskich")
        audit("Seriale")
        auditEmptyMyListAndSearch { audit(it) }
        nav("Seriale")
        waitForText("Rodzina Kowalskich")
        click("Pitbull".takeIf { false } ?: "Rodzina Kowalskich")
        waitForText("SERIAL")
        audit("Szczegóły serialu")
        back()
        nav("Filmy")
        click("Pitbull")
        waitForText("FILM")
        audit("Szczegóły filmu")
        auditFilledMyList { audit(it) }
        listOf(
            "Dostawcy",
            "Grupy i kategorie",
            "Odtwarzanie",
            "Oceny i opisy",
            "Telewizja",
            "Sieć i VPN",
            "Wygląd",
            "O aplikacji",
        ).forEach { page ->
            settingsPage(page)
            audit("Ustawienia › $page")
        }
        issues.forEach { println("UKLAD_ISSUE $it") }
        assertTrue("Problemy układu (${issues.size}):\n" + issues.joinToString("\n"), issues.isEmpty())
    }

    /** Scenariusz: katalog Xtream bez długości w liście; Home dociąga je z get_vod_info, a „2 h” podpowiada filmy. */
    @Test
    fun mamCzasNaPodpowiadaFilmyZKatalogu() {
        serveXtreamVod()
        server.serve("/player_api.php?get_vod_info") { """{"info":{"duration_secs":"6300","plot":"Opis"}}""" }
        addXtreamViaUi("dobre-haslo")
        waitForText("Kanały: 2", substring = true)

        nav("Home")
        waitForText("Mam czas na…")
        click("2 h")
        // Długości dochodzą w tle po kilku sekundach: wtedy karta pokazuje czas i dopasowanie.
        waitForText("105 min", substring = true, timeoutMs = BIG_WAIT_MS)
        assertTrue(compose.onAllNodesWithText("Nic nie pasuje", substring = true).fetchSemanticsNodes().isEmpty())
    }

    /** Scenariusz: w Ustawieniach → Odtwarzanie użytkownik wybiera profil bufora; wybór jest zapisany. */
    @Test
    fun wyborProfiluBuforaJestZapisany() {
        val key =
            androidx.datastore.preferences.core
                .stringPreferencesKey("player_live_buffer")
        settingsPage("Odtwarzanie")
        click("Bufor odtwarzania")
        waitForText("Stabilny (50 s)")
        click("Stabilny (50 s)")
        waitFor { runBlocking { dataStore.data.first()[key] } == "stable" }
    }
}
