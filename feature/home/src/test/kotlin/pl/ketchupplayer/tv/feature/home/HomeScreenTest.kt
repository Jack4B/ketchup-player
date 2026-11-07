package pl.ketchupplayer.tv.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind

/**
 * Wspólna reguła Compose i dane ekranu Home dla wariantów rozmiaru ([HomeScreenTest], [HomeScreenTallTest]).
 * Celowo bez własnych testów: testy deklarują klasy wariantów, a wspólne scenariusze to metody pomocnicze.
 * Odziedziczony test zrzutowy zapisywałby ten sam plik wzorca w innym rozmiarze (pilnuje tego
 * [HomeScreenVariantsTest]).
 */
abstract class HomeScreenFixture {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected fun item(
        id: String,
        title: String,
        kind: VodKind,
    ) = VodItem(
        id,
        ProviderId("p"),
        kind,
        title,
        "ref",
        year = 2025,
        rating = 8.1,
        plot = "Opis $title",
        genre = "Dramat",
    )

    protected fun movie(
        n: Int,
        title: String,
        rating: Double,
        year: Int,
    ) = VodEntry(
        item("m$n", title, VodKind.MOVIE).copy(rating = rating, year = year),
        positionMs = if (n <= 3) n * 1_000L else 0,
        durationMs = if (n <= 3) 4_000L else 0,
    )

    protected fun series(
        n: Int,
        title: String,
    ) = VodEntry(item("s$n", title, VodKind.SERIES), hasNewEpisodes = true)

    protected val resume = movie(1, "W mieście strachu", 8.4, 2026)
    protected val continueRow =
        listOf(
            resume,
            movie(2, "Piekielna robota", 9.1, 2026),
            movie(3, "Ukryte w ciemności", 7.9, 2025),
            movie(4, "Doctor X", 9.0, 2026),
            movie(5, "Skandalistki", 8.0, 2025),
        )
    protected val freshRow =
        listOf(
            series(1, "Jaga"),
            series(2, "Dom nad jeziorem"),
            series(3, "Moja ukryta prawda"),
            series(4, "Zombie Sagashitemas"),
            series(5, "Magic Knight Rayearth"),
        )
    protected val state =
        HomeUiState(
            loading = false,
            hasProviders = true,
            featured = resume,
            featuredResumes = true,
            continueVod = continueRow,
            newEpisodes = freshRow,
            myList = continueRow.reversed(),
        )

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    /** Ekran Home na tle motywu, jak w zrzutach. */
    protected fun showRows() {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                Box(Modifier.fillMaxSize().background(KetchupTheme.colors.background).padding(16.dp)) {
                    HomeScreen(state, {}, {}, {})
                }
            }
        }
    }

    /** D-pad: hero ma fokus; DÓŁ schodzi do rzędów, OK na hero wznawia film. */
    protected fun scenariuszDpadSchodziDoRzedowIOtwieraSzczegoly() {
        val opened = mutableListOf<String>()
        val played = mutableListOf<String>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                HomeScreen(state, {}, {}, {}, { opened += it }, { played += it })
            }
        }
        compose.waitForIdle()
        key(Key.DirectionCenter) // Wznów na hero
        key(Key.DirectionDown) // z hero na chipy „Mam czas na…”
        key(Key.DirectionDown) // dalej do rzędu Kontynuuj
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf("m1"), played)
        assertEquals(true, opened.size <= 1)
    }

    /** „Mam czas na…”: wybór 30 min pokazuje filmy do dokończenia z czasem, który zostaje; ponowny wybór je chowa. */
    protected fun scenariuszMamCzasNaPokazujeCoSieZmiesci() {
        compose.setContent { KetchupTheme(device = DeviceClass.TV) { HomeScreen(state, {}, {}, {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("30 min").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Zostało 1 min", substring = true)[0].assertExists()
        compose.onNodeWithText("30 min").performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithText("Zostało 1 min", substring = true).fetchSemanticsNodes().size)
    }

    protected fun scenariuszMamCzasNaBezDanychPodpowiadaCoZrobic() {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { HomeScreen(state.copy(continueVod = emptyList()), {}, {}, {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("2 h").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nic nie pasuje", substring = true).assertExists()
    }
}

/**
 * Ekran Home 960x540 dp. Scenariusze (D-pad, „Mam czas na…”) są wspólne z [HomeScreenTallTest] przez metody
 * pomocnicze [HomeScreenFixture]; zrzut `home_rows.png` zapisuje tylko ta klasa.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeScreenTest : HomeScreenFixture() {
    @Test
    fun screenshot() {
        showRows()
        compose.onRoot().captureRoboImage("src/test/screenshots/home_rows.png")
    }

    @Test
    fun dpadSchodziDoRzedowIOtwieraSzczegoly() = scenariuszDpadSchodziDoRzedowIOtwieraSzczegoly()

    @Test
    fun mamCzasNaPokazujeCoSieZmiesci() = scenariuszMamCzasNaPokazujeCoSieZmiesci()

    @Test
    fun mamCzasNaBezDanychPodpowiadaCoZrobic() = scenariuszMamCzasNaBezDanychPodpowiadaCoZrobic()
}

/**
 * Wysoki ekran Home 960x900 dp (pełna lista rzędów): te same scenariusze co [HomeScreenTest] plus zrzut poglądowy
 * `home_rows_tall.png`, który zapisuje wyłącznie ta klasa.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h900dp-xhdpi")
class HomeScreenTallTest : HomeScreenFixture() {
    @Test
    fun screenshotWysoki() {
        showRows()
        compose.onRoot().captureRoboImage("src/test/screenshots/home_rows_tall.png")
    }

    @Test
    fun dpadSchodziDoRzedowIOtwieraSzczegoly() = scenariuszDpadSchodziDoRzedowIOtwieraSzczegoly()

    @Test
    fun mamCzasNaPokazujeCoSieZmiesci() = scenariuszMamCzasNaPokazujeCoSieZmiesci()

    @Test
    fun mamCzasNaBezDanychPodpowiadaCoZrobic() = scenariuszMamCzasNaBezDanychPodpowiadaCoZrobic()
}
