package pl.ketchupplayer.tv.feature.providers

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class OnboardingScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        state: ProvidersUiState = ProvidersUiState(),
        events: MutableList<ProvidersEvent> = mutableListOf(),
        finished: () -> Unit = {},
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { OnboardingScreen(state, { events += it }, finished) }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    private fun has(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun screenshotKrok1() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/onboarding-krok1.png")
    }

    @Test
    fun screenshotKrok2Formularz() {
        show(ProvidersUiState(form = ProviderForm(name = "Moja lista", url = "http://przyklad.pl/lista.m3u")))
        key(Key.DirectionCenter) // fokus startuje na „Dalej”: OK przechodzi do kroku 2
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/onboarding-krok2.png")
    }

    /** D-pad: fokus startuje na „Dalej”, OK przechodzi do kroku 2 i prosi o formularz. */
    @Test
    fun okNaDalejPrzechodziDoKroku2() {
        val events = mutableListOf<ProvidersEvent>()
        show(events = events)
        assertTrue(has("Witaj w Ketchup Player"))
        key(Key.DirectionCenter)
        compose.waitForIdle()
        assertTrue(has("Dodaj pierwszą listę"))
        assertTrue(ProvidersEvent.StartAdd in events)
    }

    /** D-pad: PRAWO z „Dalej” na „Pomiń”, OK kończy kreator. */
    @Test
    fun pominKonczyKreator() {
        var finished = 0
        show(finished = { finished++ })
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(1, finished)
    }
}
