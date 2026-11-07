package pl.ketchupplayer.tv.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId

/** Zegar w nagłówku: czas ze źródła ([LocalUiClock]), jawna strefa, odświeżanie; produkcyjnie czas urządzenia. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ClockTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun zegarPokazujeCzasZeZrodlaWJawnejStrefie() {
        val clock = UiClock.fixed(Instant.parse("2026-01-15T11:34:00Z"), ZoneId.of("Europe/Warsaw"))
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                CompositionLocalProvider(LocalUiClock provides clock) { ClockText() }
            }
        }
        compose.onNodeWithText("12:34").assertExists()
    }

    @Test
    fun zegarOdswiezaSiePoUplywieCzasu() {
        var now = Instant.parse("2026-01-15T11:34:00Z")
        val clock = UiClock({ now }, { ZoneId.of("Europe/Warsaw") })
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                CompositionLocalProvider(LocalUiClock provides clock) { ClockText() }
            }
        }
        compose.onNodeWithText("12:34").assertExists()
        now = Instant.parse("2026-01-15T11:35:00Z")
        compose.mainClock.advanceTimeBy(CLOCK_TICK_MS + 1_000)
        compose.onNodeWithText("12:35").assertExists()
    }

    @Test
    fun naglowekWPodgladziePowlokiMaZamrozonyZegar() {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { ShellPreview("live") { ShellHeader("Na żywo") } }
        }
        compose.onNodeWithText("12:34").assertExists()
    }

    @Test
    fun bezPodmianyZegarPokazujeCzasUrzadzenia() {
        val before = UiClock.System.hhmm()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { ClockText() }
        }
        val after = UiClock.System.hhmm()
        val shown =
            listOf(before, after).filter {
                compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
            }
        assertTrue("zegar nie pokazuje czasu urządzenia ($before..$after)", shown.isNotEmpty())
    }
}
