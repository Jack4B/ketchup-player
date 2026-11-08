package pl.ketchupplayer.tv.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pilot na [FocusItem] (dwa elementy jeden pod drugim): który element reaguje na OK i dokąd prowadzą GÓRA/DÓŁ.
 *
 * Ustalenie z diagnozy PR #11 (sondy z CI, SHA cdf8952): w Robolectric `performKeyInput` zostawia tryb wejścia
 * dotykowy, a w nim semantyka `Focused` elementów [FocusItem] jest pusta (także w drzewie niescalonym), choć fokus
 * jest prawdziwy: OK klika element z fokusem, a DÓŁ przenosi go na następny. Na TV tryb jest klawiszowy od
 * pierwszego klawisza, a w nim semantyka pokazuje fokus. Dlatego testy fokusu w semantyce przełączają tryb jak system
 * ([tv]), a test trybu dotykowego sprawdza samo zachowanie (kto dostał kliknięcie).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class FocusItemTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val clicks = mutableListOf<String>()
    private val first = FocusRequester()
    private val second = FocusRequester()
    private var modes: InputModeManager? = null

    private fun host(tv: Boolean) {
        compose.setContent {
            KetchupTheme {
                val manager = LocalInputModeManager.current
                modes = manager
                if (tv) SideEffect { manager.requestInputMode(InputMode.Keyboard) }
                Column {
                    FocusItem({ clicks += "A" }, Modifier.testTag("a"), focusRequester = first) { Text("A") }
                    FocusItem({ clicks += "B" }, Modifier.testTag("b"), focusRequester = second) { Text("B") }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun focus(requester: FocusRequester) {
        compose.runOnIdle { requester.requestFocus() }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun naTvFokusJestWidocznyWSemantyceTylkoNaWybranymElemencie() {
        host(tv = true)
        assertEquals(InputMode.Keyboard, modes?.inputMode)
        focus(first)
        compose.onNodeWithTag("a").assertIsFocused()
        compose.onNodeWithTag("b").assertIsNotFocused()
    }

    @Test
    fun naTvOkKlikaTylkoElementZFokusem() {
        host(tv = true)
        focus(first)
        press(Key.DirectionCenter)
        assertEquals(listOf("A"), clicks)
    }

    @Test
    fun naTvDolPrzenosiFokusNaNastepnyElementAGoraWraca() {
        host(tv = true)
        focus(first)
        press(Key.DirectionDown)
        compose.onNodeWithTag("b").assertIsFocused()
        compose.onNodeWithTag("a").assertIsNotFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("B"), clicks)
        press(Key.DirectionUp)
        compose.onNodeWithTag("a").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("B", "A"), clicks)
    }

    @Test
    fun wTrybieDotykuPilotNadalPrzenosiFokusIOkKlikaWlasciwyElement() {
        host(tv = false)
        assertEquals(InputMode.Touch, modes?.inputMode)
        focus(first)
        press(Key.DirectionCenter)
        assertEquals(listOf("A"), clicks)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(listOf("A", "B"), clicks)
    }
}
