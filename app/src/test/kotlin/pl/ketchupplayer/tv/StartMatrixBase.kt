package pl.ketchupplayer.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ThemeId
import pl.ketchupplayer.tv.core.testing.assertNoHorizontalOverflow

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class StartMatrixBase(
    private val device: DeviceClass,
    private val suffix: String,
) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ekranStartowyIDotyk() {
        var epg = 0
        var look = 0
        compose.setContent {
            KetchupTheme(ThemeId.GRANAT, device = device) {
                StartScreen(onOpenEpgSources = { epg++ }, onOpenAppearance = { look++ })
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/start_$suffix.png")
        compose.assertNoHorizontalOverflow()
        compose.onNodeWithText("Źródła EPG").performClick()
        compose.onNodeWithText("Wygląd").performClick()
        assertEquals(1, epg)
        assertEquals(1, look)
    }
}
