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
import pl.ketchupplayer.tv.core.data.groups.GroupKind
import pl.ketchupplayer.tv.core.data.groups.GroupOption
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GroupsSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state =
        GroupsUiState(
            GroupKind.TV,
            listOf(GroupOption("PL | Informacje", 24, true), GroupOption("UK | News", 12, false)),
        )

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshot() {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { GroupsSettingsScreen(state, {}, {}, {}, {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/groups_settings.png")
    }

    /** D-pad: zakładki -> przyciski zbiorcze -> lista; OK na grupie przełącza ją, OK na przycisku zmienia całość. */
    @Test
    fun dpadPrzelaczaGrupeIWszystkie() {
        val toggled = mutableListOf<String>()
        val all = mutableListOf<Boolean>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                GroupsSettingsScreen(state, {}, { toggled += it }, { all += it }, {})
            }
        }
        compose.waitForIdle()
        key(Key.DirectionDown) // przycisk „Pokaż wszystkie”
        key(Key.DirectionCenter)
        key(Key.DirectionRight) // „Ukryj wszystkie”
        key(Key.DirectionCenter)
        key(Key.DirectionDown) // pierwsza grupa
        key(Key.DirectionCenter)
        assertEquals(listOf(true, false), all)
        assertEquals(listOf("PL | Informacje"), toggled)
    }

    @Test
    fun backWychodzi() {
        var backs = 0
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { GroupsSettingsScreen(state, {}, {}, {}, { backs++ }) }
        }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }
}
