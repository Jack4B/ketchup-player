package pl.ketchupplayer.tv.feature.playerui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
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

    override fun pause() = Unit

    override fun resume() = Unit

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
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { PlayerScreen(ui, controller, onRetry, onBack, vodTitle = vodTitle) }
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
}
