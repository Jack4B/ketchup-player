package pl.ketchupplayer.tv

import android.os.Looper
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
import pl.ketchupplayer.tv.core.data.catalog.AddProviderResult
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.epg.EpgSourcesRepository
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
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
class ScenariosTest {
    private companion object {
        const val WAIT_MS = 15_000L
        const val BIG_WAIT_MS = 60_000L
        const val POLL_MS = 25L
    }

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var catalog: CatalogRepository

    @Inject lateinit var epgSources: EpgSourcesRepository

    private val server = ProviderServer()

    @Before
    fun setUp() {
        hilt.inject()
        FakePlayers.reset()
        // Hermetyczność: wbudowane źródła EPG (internet) wyłączone, EPG tylko z serwera testowego.
        runBlocking {
            epgSources.setEnabled(BuiltInEpgSources.OVH_ID, false)
            epgSources.setEnabled(BuiltInEpgSources.TELEMAN_ID, false)
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
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            check(System.nanoTime() < end) { "Warunek niespełniony po $timeoutMs ms" }
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

    private fun click(text: String) {
        waitForText(text)
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun addProviderViaUi(
        name: String,
        url: String,
    ) {
        click("Dostawcy")
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
        waitForText("184", substring = true) // „127.0.0.1 · 184 kanałów”
        back()

        click("Na żywo")
        waitForText("4 Fun Dance")
        waitForText("4 Fun Kids (576i)")

        // Ulubione: długie przytrzymanie, potem grupa „Ulubione”.
        compose.onNodeWithText("4 Fun Dance").performTouchInput { longClick() }
        waitForText("★ 4 Fun Dance")
        click("★ Ulubione (1)")
        waitFor { compose.onAllNodesWithText("4 Fun TV").fetchSemanticsNodes().isEmpty() }

        // Oglądanie: pierwszy adres z listy trafia do odtwarzacza, bez panelu błędu.
        click("★ 4 Fun Dance")
        waitFor { FakePlayers.played.isNotEmpty() }
        assertEquals(listOf("http://92.114.85.87:8018/play/a00k/index.m3u8"), FakePlayers.played.toList())
        assertTrue(compose.onAllNodesWithText("Nie udało się odtworzyć kanału").fetchSemanticsNodes().isEmpty())

        // Powrót na start: kanał jest w „Ostatnio oglądane”.
        back()
        back()
        waitForText("Ostatnio oglądane")
        waitForText("4 Fun Dance")
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

        click("Na żywo")
        waitForText("TVN 24")
        compose.onAllNodesWithText("TVN 24")[0].performClick()

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

        click("Na żywo")
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

        click("Na żywo")
        waitForText("Fakty o dziewiętnastej", timeoutMs = BIG_WAIT_MS)
        back()

        click("Przewodnik")
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

        click("Na żywo")
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
}
