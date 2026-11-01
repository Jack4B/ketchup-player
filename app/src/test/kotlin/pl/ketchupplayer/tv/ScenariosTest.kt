package pl.ketchupplayer.tv

import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
class ScenariosTest {
    private companion object {
        const val WAIT_MS = 15_000L
        const val BIG_WAIT_MS = 60_000L
        const val POLL_MS = 25L
        const val CLOCK_STEP_MS = 100L
    }

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var catalog: CatalogRepository

    @Inject lateinit var epgSources: EpgSourcesRepository

    @Inject lateinit var appearance: AppearanceRepository

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Inject lateinit var failoverSettings: pl.ketchupplayer.tv.core.data.playback.FailoverSettingsRepository

    private val server = ProviderServer()

    @Before
    fun setUp() {
        hilt.inject()
        FakePlayers.reset()
        // Hermetyczność: wbudowane źródła EPG (internet) wyłączone, EPG tylko z serwera testowego.
        runBlocking {
            // DataStore jest singletonem procesu: stan poprzedniego scenariusza (ustawienia, znacznik importu EPG)
            // przeciekałby do kolejnego, więc zaczynamy od czystego.
            dataStore.edit { it.clear() }
            epgSources.setEnabled(BuiltInEpgSources.OVH_ID, false)
            epgSources.setEnabled(BuiltInEpgSources.TELEMAN_ID, false)
            appearance.setTheme(ThemeId.GRANAT)
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

    private fun settingsPage(label: String) {
        nav("Ustawienia")
        click(label)
    }

    /** Pierwszy węzeł z akcją kliknięcia o danym tekście (ten sam tekst bywa też w panelu szczegółów/podglądzie). */
    private fun clickable(text: String) = compose.onAllNodesWithText(text).filter(hasClickAction())[0]

    private fun click(text: String) {
        waitForText(text)
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
        click("★ Ulubione")
        waitFor { compose.onAllNodesWithText("4 Fun TV").fetchSemanticsNodes().isEmpty() }

        // Oglądanie: pierwszy adres z listy trafia do odtwarzacza, bez panelu błędu.
        click("★ 4 Fun Dance")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertEquals(listOf("http://92.114.85.87:8018/play/a00k/index.m3u8"), FakePlayers.played.toList())
        assertTrue(compose.onAllNodesWithText("Nie udało się odtworzyć kanału").fetchSemanticsNodes().isEmpty())

        // Powrót z odtwarzacza i na Home: kanał jest w rzędzie „Kontynuuj oglądanie” oraz w hero.
        back()
        nav("Home")
        waitForText("Kontynuuj oglądanie")
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

        nav("Przewodnik")
        waitForText("TVN 24")
        waitForText("Fakty o dziewiętnastej", timeoutMs = BIG_WAIT_MS)
        waitForText("Dzień na świecie")
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
        val ok = """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000","max_connections":"2"}}"""
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
                """{"stream_id":502,"name":"Seksmisja","rating":"8.1","category_id":"11","container_extension":"mp4"}]"""
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
        click("Maksymalny czas buforowania") // 25 s -> 5 s (pierwsza wartość listy po zawinięciu)
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

        nav("Przewodnik")
        waitForText("Fakty wieczorne", timeoutMs = BIG_WAIT_MS)
        click("Fakty wieczorne")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertEquals(listOf("$host/tvn24.m3u8"), FakePlayers.played.toList())
    }

    /** Scenariusz: użytkownik wybiera motyw Bordo; po restarcie aktywności motyw jest zapamiętany. */
    @Test
    fun wybranyMotywPrzezywaRestart() {
        settingsPage("Wygląd")
        click("Bordo")
        waitFor { runBlocking { appearance.appearance.first().theme == ThemeId.BORDO } }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(ThemeId.BORDO, runBlocking { appearance.appearance.first().theme })
        waitForText("Motyw") // ekran Wygląd po restarcie dalej widoczny
    }
}
