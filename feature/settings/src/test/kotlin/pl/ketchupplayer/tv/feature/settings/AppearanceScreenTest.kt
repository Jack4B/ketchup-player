package pl.ketchupplayer.tv.feature.settings

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
import pl.ketchupplayer.tv.core.designsystem.AccentChoice
import pl.ketchupplayer.tv.core.designsystem.AccentId
import pl.ketchupplayer.tv.core.designsystem.AccentWarning
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ThemeId

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class AppearanceScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshotWyglad() {
        compose.setContent { KetchupTheme { AppearanceScreen(AppearanceUiState(), {}, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/appearance.png")
    }

    @Test
    fun screenshotEdytoraWlasnegoKoloru() {
        val editor = CustomEditor("#8844CC", 270f, 0.55f, 0.5f, warnings = setOf(AccentWarning.LOOKS_LIKE_ERROR_RED))
        val state = AppearanceUiState(ThemeId.BORDO, AccentChoice.Custom(0xFF8844CCL), editor)
        compose.setContent { KetchupTheme(ThemeId.BORDO, state.accent) { AppearanceScreen(state, {}, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/appearance_custom.png")
    }

    /** D-pad: fokus na pierwszej karcie motywu (Granat), PRAWO na Czerń, OK wybiera. */
    @Test
    fun dpadWybierzMotyw() {
        val events = mutableListOf<AppearanceEvent>()
        compose.setContent { KetchupTheme { AppearanceScreen(AppearanceUiState(), { events += it }, {}) } }
        compose.waitForIdle()
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(listOf<AppearanceEvent>(AppearanceEvent.SelectTheme(ThemeId.CZERN)), events)
    }

    /** Dotyk: tapnięcie akcentu i „Własny…”. */
    @Test
    fun dotykWybieraAkcentIOtwieraEdytor() {
        val events = mutableListOf<AppearanceEvent>()
        compose.setContent { KetchupTheme { AppearanceScreen(AppearanceUiState(), { events += it }, {}) } }
        compose.onNodeWithText("Fiolet").performClick()
        compose.onNodeWithText("Własny…").performClick()
        assertEquals(
            listOf(AppearanceEvent.SelectPreset(AccentId.FIOLET), AppearanceEvent.OpenCustom),
            events,
        )
    }

    /** D-pad na suwaku edytora: PRAWO zmienia wartość o krok (dokładny suwak zależy od kolejności fokusu). */
    @Test
    fun dpadSuwakZmieniaWartosc() {
        val events = mutableListOf<AppearanceEvent>()
        val editor = CustomEditor("#8844CC", 270f, 0.5f, 0.5f)
        val state = AppearanceUiState(editor = editor)
        compose.setContent { KetchupTheme { AppearanceScreen(state, { events += it }, {}) } }
        compose.waitForIdle()
        // Fokus startuje na karcie motywu; przejście do suwaka odwzorowuje nawigację DOL.
        repeat(3) { key(Key.DirectionDown) }
        key(Key.DirectionRight)
        val sliderEvents =
            events.filter {
                it is AppearanceEvent.HueChanged ||
                    it is AppearanceEvent.SaturationChanged ||
                    it is AppearanceEvent.LightnessChanged
            }
        assertEquals(1, sliderEvents.size)
    }

    @Test
    fun backZamykaEdytorPotemWychodzi() {
        val events = mutableListOf<AppearanceEvent>()
        var backs = 0
        val state = AppearanceUiState(editor = CustomEditor("#8844CC", 270f, 0.5f, 0.5f))
        compose.setContent { KetchupTheme { AppearanceScreen(state, { events += it }, { backs++ }) } }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(listOf<AppearanceEvent>(AppearanceEvent.CloseCustom), events)
        assertEquals(0, backs)
    }
}
