package pl.ketchupplayer.tv.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationDiagnostics
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationPrefs
import pl.ketchupplayer.tv.core.data.personalization.TeamChoice
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.personalization.FollowState
import pl.ketchupplayer.tv.core.model.personalization.Team
import pl.ketchupplayer.tv.core.testing.assertNoHorizontalOverflow

/**
 * Ustawienia → Personalizacja: zgoda domyślnie wyłączona, tryb „nie ucz się”, retencja, drużyny, kopia danych
 * i czyszczenie z potwierdzeniem. Wszystko pilotem (D-pad, OK, BACK). Drużyny zmyślone.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h900dp-xhdpi")
class PersonalizationSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Recorded {
        val enabled = mutableListOf<Boolean>()
        val incognito = mutableListOf<Boolean>()
        val retention = mutableListOf<Int>()
        val followed = mutableListOf<String>()
        val unfollowed = mutableListOf<String>()
        val added = mutableListOf<String>()
        var exports = 0
        var clears = 0
        var backs = 0
    }

    private val teams =
        listOf(
            TeamChoice(Team("wisla-nadmorska", "Wisła Nadmorska"), FollowState.FOLLOWED),
            TeamChoice(Team("hutnik-lesny", "Hutnik Leśny"), FollowState.SUGGESTED),
            TeamChoice(Team("gornik-zmyslony", "Górnik Zmyślony"), null),
            TeamChoice(Team("stal-probna", "Stal Próbna"), null),
        )
    private val on =
        PersonalizationUiState(
            prefs = PersonalizationPrefs(enabled = true),
            teams = teams,
            diagnostics = PersonalizationDiagnostics(12, 7, 4, 1, 23, 5, 1, snapshotBuiltAtMs = 1_774_000_000_000),
        )

    private fun show(
        state: PersonalizationUiState,
        r: Recorded = Recorded(),
    ): Recorded {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                PersonalizationSettingsScreen(
                    state,
                    PersonalizationActions(
                        onEnabled = { r.enabled += it },
                        onIncognito = { r.incognito += it },
                        onRetention = { r.retention += it },
                        onFollow = { r.followed += it },
                        onUnfollow = { r.unfollowed += it },
                        onAddTeam = { r.added += it },
                        onExport = { r.exports++ },
                        onClear = { r.clears++ },
                    ),
                    onBack = { r.backs++ },
                )
            }
        }
        compose.waitForIdle()
        return r
    }

    private fun key(k: Key) {
        compose.onRoot().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    private fun dialogKey(k: Key) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    private fun clickable(text: String) = compose.onAllNodesWithText(text, substring = true).filter(hasClickAction())[0]

    @Test
    fun domyslnieWylaczonaPokazujeTylkoZgodeICzyszczenie() {
        val r = show(PersonalizationUiState())
        compose.onNodeWithText("Wyłączone: aplikacja nie zapisuje historii oglądania.").assertExists()
        listOf(TAG_PERSONAL_INCOGNITO, TAG_PERSONAL_ADD_TEAM).forEach {
            assertEquals(it, 0, compose.onAllNodesWithTag(it).fetchSemanticsNodes().size)
        }
        compose.onNodeWithTag(TAG_PERSONAL_CLEAR).assertExists()
        key(Key.DirectionCenter) // fokus startuje na przełączniku zgody
        assertEquals(listOf(true), r.enabled)
    }

    @Test
    fun dpadPrzelaczaZgodeTrybNieUczSieIRetencje() {
        val r = show(on)
        key(Key.DirectionCenter)
        assertEquals("OK na włączonej zgodzie ją wyłącza", listOf(false), r.enabled)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf(true), r.incognito)
        key(Key.DirectionDown)
        key(Key.DirectionCenter) // retencja: lista wyboru z ptaszkiem przy bieżącej wartości (180 dni)
        compose.onNodeWithText("✓").assertExists()
        clickable("90 dni").performClick()
        compose.waitForIdle()
        assertEquals(listOf(90), r.retention)
        assertEquals("wybór zamyka listę", 0, compose.onAllNodesWithText("365 dni").fetchSemanticsNodes().size)
    }

    @Test
    fun druzynyDodawanieZListyIZmianaStanu() {
        val r = show(on)
        clickable("Wisła Nadmorska").performScrollTo().performClick()
        assertEquals("OK na obserwowanej: przestań obserwować", listOf("wisla-nadmorska"), r.unfollowed)
        clickable("Hutnik Leśny").performClick()
        assertEquals("propozycja staje się obserwowaną dopiero po OK", listOf("hutnik-lesny"), r.followed)
        compose.onNodeWithTag(TAG_PERSONAL_ADD_TEAM).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Wybierz drużynę").assertExists()
        val shown = compose.onAllNodesWithText("Wisła Nadmorska").fetchSemanticsNodes().size
        assertEquals("obserwowana jest tylko na ekranie, nie na liście do dodania", 1, shown)
        dialogKey(Key.DirectionCenter) // pierwsza alfabetycznie: Górnik Zmyślony
        assertEquals(listOf("hutnik-lesny", "gornik-zmyslony"), r.followed)
    }

    @Test
    fun wlasnaDruzynaJestDodawanaPoWpisaniuNazwy() {
        val r = show(on)
        compose.onNodeWithTag(TAG_PERSONAL_ADD_CUSTOM).performScrollTo().performClick()
        assertTrue("pusta nazwa niczego nie dodaje", r.added.isEmpty())
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("  Błękitni Zmyśleni ")
        compose.onNodeWithTag(TAG_PERSONAL_ADD_CUSTOM).performScrollTo().performClick()
        assertEquals(listOf("Błękitni Zmyśleni"), r.added)
    }

    @Test
    fun czyszczenieWymagaPotwierdzeniaADomyslnieJestAnuluj() {
        val r = show(on)
        compose.onNodeWithTag(TAG_PERSONAL_CLEAR).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Usunąć historię oglądania, oceny i drużyny?").assertExists()
        dialogKey(Key.DirectionCenter) // fokus startuje na „Anuluj”
        assertEquals(0, r.clears)
        compose.onNodeWithTag(TAG_PERSONAL_CLEAR).performClick()
        compose.waitForIdle()
        dialogKey(Key.DirectionDown)
        dialogKey(Key.DirectionCenter)
        assertEquals(1, r.clears)
    }

    @Test
    fun kopiaDanychIKomunikaty() {
        val saved = PersonalMessage.Exported("/storage/emulated/0/export/ketchup-personalizacja.json")
        val r = show(on.copy(message = saved))
        clickable("Zapisz kopię danych").performScrollTo().performClick()
        assertEquals(1, r.exports)
        compose.onNodeWithText("Zapisano:", substring = true).assertExists()
        compose.onNodeWithText("Sesje: 12 (jako oglądanie: 7, neutralne: 4, awarie: 1)").assertExists()
    }

    @Test
    fun backWracaDoKategorii() {
        val r = show(on)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertTrue(r.backs == 1)
    }

    @Test
    fun screenshotWlaczona() {
        show(on)
        compose.onRoot().captureRoboImage("src/test/screenshots/personalization_settings.png")
        compose.assertNoHorizontalOverflow()
    }

    @Test
    fun screenshotWylaczona() {
        show(PersonalizationUiState())
        compose.onRoot().captureRoboImage("src/test/screenshots/personalization_settings_off.png")
    }
}
