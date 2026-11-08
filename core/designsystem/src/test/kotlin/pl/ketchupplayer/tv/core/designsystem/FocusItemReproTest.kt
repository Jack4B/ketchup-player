package pl.ketchupplayer.tv.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SONDA (tymczasowa): minimalne odtworzenie różnicy między działającym `focusable()` (AutoFocusTest) a [FocusItem]
 * (clickable + focusable). Dla każdego wariantu drukuje: tryb wejścia, wynik requestFocus, czy drzewo semantyki
 * pokazuje fokus oraz czy OK (DirectionCenter) wywołało kliknięcie, czyli czy fokus jest rzeczywiście ustawiony.
 * Niczego nie asertuje: wynik czyta się z raportu JUnit.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class FocusItemReproTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var modes: InputModeManager? = null
    private var clicks = 0

    private fun host(content: @Composable () -> Unit) {
        compose.setContent {
            modes = LocalInputModeManager.current
            content()
        }
        compose.waitForIdle()
    }

    private fun semanticFocus() = compose.onAllNodes(isFocused()).fetchSemanticsNodes().size

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun probe(
        name: String,
        requester: FocusRequester,
    ) {
        val mode = modes?.inputMode
        val requested = compose.runOnIdle { requester.requestFocus() }
        compose.waitForIdle()
        val semantics = semanticFocus()
        press(Key.DirectionCenter)
        println("REPRO $name: tryb=$mode requestFocus=$requested semantykaFokusu=$semantics klikniecia=$clicks")
    }

    private val box = Modifier.size(80.dp)

    @Test
    fun wariantZwyklyFocusable() {
        val r = FocusRequester()
        host { Box(box.focusRequester(r).focusable()) }
        probe("focusable", r)
    }

    @Test
    fun wariantSamClickable() {
        val r = FocusRequester()
        host { Box(box.focusRequester(r).clickable { clicks++ }) }
        probe("clickable", r)
    }

    @Test
    fun wariantClickableIFocusablePrzedRequesterem() {
        val r = FocusRequester()
        host { Box(box.focusRequester(r).combinedClickable(onClick = { clicks++ }).focusable()) }
        probe("requester,combinedClickable,focusable", r)
    }

    @Test
    fun wariantRequesterMiedzyClickableAFocusable() {
        val r = FocusRequester()
        host { Box(box.combinedClickable(onClick = { clicks++ }).focusRequester(r).focusable()) }
        probe("combinedClickable,requester,focusable", r)
    }

    @Test
    fun wariantRequesterPoFocusable() {
        val r = FocusRequester()
        host { Box(box.combinedClickable(onClick = { clicks++ }).focusable().focusRequester(r)) }
        probe("combinedClickable,focusable,requester", r)
    }

    @Test
    fun wariantFocusItemZParametremFocusRequester() {
        val r = FocusRequester()
        host { FocusItem({ clicks++ }, focusRequester = r) { Box(Modifier.size(40.dp)) } }
        probe("FocusItem(focusRequester)", r)
    }

    @Test
    fun wariantFocusItemZRequesteremWModifier() {
        val r = FocusRequester()
        host { FocusItem({ clicks++ }, Modifier.focusRequester(r)) { Box(Modifier.size(40.dp)) } }
        probe("FocusItem(Modifier.focusRequester)", r)
    }

    @Test
    fun wariantFocusItemPoPierwszymKlawiszu() {
        val r = FocusRequester()
        host { FocusItem({ clicks++ }, focusRequester = r) { Box(Modifier.size(40.dp)) } }
        press(Key.ShiftLeft)
        probe("FocusItem po ShiftLeft", r)
    }

    @Test
    fun wariantFocusItemPoWymuszeniuTrybuKlawiatury() {
        val r = FocusRequester()
        host { FocusItem({ clicks++ }, focusRequester = r) { Box(Modifier.size(40.dp)) } }
        val switched = compose.runOnIdle { modes?.requestInputMode(InputMode.Keyboard) }
        println("REPRO requestInputMode(Keyboard)=$switched tryb=${modes?.inputMode}")
        probe("FocusItem po requestInputMode(Keyboard)", r)
    }

    @Test
    fun wariantFocusItemFokusRuchemStrzalki() {
        val a = FocusRequester()
        host {
            Box {
                FocusItem({ clicks++ }, focusRequester = a) { Box(Modifier.size(40.dp)) }
            }
        }
        press(Key.ShiftLeft)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        println("REPRO FocusItem po ShiftLeft+DOL+OK: semantykaFokusu=${semanticFocus()} klikniecia=$clicks")
    }
}
