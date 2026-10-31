package pl.ketchupplayer.tv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class StartScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** Regresja: komponenty tv-material nie reagowały na dotyk (telefon). */
    @Test
    fun dotykPrzyciskuOtwieraZrodlaEpg() {
        var opened = 0
        compose.setContent { KetchupTheme { StartScreen(onOpenEpgSources = { opened++ }) } }
        compose.onNodeWithText("Źródła EPG").performClick()
        assertEquals(1, opened)
    }

    /** Regresja: tytuł nakładał się na siebie w pionie (zawijanie bez lineHeight). */
    @Test
    fun screenshotPion() {
        compose.setContent { KetchupTheme { StartScreen() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/start_screen_portrait.png")
    }
}
