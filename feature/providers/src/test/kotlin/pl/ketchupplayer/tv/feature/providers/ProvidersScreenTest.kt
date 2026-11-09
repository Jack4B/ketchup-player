package pl.ketchupplayer.tv.feature.providers

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.LocalUiClock
import pl.ketchupplayer.tv.core.designsystem.UiClock
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.RequestMethod
import pl.ketchupplayer.tv.core.testing.assertNoHorizontalOverflow

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ProvidersScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val p1 =
        ProviderInfo(ProviderId("a"), "Domowa lista", ProviderType.M3U, "iptv.example.invalid", 2514, 1L, null)
    private val p2 =
        ProviderInfo(ProviderId("b"), "Zapasowa", ProviderType.M3U, "backup.example.invalid", 0, 2L, "HTTP 503")

    private fun show(
        state: ProvidersUiState,
        events: MutableList<ProvidersEvent> = mutableListOf(),
        clock: UiClock = UiClock.Frozen,
        onBack: () -> Unit = {},
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                CompositionLocalProvider(LocalUiClock provides clock) {
                    ProvidersScreen(state, { events += it }, onBack)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshotPusta() {
        show(ProvidersUiState())
        compose.onRoot().captureRoboImage("src/test/screenshots/providers_empty.png")
        compose.assertNoHorizontalOverflow()
    }

    /** „Odświeżono N dni temu” liczy się od czasu ze źródła ([LocalUiClock]), a nie od zegara systemowego. */
    @Test
    fun wiekOdswiezeniaLiczonyOdZrodlaCzasu() {
        val refreshed = p1.copy(lastRefreshMs = UiClock.Frozen.nowMs() - 3 * MS_PER_DAY)
        show(ProvidersUiState(providers = listOf(refreshed)))
        // Liczba dni pochodzi ze źródła czasu; końcówka („dzień/dni/dnia”) zależy od reguł liczby mnogiej locale testu.
        compose.onNodeWithText("odświeżono 3 ", substring = true).assertExists()
    }

    @Test
    fun screenshotZListaIRozwinieciem() {
        show(ProvidersUiState(providers = listOf(p1, p2), expandedId = p1.id))
        compose.onRoot().captureRoboImage("src/test/screenshots/providers_list.png")
    }

    @Test
    fun screenshotFormularzaZBledem() {
        show(ProvidersUiState(form = ProviderForm(name = "Moja", url = "ftp://x", urlError = true)))
        compose.onRoot().captureRoboImage("src/test/screenshots/providers_form.png")
    }

    @Test
    fun dpadPustaListaFokusNaDodaj() {
        val events = mutableListOf<ProvidersEvent>()
        show(ProvidersUiState(), events)
        key(Key.DirectionCenter)
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.StartAdd), events)
    }

    @Test
    fun dpadPierwszyDostawcaRozwijaAkcje() {
        val events = mutableListOf<ProvidersEvent>()
        show(ProvidersUiState(providers = listOf(p1, p2)), events)
        key(Key.DirectionCenter)
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.Toggle(p1.id)), events)
    }

    @Test
    fun backZamykaFormularzPotemWychodzi() {
        val events = mutableListOf<ProvidersEvent>()
        var backs = 0
        show(ProvidersUiState(form = ProviderForm()), events) { backs++ }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.Cancel), events)
        assertEquals(0, backs)
    }

    @Test
    fun screenshotFormularzaXtream() {
        show(
            ProvidersUiState(
                form =
                    ProviderForm(
                        kind = ProviderKind.XTREAM,
                        name = "Xtream",
                        url = "http://x.example.invalid:8080",
                        username = "jan",
                        password = "haslo",
                        credentialsError = false,
                    ),
            ),
        )
        compose.onRoot().captureRoboImage("src/test/screenshots/providers_form_xtream.png")
        compose.assertNoHorizontalOverflow()
    }

    @Test
    fun wyborTypuXtreamDotykiem() {
        val events = mutableListOf<ProvidersEvent>()
        show(ProvidersUiState(form = ProviderForm()), events)
        compose.onNodeWithText("Xtream Codes").performClick()
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.SelectKind(ProviderKind.XTREAM)), events)
    }

    /** Kreator: wiersz User-Agent otwiera listę wyboru z ptaszkiem; wybór pozycji wysyła zdarzenie z jej id. */
    @Test
    fun wierszUserAgentOtwieraListeWyboru() {
        val events = mutableListOf<ProvidersEvent>()
        show(ProvidersUiState(form = ProviderForm()), events)
        compose.onNodeWithText("User-Agent:", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("✓").assertExists()
        compose.onNodeWithText("VLC").performClick()
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.SelectUserAgent("vlc")), events)
    }

    /** Kreator: pole „Metoda zapytania” (GET domyślnie, POST) działa dotykiem. */
    @Test
    fun wyborMetodyPostDotykiem() {
        val events = mutableListOf<ProvidersEvent>()
        show(ProvidersUiState(form = ProviderForm()), events)
        compose.onNodeWithText("Metoda zapytania", substring = true).assertExists()
        compose.onNodeWithText("POST").performClick()
        compose.onNodeWithText("GET").performClick()
        assertEquals(
            listOf<ProvidersEvent>(
                ProvidersEvent.SelectRequestMethod(RequestMethod.POST),
                ProvidersEvent.SelectRequestMethod(RequestMethod.GET),
            ),
            events,
        )
    }

    /**
     * Kreator: wybór metody D-padem. Okno wychodzi z trybu dotyku jak TV po pierwszym klawiszu pilota
     * (`requestFocusFromTouch`), fokus ląduje na przycisku „POST”, a OK wysyła zdarzenie.
     */
    private fun postPrzezOkNaPrzyciskuPost(kind: ProviderKind) {
        val events = mutableListOf<ProvidersEvent>()
        show(ProvidersUiState(form = ProviderForm(kind = kind)), events)
        compose.runOnUiThread {
            compose.activity.window.decorView
                .requestFocusFromTouch()
        }
        compose.waitForIdle()
        compose.onNodeWithText("POST").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        key(Key.DirectionCenter)
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.SelectRequestMethod(RequestMethod.POST)), events)
    }

    @Test
    fun wyborMetodyPostDpademWFormularzuM3u() = postPrzezOkNaPrzyciskuPost(ProviderKind.M3U)

    @Test
    fun wyborMetodyPostDpademWFormularzuXtream() = postPrzezOkNaPrzyciskuPost(ProviderKind.XTREAM)
}

private const val MS_PER_DAY = 86_400_000L
