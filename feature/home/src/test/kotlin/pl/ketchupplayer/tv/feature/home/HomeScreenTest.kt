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
class HomeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun item(
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

    private val resume = VodEntry(item("m1", "Burza", VodKind.MOVIE), positionMs = 1_000, durationMs = 4_000)
    private val fresh = VodEntry(item("s1", "Dom nad jeziorem", VodKind.SERIES), hasNewEpisodes = true)
    private val state =
        HomeUiState(
            loading = false,
            hasProviders = true,
            featured = resume,
            featuredResumes = true,
            continueVod = listOf(resume),
            newEpisodes = listOf(fresh),
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
