package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Galeria motywów v3 (zrzuty Roborazzi 1920x1080). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ThemeGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        theme: ThemeId,
        accent: AccentId = theme.defaultAccent,
        name: String,
    ) {
        compose.setContent { ThemeSwatch(theme, accent = accent) }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun granat() = shot(ThemeId.GRANAT, name = "theme_granat")

    @Test
    fun czern() = shot(ThemeId.CZERN, name = "theme_czern")

    @Test
    fun bordo() = shot(ThemeId.BORDO, name = "theme_bordo")

    @Test
    fun granatZFioletowymAkcentem() = shot(ThemeId.GRANAT, AccentId.FIOLET, "theme_granat_fiolet")
}
