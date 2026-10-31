package pl.ketchupplayer.tv.core.designsystem

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

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h1100dp-xhdpi")
class ComponentsGalleryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun galeriaGranat() {
        compose.setContent { KetchupTheme(ThemeId.GRANAT) { ComponentsGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/components_granat.png")
    }

    @Test
    fun galeriaBordoZFokusem() {
        compose.setContent { KetchupTheme(ThemeId.BORDO) { ComponentsGallery() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/components_bordo_focus.png")
    }

    /** D-pad: fokus na przycisku, PRAWO przechodzi dalej, OK wywołuje akcję. */
    @Test
    fun dpadPrzyciskiIOk() {
        var clicks = 0
        compose.setContent { KetchupTheme { ComponentsGallery(onClick = { clicks++ }) } }
        compose.waitForIdle()
        key(Key.DirectionCenter)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(2, clicks)
    }

    @Test
    fun dotykPlakatu() {
        var clicks = 0
        compose.setContent { KetchupTheme { ComponentsGallery(onClick = { clicks++ }) } }
        compose.onNodeWithText("Tytuł filmu").performClick()
        compose.onNodeWithText("Źródła EPG").performClick()
        assertEquals(2, clicks)
    }
}
