package pl.ketchupplayer.tv.feature.settings

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
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.EpgSourceKind

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class EpgSourcesScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sources =
        listOf(
            BuiltInEpgSources.ovh(),
            BuiltInEpgSources.teleman(),
            EpgSource("c1", "Moje EPG", EpgSourceKind.CUSTOM_XMLTV, "https://moje.pl/epg.xml.gz", true),
        )

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshotListy() {
        compose.setContent { EpgSourcesScreen(EpgSourcesUiState(sources), {}, {}) }
        compose.onRoot().captureRoboImage("src/test/screenshots/epg_sources_list.png")
    }

    @Test
    fun screenshotFormularza() {
        val state = EpgSourcesUiState(sources, AddForm("Moje", "ftp://x", FormError.URL))
        compose.setContent { EpgSourcesScreen(state, {}, {}) }
        compose.onRoot().captureRoboImage("src/test/screenshots/epg_sources_form.png")
    }

    /** D-pad: fokus startuje na pierwszej pozycji, DOL przechodzi dalej, OK przelacza flage. */
    @Test
    fun dpadFokusIOk() {
        val events = mutableListOf<EpgSourcesEvent>()
        compose.setContent { EpgSourcesScreen(EpgSourcesUiState(sources), { events += it }, {}) }
        compose.waitForIdle()

        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)

        assertEquals(
            listOf(
                EpgSourcesEvent.Toggle(BuiltInEpgSources.OVH_ID, false),
                EpgSourcesEvent.Toggle(BuiltInEpgSources.TELEMAN_ID, true),
                EpgSourcesEvent.StartAdd,
            ),
            events,
        )
    }

    /** Dotyk (telefon): tapnięcie wiersza przełącza flagę, przycisk dodaje źródło. */
    @Test
    fun dotykPrzelaczaIDodaje() {
        val events = mutableListOf<EpgSourcesEvent>()
        compose.setContent { EpgSourcesScreen(EpgSourcesUiState(sources), { events += it }, {}) }
        compose.onNodeWithText("Teleman.pl").performClick()
        compose.onNodeWithText("Dodaj własne źródło").performClick()
        assertEquals(
            listOf(EpgSourcesEvent.Toggle(BuiltInEpgSources.TELEMAN_ID, false), EpgSourcesEvent.StartAdd),
            events,
        )
    }

    @Test
    fun backZamykaFormularzPotemWychodzi() {
        val events = mutableListOf<EpgSourcesEvent>()
        var backs = 0
        val form = AddForm()
        compose.setContent {
            EpgSourcesScreen(EpgSourcesUiState(sources, form), { events += it }, { backs++ })
        }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(listOf<EpgSourcesEvent>(EpgSourcesEvent.Cancel), events)
        assertEquals(0, backs)
    }
}
