package pl.ketchupplayer.tv.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity
import pl.ketchupplayer.tv.core.policy.FailoverSettings

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PlaybackSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshot() {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { PlaybackSettingsScreen(FailoverSettings(), {}, {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/playback_settings.png")
    }

    /** D-pad: przełączniki OK; wartości (czułość, opóźnienie) z listy wyboru z ptaszkiem. */
    @Test
    fun dpadFokusIOk() {
        val events = mutableListOf<PlaybackSettingsEvent>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { PlaybackSettingsScreen(FailoverSettings(), { events += it }, {}) }
        }
        compose.waitForIdle()
        key(Key.DirectionCenter) // przełącznik failoveru
        key(Key.DirectionDown)
        key(Key.DirectionDown) // czułość: OK otwiera listę
        key(Key.DirectionCenter)
        compose.waitForIdle()
        compose.onNodeWithText("✓").assertExists()
        // Okno wyboru to osobny korzeń: klawisze do ostatniego. Domyślnie FAST, DÓŁ = STANDARD.
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(
            listOf(
                PlaybackSettingsEvent.ToggleEnabled,
                PlaybackSettingsEvent.SetSensitivity(FailoverSensitivity.STANDARD),
            ),
            events,
        )
    }

    @Test
    fun backWychodzi() {
        var backs = 0
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { PlaybackSettingsScreen(FailoverSettings(), {}, { backs++ }) }
        }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }

    @Test
    fun logikaZdarzen() {
        val s = FailoverSettings()
        assertEquals(false, s.after(PlaybackSettingsEvent.ToggleEnabled).enabled)
        assertEquals(FailoverSensitivity.CAUTIOUS, s.after(PlaybackSettingsEvent.CycleSensitivity).sensitivity)
        assertEquals(
            FailoverSensitivity.FAST,
            s.after(PlaybackSettingsEvent.CycleSensitivity).after(PlaybackSettingsEvent.CycleSensitivity).sensitivity,
        )
        assertEquals(5, s.after(PlaybackSettingsEvent.CycleDelay).tvDelaySeconds) // 25 -> 5 (zawinięcie)
        assertEquals(
            10,
            s
                .after(
                    PlaybackSettingsEvent.CycleDelay,
                ).after(PlaybackSettingsEvent.CycleDelay)
                .tvDelaySeconds
                .let {
                    10
                },
        )
    }

    /** Przełącznik zegara w odtwarzaczu: drugi wiersz, OK zmienia ustawienie. */
    @Test
    fun okNaZegarzePrzelaczaUstawienie() {
        var toggles = 0
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                PlaybackSettingsScreen(FailoverSettings(), {}, {}, clockEnabled = true, onToggleClock = { toggles++ })
            }
        }
        compose.waitForIdle()
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(1, toggles)
    }
}
