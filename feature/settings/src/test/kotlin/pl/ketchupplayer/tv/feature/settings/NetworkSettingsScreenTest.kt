package pl.ketchupplayer.tv.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import pl.ketchupplayer.tv.core.network.UserAgentProfiles

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class NetworkSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val rows =
        listOf(
            ProviderHeaderRow("a", "365TV", UserAgentProfiles.AUTO),
            ProviderHeaderRow("b", "Lista PL", UserAgentProfiles.VLC),
        )

    @Test
    fun screenshot() {
        compose.setContent { KetchupTheme(device = DeviceClass.TV) { NetworkSettingsScreen(rows, {}, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/network_settings.png")
    }

    /** D-pad: fokus na pierwszej liście, OK przełącza jej nagłówek; DÓŁ + OK drugiej. */
    @Test
    fun dpadOkPrzelaczaNaglowek() {
        val cycled = mutableListOf<String>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { NetworkSettingsScreen(rows, { cycled += it.providerId }, {}) }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf("a", "b"), cycled)
    }

    @Test
    fun backWychodzi() {
        var backs = 0
        compose.setContent { KetchupTheme(device = DeviceClass.TV) { NetworkSettingsScreen(rows, {}, { backs++ }) } }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }
}
