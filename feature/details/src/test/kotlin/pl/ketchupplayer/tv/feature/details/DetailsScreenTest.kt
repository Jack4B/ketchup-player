package pl.ketchupplayer.tv.feature.details

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import pl.ketchupplayer.tv.core.data.vod.EpisodeEntry
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DetailsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val movie =
        VodItem(
            "m1",
            ProviderId("p"),
            VodKind.MOVIE,
            "Czas zemsty",
            "ref",
            year = 2024,
            rating = 8.2,
            genre = "Thriller",
            plot = "Opis filmu, który ma kilka zdań i pokazuje, jak wygląda blok tekstu na ekranie szczegółów.",
            durationMin = 112,
        )
    private val movieState =
        DetailsUiState(
            loading = false,
            entry = VodEntry(movie, positionMs = 600_000, durationMs = 6_720_000),
            target = PlayTarget("m1", positionMs = 600_000),
            sources = 2,
            similar = listOf(movie.copy(id = "m2", title = "Podobny film")),
        )

    private fun show(
        state: DetailsUiState,
        events: MutableList<DetailsEvent> = mutableListOf(),
        played: MutableList<Pair<String, Boolean>> = mutableListOf(),
        opened: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                Box(Modifier.fillMaxSize().background(KetchupTheme.colors.background)) {
                    DetailsScreen(state, { events += it }, { id, fromStart -> played += id to fromStart }, {
                        opened +=
                            it
                    })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) {
        compose.onRoot().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    @Test
    fun screenshotFilmu() {
        show(movieState)
        compose.onRoot().captureRoboImage("src/test/screenshots/details_movie.png")
    }

    /** Bez włączonej personalizacji przycisku „Lubię to” nie ma (nic by nie zapisał). */
    @Test
    fun lubieToNiewidoczneBezPersonalizacji() {
        show(movieState)
        assertEquals(0, compose.onAllNodesWithText("Lubię to", substring = true).fetchSemanticsNodes().size)
    }

    /** Pilotem: w prawo za „Moją listę”, OK przełącza „Lubię to”. */
    @Test
    fun lubieToDzialaPilotem() {
        val events = mutableListOf<DetailsEvent>()
        show(movieState.copy(likeVisible = true), events = events)
        compose.onNodeWithText("♡ Lubię to").assertExists()
        key(Key.ShiftLeft)
        repeat(3) { key(Key.DirectionRight) } // Wznów, Od początku, Moja lista, Lubię to
        key(Key.DirectionCenter)
        assertEquals(listOf<DetailsEvent>(DetailsEvent.ToggleLike), events)
    }

    @Test
    fun polubionyFilmPokazujeStan() {
        show(movieState.copy(likeVisible = true, liked = true))
        compose.onNodeWithText("♥ Lubisz to").assertExists()
    }

    /** D-pad: fokus startuje na głównym przycisku; OK wznawia film od zapisanej pozycji. */
    @Test
    fun okNaStarcieWznawiaFilm() {
        val played = mutableListOf<Pair<String, Boolean>>()
        show(movieState, played = played)
        key(Key.ShiftLeft) // pierwszy klawisz po starcie tylko przełącza tryb wejścia
        key(Key.DirectionCenter)
        assertEquals(listOf("m1" to false), played)
    }

    /** D-pad: PRAWO przechodzi na „Od początku”, OK uruchamia od zera. */
    @Test
    fun prawoOkGraOdPoczatku() {
        val played = mutableListOf<Pair<String, Boolean>>()
        show(movieState, played = played)
        key(Key.ShiftLeft)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(listOf("m1" to true), played)
    }

    /** D-pad: dalej w prawo jest „Moja lista”; OK przełącza ją zdarzeniem. */
    @Test
    fun mojaListaPrzelaczaSiePilotem() {
        val events = mutableListOf<DetailsEvent>()
        show(movieState, events = events)
        key(Key.ShiftLeft)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(listOf<DetailsEvent>(DetailsEvent.ToggleMyList), events)
    }

    /** Serial: sezony jako chipy, odcinki z sezonu; ekran pokazuje kod odcinka w przycisku głównym. */
    @Test
    fun serialPokazujeSezonyIOdcinki() {
        val series = movie.copy(id = "s1", kind = VodKind.SERIES, title = "Dom nad jeziorem")
        val ep = Episode("e1", "s1", 1, 1, "Pilot", "ref", durationMin = 45)
        val state =
            DetailsUiState(
                loading = false,
                entry = VodEntry(series),
                seasons = listOf(1, 2),
                season = 1,
                episodes = listOf(EpisodeEntry(ep)),
                target = PlayTarget("e1", code = "S1 E1"),
            )
        show(state)
        compose.onNodeWithText("Sezon 1").assertExists()
        compose.onNodeWithText("Pilot", substring = true).assertExists()
        compose.onNodeWithText("▶ Oglądaj S1 E1").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/details_series.png")
    }

    @Test
    fun brakPozycjiPokazujeKomunikat() {
        show(DetailsUiState(loading = false, entry = null))
        compose.onNodeWithText("Nie znaleziono pozycji").assertExists()
    }
}
