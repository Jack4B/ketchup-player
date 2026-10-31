package pl.ketchupplayer.tv.feature.playerui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.media3.common.Player
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
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

internal class FakeController(
    initial: PlayerState = PlayerState(),
) : PlayerController {
    override val state: StateFlow<PlayerState> = MutableStateFlow(initial)
    override val player: Player? = null
    val played = mutableListOf<StreamSpec>()

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
    ) {
        compose.setContent { KetchupTheme(device = DeviceClass.TV) { PlayerScreen(ui, controller, onRetry, onBack) } }
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

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
}
