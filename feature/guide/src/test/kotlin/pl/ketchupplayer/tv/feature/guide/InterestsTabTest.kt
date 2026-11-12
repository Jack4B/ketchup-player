package pl.ketchupplayer.tv.feature.guide

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
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
import pl.ketchupplayer.tv.epg.radar.InterestHit

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class InterestsTabTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pid = ProviderId("p")
    private val min = 60_000L
    private val now = 1_800_000_000_000L
    private val channel = Channel("p:1", pid, "Sport TV", "u1", 7)
    private val state =
        GuideUiState(
            loading = false,
            channels = listOf(channel),
            windowStartMs = now,
            windowEndMs = GuideGeometry.windowEnd(now),
            nowMs = now,
        )
    private val interests =
        InterestsUiState(
            keywords = listOf("Kleks", "Premier League"),
            hits =
                listOf(
                    InterestHit(
                        "Premier League",
                        channel,
                        EpgProgramme("p:1", now - 10 * min, now + 80 * min, "Mecz kolejki"),
                    ),
                    InterestHit(
                        "Kleks",
                        channel,
                        EpgProgramme("p:1", now + 120 * min, now + 190 * min, "Akademia pana Kleksa"),
                    ),
                ),
        )

    private fun show(
        data: InterestsUiState = interests,
        added: MutableList<String> = mutableListOf(),
        removed: MutableList<String> = mutableListOf(),
        opened: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                ShellPreview("guide") {
                    GuideScreen(
                        state,
                        { emptyList() },
                        { opened += it },
                        {},
                        {},
                        interests = data,
                        onAddInterest = { added += it },
                        onRemoveInterest = { removed += it },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Moje zainteresowania").performClick()
        compose.waitForIdle()
    }

    private fun key(k: Key) {
        compose.onRoot().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    @Test
    fun screenshotZainteresowan() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/guide_interests.png")
    }

    @Test
    fun screenshotPustychZainteresowan() {
        show(InterestsUiState())
        compose.onRoot().captureRoboImage("src/test/screenshots/guide_interests_empty.png")
    }

    @Test
    fun dodanieSlowaWysylaZdarzenieIWpisanyTekst() {
        val added = mutableListOf<String>()
        show(added = added)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Pitbull")
        compose.onNodeWithText("Dodaj").performClick()
        assertEquals(listOf("Pitbull"), added)
    }

    @Test
    fun okNaSlowieUsuwaJe() {
        val removed = mutableListOf<String>()
        show(removed = removed)
        compose.onNodeWithText("Kleks  ✕").performClick()
        assertEquals(listOf("Kleks"), removed)
    }

    @Test
    fun okNaTrafieniuOtwieraKanal() {
        val opened = mutableListOf<String>()
        show(opened = opened)
        compose.onNodeWithText("Mecz kolejki").performClick()
        assertEquals(listOf("p:1"), opened)
        key(Key.DirectionDown)
    }
}
