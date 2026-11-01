package pl.ketchupplayer.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ProviderId
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
        val opened = mutableListOf<Destination>()
        compose.setContent {
            KetchupTheme(ThemeId.GRANAT, device = device) {
                StartScreen(onNavigate = { opened += it })
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/start_$suffix.png")
        compose.assertNoHorizontalOverflow()
        compose.onNodeWithText("Na żywo").performScrollTo().performClick()
        compose.onNodeWithText("Dostawcy").performScrollTo().performClick()
        compose.onNodeWithText("Źródła EPG").performScrollTo().performClick()
        compose.onNodeWithText("Wygląd").performScrollTo().performClick()
        assertEquals(
            listOf(Destination.LIVE, Destination.PROVIDERS, Destination.EPG_SOURCES, Destination.APPEARANCE),
            opened,
        )
    }

    @Test
    fun wierszOstatnioOgladaneOtwieraKanal() {
        val pid = ProviderId("p")
        val recent =
            listOf(
                Channel("p:1", pid, "PL| TVN 24", "http://h.example.invalid/1"),
                Channel("p:2", pid, "TVP Info", "http://h.example.invalid/2"),
                Channel("p:3", pid, "Polsat News", "http://h.example.invalid/3"),
            )
        val opened = mutableListOf<String>()
        compose.setContent {
            KetchupTheme(ThemeId.GRANAT, device = device) {
                StartScreen(recent = recent, onOpenChannel = { opened += it })
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/start_recent_$suffix.png")
        compose.assertNoHorizontalOverflow()
        compose.onNodeWithText("Ostatnio oglądane").assertExists()
        compose.onNodeWithText("TVN 24").performScrollTo().performClick() // bez przedrostka dostawcy
        compose.onNodeWithText("TVP Info").performScrollTo().performClick()
        assertEquals(listOf("p:1", "p:2"), opened)
    }
}
