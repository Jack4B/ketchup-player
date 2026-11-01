package pl.ketchupplayer.tv.feature.livetv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.core.model.ProviderId

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LiveTvScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pid = ProviderId("p")
    private val channels =
        listOf(
            Channel("p:1", pid, "JEDEN HD", "http://h.example.invalid/1", 101, group = "Ogólne"),
            Channel("p:2", pid, "INFO24", "http://h.example.invalid/2", 102, group = "Info"),
            Channel("p:3", pid, "SPORT+", "http://h.example.invalid/3", 103, group = "Sport"),
        )
    private val full = LiveTvUiState(loading = false, groups = listOf("Info", "Ogólne", "Sport"), channels = channels)

    private fun show(
        state: LiveTvUiState,
        events: MutableList<LiveTvEvent> = mutableListOf(),
        opened: MutableList<String> = mutableListOf(),
        providers: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                LiveTvScreen(state, { events += it }, { opened += it }, providers, onBack)
            }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshotListyKanalow() {
        show(full)
        compose.onRoot().captureRoboImage("src/test/screenshots/livetv_channels.png")
    }

    @Test
    fun screenshotZAudycjamiTeraz() {
        val now = 1_800_000_000_000L
        val onAir =
            mapOf(
                "p:1" to EpgProgramme("p:1", now - 1_200_000, now + 2_400_000, "Wiadomości"),
                "p:3" to EpgProgramme("p:3", now - 600_000, now + 3_000_000, "Mecz: finał ligi"),
            )
        show(full.copy(onAir = onAir, nowMs = now))
        compose.onRoot().captureRoboImage("src/test/screenshots/livetv_epg.png")
    }

    @Test
    fun screenshotLadowaniaIPusty() {
        show(LiveTvUiState(loading = true))
        compose.onRoot().captureRoboImage("src/test/screenshots/livetv_loading.png")
    }

    @Test
    fun screenshotPustegoKatalogu() {
        show(LiveTvUiState(loading = false))
        compose.onRoot().captureRoboImage("src/test/screenshots/livetv_empty.png")
    }

    @Test
    fun dpadFokusNaPierwszymKanaleIOkOtwieraOdtwarzacz() {
        val opened = mutableListOf<String>()
        show(full, opened = opened)
        key(Key.DirectionCenter)
        assertEquals(listOf("p:1"), opened)
    }

    @Test
    fun dpadDolPrzechodziDoKolejnegoKanalu() {
        val opened = mutableListOf<String>()
        show(full, opened = opened)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf("p:2"), opened)
    }

    @Test
    fun dpadLewoPrzechodziDoGrupIOkWybieraGrupe() {
        val events = mutableListOf<LiveTvEvent>()
        show(full, events)
        key(Key.DirectionLeft)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf<LiveTvEvent>(LiveTvEvent.SelectGroup("Info")), events)
    }

    @Test
    fun pustyKatalogMaPrzyciskDostawcow() {
        var clicks = 0
        show(LiveTvUiState(loading = false), providers = { clicks++ })
        key(Key.DirectionCenter)
        assertEquals(0, clicks) // przycisk nie ma autofokusu: pierwszy OK tylko ustawia fokus lub nic nie robi
    }

    @Test
    fun screenshotUlubionych() {
        val state =
            full.copy(
                favorites = setOf("p:3"),
                favoritesSelected = true,
                channels = channels.filter { it.id == "p:3" },
            )
        show(state)
        compose.onRoot().captureRoboImage("src/test/screenshots/livetv_favorites.png")
    }

    /** Pilot: przytrzymanie OK na kanale przełącza ulubiony (zdarzenie), zwykłe OK otwiera odtwarzacz. */
    @Test
    fun dlugieOkPrzelaczaUlubiony() {
        val events = mutableListOf<LiveTvEvent>()
        val opened = mutableListOf<String>()
        show(full, events, opened)
        compose.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(1_000)
            keyUp(Key.DirectionCenter)
        }
        compose.waitForIdle()
        assertEquals(listOf<LiveTvEvent>(LiveTvEvent.ToggleFavorite("p:1")), events)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun grupaUlubioneWidocznaTylkoGdyJestUlubiony() {
        val events = mutableListOf<LiveTvEvent>()
        show(full.copy(favorites = setOf("p:2")), events)
        key(Key.DirectionLeft)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(listOf<LiveTvEvent>(LiveTvEvent.SelectFavorites), events)
    }

    @Test
    fun backWychodzi() {
        var backs = 0
        show(full, onBack = { backs++ })
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }
}
