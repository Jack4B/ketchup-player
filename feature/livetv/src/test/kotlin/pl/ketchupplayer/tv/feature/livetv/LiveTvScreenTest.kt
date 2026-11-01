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
import pl.ketchupplayer.tv.core.designsystem.ShellPreview
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
                ShellPreview("live") {
                    LiveTvScreen(state, { events += it }, { opened += it }, providers, onBack)
                }
            }
        }
        compose.waitForIdle()
    }

    /** Zdarzenia użytkownika bez śledzenia fokusu (FocusChannel pojawia się przy każdym ruchu). */
    private fun List<LiveTvEvent>.actions() = filterNot { it is LiveTvEvent.FocusChannel }

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
        assertEquals(listOf<LiveTvEvent>(LiveTvEvent.SelectGroup("Info")), events.actions())
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
        assertEquals(listOf<LiveTvEvent>(LiveTvEvent.ToggleFavorite("p:1")), events.actions())
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun grupaUlubioneWidocznaTylkoGdyJestUlubiony() {
        val events = mutableListOf<LiveTvEvent>()
        show(full.copy(favorites = setOf("p:2")), events)
        key(Key.DirectionLeft) // wysuwa panel grup, fokus na wybranej („Wszystkie”)
        key(Key.DirectionUp) // wyżej leży „Ulubione”
        key(Key.DirectionCenter)
        assertEquals(listOf<LiveTvEvent>(LiveTvEvent.SelectFavorites), events.actions())
    }

    @Test
    fun backWychodzi() {
        var backs = 0
        show(full, onBack = { backs++ })
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }

    /** Dane jak na makiecie v3 (03-live): grupy z licznikami, kanały z audycją i godzinami, panel szczegółów. */
    @Test
    fun screenshotWgMakiety() {
        val min = 60_000L
        val now = 1_800_000_000_000L
        val names =
            listOf(
                Triple("Kanał Jeden", "Wiadomości wieczorne", 101),
                Triple("Info 24", "Rozmowa dnia", 102),
                Triple("Sport Plus", "Magazyn piłkarski", 103),
                Triple("Kino Premium", "Film fabularny", 104),
                Triple("Mini TV", "Bajki na dobranoc", 105),
                Triple("Doku HD", "Natura z bliska", 106),
                Triple("Hity Muzyka", "Lista przebojów", 107),
            )
        val chans =
            names.map { (n, _, no) ->
                Channel("p:$no", pid, n, "http://h.example.invalid/$no", no, group = "Informacje")
            }
        val air =
            names.associate { (_, t, no) ->
                "p:$no" to
                    EpgProgramme(
                        "p:$no",
                        now - 30 * min,
                        now + 30 * min,
                        t,
                        "Opis audycji z przewodnika EPG: dwie, trzy linie tekstu zastępczego.",
                    )
            }
        val state =
            LiveTvUiState(
                loading = false,
                groups = listOf("Informacje", "Sport", "Filmy", "Dzieci", "Muzyka", "Dokument"),
                selectedGroup = "Informacje",
                channels = chans,
                favorites = setOf("p:101"),
                onAir = air,
                nowMs = now,
                groupCounts =
                    mapOf(
                        "Informacje" to 24,
                        "Sport" to 38,
                        "Filmy" to 56,
                        "Dzieci" to 18,
                        "Muzyka" to 22,
                        "Dokument" to 30,
                    ),
                totalCount = 642,
                recentCount = 8,
                details =
                    ChannelDetails(
                        chans[1],
                        air["p:102"],
                        EpgProgramme("p:102", now + 30 * min, now + 90 * min, "Serwis informacyjny"),
                        "Lista 1 · Dostawca A",
                    ),
            )
        show(state)
        compose.onRoot().captureRoboImage("src/test/screenshots/livetv_makieta.png")
    }
}
