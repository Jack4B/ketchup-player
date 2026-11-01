package pl.ketchupplayer.tv.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regresja: `Text` z większym `fontSize` dziedziczył stałą wysokość linii ze stylu motywu, więc zawijany tytuł
 * („Witaj w Ketchup / Player”) miał nachodzące na siebie linie. Wysokość linii musi rosnąć z rozmiarem czcionki.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TextLineHeightTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun zawinietyDuzyNapisMaLinieNieMniejszeNizRozmiarCzcionki() {
        val sizeSp = 34.sp
        var lines = 0
        var heightPx = 0f
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                Text(
                    "Witaj w Ketchup Player",
                    fontSize = sizeSp,
                    modifier = Modifier.width(260.dp),
                    onTextLayout = {
                        lines = it.lineCount
                        heightPx = it.size.height.toFloat()
                    },
                )
            }
        }
        compose.waitForIdle()
        val sizePx = sizeSp.value * compose.activity.resources.displayMetrics.density
        assertTrue("napis powinien się zawinąć (linii: $lines)", lines >= 2)
        // Linia nie niższa niż 1,1 × rozmiar czcionki (wcześniej ~0,7 ×: linie nachodziły).
        assertTrue("wysokość linii ${heightPx / lines}px < ${1.1f * sizePx}px", heightPx / lines >= 1.1f * sizePx)
    }
}
