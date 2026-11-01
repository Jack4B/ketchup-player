package pl.ketchupplayer.tv.core.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ShellTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val entries =
        listOf(
            RailEntry("profile", "Profil", RailIcon.PROFILE),
            RailEntry("search", "Szukaj", RailIcon.SEARCH),
            RailEntry("home", "Home", RailIcon.HOME),
            RailEntry("live", "Na żywo", RailIcon.TV),
            RailEntry("guide", "Przewodnik", RailIcon.GUIDE),
            RailEntry("movies", "Filmy", RailIcon.MOVIES),
            RailEntry("series", "Seriale", RailIcon.SERIES, badge = true),
            RailEntry("favorites", "Moja lista", RailIcon.FAVORITES),
            RailEntry("settings", "Ustawienia", RailIcon.SETTINGS),
        )

    private fun show(
        theme: ThemeId = ThemeId.GRANAT,
        selected: String = "live",
        onSelect: (String) -> Unit = {},
        onRailBack: () -> Unit = {},
    ) {
        compose.setContent {
            KetchupTheme(theme, device = DeviceClass.TV) {
                AppShell(entries, selected, onSelect, onRailBack) {
                    ShellScreen("Na żywo") {
                        Row {
                            KetchupButton("Pierwszy", {}, primary = true, modifier = Modifier.requestFocusWhenPlaced())
                            KetchupButton("Drugi", {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    /** Pierwszy klawisz po starcie w teście tylko przełącza tryb wejścia z dotyku na klawiaturę (jak pilot na TV). */
    private fun keyboardMode() = key(Key.ShiftLeft)

    @Test
    fun screenshotGranat() {
        show(ThemeId.GRANAT)
        compose.onRoot().captureRoboImage("src/test/screenshots/shell_granat.png")
    }

    @Test
    fun screenshotCzern() {
        show(ThemeId.CZERN)
        compose.onRoot().captureRoboImage("src/test/screenshots/shell_czern.png")
    }

    @Test
    fun screenshotBordo() {
        show(ThemeId.BORDO)
        compose.onRoot().captureRoboImage("src/test/screenshots/shell_bordo.png")
    }

    @Test
    fun screenshotRozwinietejSzyny() {
        show()
        key(Key.DirectionLeft)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/shell_rozwinieta.png")
    }

    /** Jak w TiViMate: rozwinięta szuflada przesuwa treść w prawo (nadal widoczną), a nie nachodzi na nią. */
    @Test
    fun rozwinietaSzynaPrzesuwaTresc() {
        show()
        keyboardMode()
        val before =
            compose
                .onNodeWithText("Pierwszy")
                .fetchSemanticsNode()
                .boundsInRoot.left
        key(Key.DirectionLeft)
        compose.waitForIdle()
        val after =
            compose
                .onNodeWithText("Pierwszy")
                .fetchSemanticsNode()
                .boundsInRoot.left
        assertTrue("treść powinna przesunąć się w prawo ($before -> $after)", after > before + 40f)
    }

    /** Ustawienia (konfiguracja) nie przesuwają treści: szuflada może na nią nachodzić. */
    @Test
    fun szynaNaUstawieniachNiePrzesuwaTresci() {
        show(selected = "settings")
        keyboardMode()
        val before =
            compose
                .onNodeWithText("Pierwszy")
                .fetchSemanticsNode()
                .boundsInRoot.left
        key(Key.DirectionLeft)
        compose.waitForIdle()
        val after =
            compose
                .onNodeWithText("Pierwszy")
                .fetchSemanticsNode()
                .boundsInRoot.left
        assertEquals(before, after, 1f)
    }

    /** Pilot: LEWO z treści trafia w szynę na aktywną pozycję („Na żywo”), OK ją wybiera. */
    @Test
    fun dpadLewoDoSzynyNaAktywnaPozycjeOkWybiera() {
        var selected = ""
        show(onSelect = { selected = it })
        keyboardMode()
        key(Key.DirectionLeft)
        key(Key.DirectionCenter)
        assertEquals("live", selected)
    }

    @Test
    fun dpadPoSzynieDolWybieraKolejnaSekcje() {
        var selected = ""
        show(onSelect = { selected = it })
        keyboardMode()
        key(Key.DirectionLeft)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals("guide", selected)
    }

    @Test
    fun backZTresciWracaNaSzyneABackZSzinyWolaCallback() {
        var railBacks = 0
        show(onRailBack = { railBacks++ })
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Na żywo", substring = true) // szyna rozwinięta pokazuje podpisy
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, railBacks)
    }
}
