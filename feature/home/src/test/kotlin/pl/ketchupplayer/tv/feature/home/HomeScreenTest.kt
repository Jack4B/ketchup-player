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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
open class HomeScreenTest {
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

    @Test
    fun screenshot() {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                Box(Modifier.fillMaxSize().background(KetchupTheme.colors.background).padding(16.dp)) {
                    HomeScreen(state, {}, {}, {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/home_rows.png")
    }

    /** D-pad: hero ma fokus; DÓŁ schodzi do rzędów, OK na hero wznawia film. */
    @Test
    fun dpadSchodziDoRzedowIOtwieraSzczegoly() {
        val opened = mutableListOf<String>()
        val played = mutableListOf<String>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                HomeScreen(state, {}, {}, {}, { opened += it }, { played += it })
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Nowe odcinki").assertExists()
        key(Key.DirectionCenter) // Wznów na hero
        key(Key.DirectionDown) // przycisk Szczegóły/Wznów -> rząd Kontynuuj (przejście z paska przycisków)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf("m1"), played)
        assertEquals(true, opened.size <= 1)
    }
}

/** Wysoki ekran (pełna lista rzędów Home) do zrzutu poglądowego. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h900dp-xhdpi")
class HomeScreenTallTest : HomeScreenTest() {
    @Test
    fun screenshotWysoki() {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                Box(Modifier.fillMaxSize().background(KetchupTheme.colors.background).padding(16.dp)) {
                    HomeScreen(state, {}, {}, {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/home_rows_tall.png")
    }
}
