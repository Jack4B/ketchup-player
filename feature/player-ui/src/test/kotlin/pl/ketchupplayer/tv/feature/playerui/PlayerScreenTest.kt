package pl.ketchupplayer.tv.feature.playerui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.common.Player
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.player.engine.PlaybackStatus
import pl.ketchupplayer.tv.player.engine.PlayerController
import pl.ketchupplayer.tv.player.engine.PlayerState
import pl.ketchupplayer.tv.player.engine.TrackInfo
import pl.ketchupplayer.tv.player.engine.TrackOption

internal class FakeController(
    initial: PlayerState = PlayerState(),
    trackInfo: TrackInfo = TrackInfo(),
) : PlayerController {
    override val state: StateFlow<PlayerState> = MutableStateFlow(initial)
    override val player: Player? = null
    val played = mutableListOf<StreamSpec>()
    private val trackFlow = MutableStateFlow(trackInfo)
    override val tracks: StateFlow<TrackInfo> = trackFlow
    val calls = mutableListOf<String>()

    override fun selectAudio(id: String) {
        calls += "audio:$id"
    }

    override fun selectText(id: String?) {
        calls += "text:$id"
    }

    override fun setSpeed(speed: Float) {
        calls += "speed:$speed"
    }

    override fun setAudioDelayMs(ms: Int) {
        calls += "audioDelay:$ms"
        trackFlow.value = trackFlow.value.copy(audioDelayMs = ms)
    }

    override fun setSubtitleDelayMs(ms: Int) {
        calls += "subDelay:$ms"
    }

    override fun play(spec: StreamSpec) {
        played += spec
    }

    var pauses = 0
    var resumes = 0

    override fun pause() {
        pauses++
    }

    override fun resume() {
        resumes++
    }

    override fun stop() = Unit

    override fun release() = Unit
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PlayerScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        ui: PlayerUiState,
        controller: PlayerController = FakeController(),
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
        vodTitle: String? = null,
        showClock: Boolean = true,
        vodInfo: VodInfoUi? = null,
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                PlayerScreen(
                    ui,
                    controller,
                    onRetry,
                    onBack,
                    vodTitle = vodTitle,
                    showClock = showClock,
                    vodInfo = vodInfo,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) {
        compose.onRoot().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    @Test
    fun screenshotLaczenia() {
        show(PlayerUiState.Resolving("JEDEN HD"))
        compose.onRoot().captureRoboImage("src/test/screenshots/player_resolving.png")
    }

    @Test
    fun screenshotBuforowania() {
        show(PlayerUiState.Playing("JEDEN HD"), FakeController(PlayerState(status = PlaybackStatus.BUFFERING)))
        compose.onRoot().captureRoboImage("src/test/screenshots/player_buffering.png")
    }

    @Test
    fun screenshotBledu() {
        show(PlayerUiState.Failed("JEDEN HD", unknownChannel = false))
        compose.onRoot().captureRoboImage("src/test/screenshots/player_error.png")
    }

    @Test
    fun bladOdtwarzaniaPokazujePanelZPonowieniem() {
        var retries = 0
        show(
            PlayerUiState.Playing("JEDEN HD"),
            FakeController(PlayerState(status = PlaybackStatus.ERROR, errorCode = 2001)),
            onRetry = { retries++ },
        )
        key(Key.DirectionCenter) // fokus startowy na „Spróbuj ponownie”
        assertEquals(1, retries)
    }

    @Test
    fun dpadWPanelBleduPrawoToWroc() {
        var backs = 0
        show(PlayerUiState.Failed("X", unknownChannel = false), onBack = { backs++ })
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(1, backs)
    }

    @Test
    fun backWychodziZOdtwarzacza() {
        var backs = 0
        show(PlayerUiState.Playing("JEDEN HD"), onBack = { backs++ })
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }

    private val multiTrack =
        TrackInfo(
            audio =
                listOf(
                    TrackOption("1:0", "Polski · AC3 5.1", true),
                    TrackOption("2:0", "Angielski · AAC stereo", false),
                ),
            text = listOf(TrackOption("3:0", "Polski", false), TrackOption("4:0", "Angielski", false)),
        )

    private fun vod(controller: FakeController) =
        show(
            PlayerUiState.Playing("Film"),
            controller,
            vodTitle = "Film",
        )

    private fun has(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** DÓŁ otwiera pasek opcji, OK na „Audio” panel ze ścieżkami, DÓŁ + OK wybiera drugą ścieżkę. */
    @Test
    fun wyborSciezkiAudioPilotem() {
        val c = FakeController(PlayerState(status = PlaybackStatus.READY), multiTrack)
        vod(c)
        key(Key.DirectionDown)
        assertTrue(has("Audio"))
        key(Key.DirectionCenter)
        assertTrue(has("Angielski · AAC stereo"))
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf("audio:2:0"), c.calls)
    }

    @Test
    fun backZamykaPanelPotemPaskPotemWychodzi() {
        var backs = 0
        val c = FakeController(PlayerState(status = PlaybackStatus.READY), multiTrack)
        show(PlayerUiState.Playing("Film"), c, onBack = { backs++ }, vodTitle = "Film")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } // zamyka panel
        compose.waitForIdle()
        assertFalse(has("Angielski · AAC stereo"))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } // zamyka pasek
        compose.waitForIdle()
        assertEquals(0, backs)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } // wychodzi
        assertEquals(1, backs)
    }

    @Test
    fun synchronizacjaStrzalkiZmieniajaOpoznienieDzwieku() {
        val c = FakeController(PlayerState(status = PlaybackStatus.READY), multiTrack)
        vod(c)
        key(Key.DirectionDown)
        key(Key.DirectionRight) // Napisy
        key(Key.DirectionRight) // Synchronizacja
        key(Key.DirectionCenter)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        assertEquals(listOf("audioDelay:50", "audioDelay:100"), c.calls)
    }

    @Test
    fun brakSciezekPokazujeKomunikat() {
        vod(FakeController(PlayerState(status = PlaybackStatus.READY)))
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertTrue(has("Brak innych ścieżek w tym strumieniu"))
    }

    @Test
    fun screenshotPanelAudio() {
        vod(FakeController(PlayerState(status = PlaybackStatus.READY), multiTrack))
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        compose.onRoot().captureRoboImage("src/test/screenshots/player_panel_audio.png")
    }

    @Test
    fun screenshotPaskaOpcji() {
        vod(FakeController(PlayerState(status = PlaybackStatus.READY), multiTrack))
        key(Key.DirectionDown)
        compose.onRoot().captureRoboImage("src/test/screenshots/player_chips.png")
    }

    @Test
    fun screenshotPanelSynchronizacji() {
        vod(FakeController(PlayerState(status = PlaybackStatus.READY), multiTrack))
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        compose.onRoot().captureRoboImage("src/test/screenshots/player_panel_sync.png")
    }

    private val clockRegex = Regex("""\d{2}:\d{2}""")

    private fun hasClock() =
        compose
            .onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher("godzina HH:mm") { node ->
                    node.config
                        .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                        ?.any { clockRegex.matches(it.text) } == true
                },
            ).fetchSemanticsNodes()
            .isNotEmpty()

    /** Zegar w prawym górnym rogu jest widoczny podczas oglądania kanału (nawet po zniknięciu paska OSD). */
    @Test
    fun zegarWidocznyPodczasOgladania() {
        show(PlayerUiState.Playing("JEDEN HD"), FakeController(PlayerState(status = PlaybackStatus.READY)))
        assertTrue(hasClock())
    }

    @Test
    fun zegarMoznaWylaczycUstawieniem() {
        show(
            PlayerUiState.Playing("JEDEN HD"),
            FakeController(PlayerState(status = PlaybackStatus.READY)),
            showClock = false,
        )
        assertFalse(hasClock())
    }

    @Test
    fun screenshotZegara() {
        show(PlayerUiState.Playing("JEDEN HD"), FakeController(PlayerState(status = PlaybackStatus.READY)))
        compose.onRoot().captureRoboImage("src/test/screenshots/player_clock.png")
    }

    private val bigInfo =
        VodInfoUi(
            "W mieście strachu",
            "2026 · Akcja, Kryminał, Dramat · 104 min · ★ 8,4",
            "W momencie kryzysu w Irlandii 18-letnia Elisa, wychowana na marginesie półświatka, zostaje porwana.",
            "Anna Kowalska, Jan Nowak, Maria Wiśniewska",
            "Opis z katalogu dostawcy",
        )

    /** OSD filmu: dolny pas z tytułem, paskiem, czasami i podpowiedziami; karta z opisem na starcie (zrzut). */
    @Test
    fun screenshotOsdFilmuZOpisem() {
        show(
            PlayerUiState.Playing("W mieście strachu"),
            FakeController(PlayerState(status = PlaybackStatus.READY, positionMs = 2_400_000, durationMs = 6_240_000)),
            vodTitle = "W mieście strachu",
            vodInfo = bigInfo,
        )
        compose.onRoot().captureRoboImage("src/test/screenshots/player_vod_osd.png")
    }

    /** GÓRA przywołuje i chowa kartę z opisem filmu (obsada, źródło opisu). */
    @Test
    fun gora_pokazujeIChoweOpisFilmu() {
        show(
            PlayerUiState.Playing("W mieście strachu"),
            FakeController(PlayerState(status = PlaybackStatus.READY, durationMs = 6_000_000)),
            vodTitle = "W mieście strachu",
            vodInfo = bigInfo,
        )
        assertTrue(has("Obsada:")) // na starcie karta jest otwarta
        key(Key.DirectionUp)
        assertFalse(has("Obsada:"))
        key(Key.DirectionUp)
        assertTrue(has("Obsada:"))
        assertTrue(has("Opis z katalogu dostawcy"))
    }

    /** Błąd z boxa: po wyjściu z aplikacji dźwięk grał w tle. ON_STOP wstrzymuje, powrót wznawia. */
    @Test
    fun wyjscieZAplikacjiWstrzymujeOdtwarzanieAPowrotWznawia() {
        val c = FakeController(PlayerState(status = PlaybackStatus.READY, isPlaying = true))
        show(PlayerUiState.Playing("JEDEN HD"), c)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED) // ON_STOP
        compose.waitForIdle()
        assertEquals(1, c.pauses)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(1, c.resumes)
    }

    /** Dłuższe trzymanie klawisza przewijania przyspiesza: 10 s, potem 30, 60 i 90 s na krok. */
    @Test
    fun krokPrzewijaniaRosnieZDlugoscia_trzymania() {
        assertEquals(10_000L, seekStepMs(0))
        assertEquals(10_000L, seekStepMs(5))
        assertEquals(30_000L, seekStepMs(6))
        assertEquals(60_000L, seekStepMs(14))
        assertEquals(90_000L, seekStepMs(28))
        assertEquals(90_000L, seekStepMs(200))
    }
}
