package pl.ketchupplayer.tv

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Test startu z prawdziwym grafem Hilt: łapie błędy wstrzykiwania, brakujące klasy i crashe przy starcie
 * (czego nie wykryją testy samych ekranów).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w960dp-h540dp-xhdpi")
class AppStartTest {
    private companion object {
        const val WAIT_MS = 5_000L
    }

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun nav(label: String) {
        compose.onNodeWithContentDescription(label).performClick()
        compose.waitForIdle()
    }

    private fun settingsPage(label: String) {
        nav("Ustawienia")
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun aplikacjaStartujeNaHomeZSzynaNawigacji() {
        hilt.inject()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Na żywo").assertExists()
        compose.onNodeWithContentDescription("Ustawienia").assertExists()
        compose.onNodeWithText("Home").assertExists()
    }

    @Test
    fun przejsciaDoDostawcowINaZywoZPrawdziwymGrafem() {
        hilt.inject()
        settingsPage("Dostawcy")
        compose.onNodeWithText("Dodaj dostawcę").assertExists()
        nav("Na żywo")
        // Dane z Room i EPG płyną asynchronicznie: czekamy na pusty stan z przyciskiem „Dostawcy”.
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText("Dostawcy").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun obrotZachowujeBiezacyEkran() {
        hilt.inject()
        settingsPage("Dostawcy")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Dodaj dostawcę").assertExists()
    }
}
