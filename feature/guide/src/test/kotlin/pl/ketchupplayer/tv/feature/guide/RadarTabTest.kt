package pl.ketchupplayer.tv.feature.guide

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
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
import pl.ketchupplayer.tv.core.designsystem.ShellPreview
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.epg.radar.RadarItem
import pl.ketchupplayer.tv.epg.radar.RadarSlot

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class RadarTabTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pid = ProviderId("p")
    private val min = 60_000L
    private val now = 1_800_000_000_000L
    private val state =
        GuideUiState(
            loading = false,
            channels = listOf(Channel("p:1", pid, "TVN", "u1"), Channel("p:2", pid, "Polsat", "u2")),
            windowStartMs = now,
            windowEndMs = GuideGeometry.windowEnd(now),
            nowMs = now,
        )
    private val items =
        listOf(
            RadarItem(
                Channel("p:1", pid, "TVN", "u1"),
                EpgProgramme("p:1", now - 20 * min, now + 70 * min, "Noc w muzeum", "O: 7,5/10", "Film"),
                68,
                75,
            ),
            RadarItem(
                Channel("p:2", pid, "Polsat", "u2"),
                EpgProgramme("p:2", now - 5 * min, now + 55 * min, "Wiadomości", null, "Informacje"),
                50,
                null,
            ),
        )

    private fun show(
        opened: MutableList<String> = mutableListOf(),
        slots: MutableList<RadarSlot> = mutableListOf(),
        radar: RadarUiState = RadarUiState(RadarSlot.NOW, false, items),
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                ShellPreview("guide") {
                    GuideScreen(state, { emptyList() }, { opened += it }, {}, {}, radar, { slots += it })
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Radar").performClick()
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshotRadaru() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/guide_radar.png")
    }

    /** D-pad: fokus startuje na pierwszej pozycji, OK otwiera jej kanał. */
    @Test
    fun dpadOkOtwieraKanalPierwszejPozycji() {
        val opened = mutableListOf<String>()
        show(opened)
        key(Key.DirectionCenter)
        assertEquals(listOf("p:1"), opened)
    }

    @Test
    fun dpadDolIOkOtwieraKolejnaPozycje() {
        val opened = mutableListOf<String>()
        show(opened)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf("p:2"), opened)
    }

    @Test
    fun wyborSlotuWywolujeZdarzenie() {
        val slots = mutableListOf<RadarSlot>()
        show(slots = slots)
        compose.onNodeWithText("Za chwilę").performClick()
        compose.onNodeWithText("Wieczorem").performClick()
        assertEquals(listOf(RadarSlot.SOON, RadarSlot.EVENING), slots)
    }
}
