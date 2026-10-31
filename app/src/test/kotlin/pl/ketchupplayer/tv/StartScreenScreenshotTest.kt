package pl.ketchupplayer.tv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class StartScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startScreen() {
        compose.setContent { KetchupTheme { StartScreen() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/start_screen.png")
    }
}
