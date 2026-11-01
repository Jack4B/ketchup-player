package pl.ketchupplayer.tv.feature.vod

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.data.vod.EpisodeEntry
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.data.vod.VodRepository
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ShellPreview
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind

internal val PID = ProviderId("p")

internal fun movie(
    n: Int,
    title: String,
    category: String,
    year: Int,
    rating: Double,
    added: Long = n.toLong(),
) = VodEntry(
    VodItem(
        "p:m$n",
        PID,
        VodKind.MOVIE,
        title,
        "xtream:movie:$n.mp4",
        null,
        null,
        year,
        rating,
        null,
        null,
        null,
        category,
        added,
    ),
)

internal val MOVIES =
    listOf(
        movie(1, "Most nad rzeką", "Dramat", 2018, 6.0),
        movie(2, "Czerwona glina", "Akcja", 2019, 7.1),
        movie(3, "Nocny lot", "Dramat", 2020, 8.2),
        movie(4, "Ostatni pociąg", "Akcja", 2021, 9.3),
        movie(5, "Dom nad jeziorem", "Dramat", 2022, 6.4),
        movie(6, "Cichy świadek", "Komedia", 2023, 7.5),
        movie(7, "Złote miasto", "Dramat", 2024, 8.6),
        movie(8, "Burza", "Akcja", 2018, 9.7),
    )

internal class FakeVodRepo(
    private val all: List<VodEntry>,
) : VodRepository {
    override fun entries(kind: VodKind): Flow<List<VodEntry>> = MutableStateFlow(all.filter { it.item.kind == kind })

    override fun myList() = MutableStateFlow(emptyList<VodEntry>())

    override fun continueWatching(limit: Int) = MutableStateFlow(emptyList<VodEntry>())

    override fun entry(id: String) = MutableStateFlow(all.firstOrNull { it.item.id == id })

    override fun anyNewEpisodes() = MutableStateFlow(all.any { it.hasNewEpisodes })

    override suspend fun markSeen(seriesId: String) = Unit

    override suspend fun refreshInfo(itemId: String) = Unit

    override fun episodes(seriesId: String) = MutableStateFlow(emptyList<EpisodeEntry>())

    override suspend fun refreshEpisodes(seriesId: String) = Result.success(0)

    override suspend fun setMyList(
        itemId: String,
        value: Boolean,
    ) = Unit

    override suspend fun saveProgress(
        itemId: String,
        positionMs: Long,
        durationMs: Long,
    ) = Unit

    override suspend fun similar(
        id: String,
        limit: Int,
    ) = emptyList<VodItem>()

    override suspend fun playback(itemId: String) = null

    override suspend fun resolve(itemId: String) = Result.success(StreamSpec("http://h.example.invalid/x"))
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class VodBrowseScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun stateOf(vm: VodBrowseViewModel): VodBrowseUiState {
        vm.bind(VodMode.MOVIES)
        return runCatching { kotlinx.coroutines.runBlocking { vm.state.first { !it.loading } } }.getOrThrow()
    }

    private fun show(
        state: VodBrowseUiState,
        opened: MutableList<String> = mutableListOf(),
        events: MutableList<VodBrowseEvent> = mutableListOf(),
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                ShellPreview("movies") { VodBrowseScreen(state, { events += it }, { opened += it }, {}) }
            }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    private val base get() = stateOf(VodBrowseViewModel(FakeVodRepo(MOVIES), FakeGroups()))

    @Test
    fun screenshotSiatki() {
        show(base)
        compose.onRoot().captureRoboImage("src/test/screenshots/vod-movies.png")
    }

    @Test
    fun `fokus startuje na kategoriach, PRAWO wchodzi na pierwszy plakat, OK otwiera szczegoly`() {
        val opened = mutableListOf<String>()
        val s = base
        show(s, opened)
        key(Key.ShiftLeft) // pierwszy klawisz po starcie tylko przełącza tryb wejścia
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(
            listOf(
                s.entries
                    .first()
                    .item.id,
            ),
            opened,
        )
    }

    @Test
    fun `strzalka w prawo przechodzi na kolejny plakat`() {
        val s = base
        val opened = mutableListOf<String>()
        show(s, opened)
        key(Key.ShiftLeft)
        key(Key.DirectionRight) // z kategorii na pierwszy plakat
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(listOf(s.entries[1].item.id), opened)
    }

    /** Po wejściu na miniatury kategorie się chowają; LEWO z pierwszej kolumny wysuwa je i daje im fokus. */
    @Test
    fun `LEWO z pierwszej kolumny wysuwa kategorie`() {
        val events = mutableListOf<VodBrowseEvent>()
        show(base, mutableListOf(), events)
        key(Key.ShiftLeft)
        key(Key.DirectionRight)
        compose.waitForIdle()
        compose.onNodeWithText("Wszystkie").assertDoesNotExist()
        key(Key.DirectionLeft)
        settle()
        compose.onNodeWithText("Wszystkie").assertExists()
        key(Key.DirectionCenter) // OK trafia w kategorię, więc fokus jest w panelu
        assertEquals(listOf<VodBrowseEvent>(VodBrowseEvent.SelectCategory(null)), events)
    }

    @Test
    fun `BACK z siatki wysuwa kategorie`() {
        val events = mutableListOf<VodBrowseEvent>()
        show(base, mutableListOf(), events)
        key(Key.ShiftLeft)
        key(Key.DirectionRight)
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
        compose.onNodeWithText("Wszystkie").assertExists()
        key(Key.DirectionCenter)
        assertEquals(listOf<VodBrowseEvent>(VodBrowseEvent.SelectCategory(null)), events)
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(600) // animacja panelu i ponowienia fokusu
        compose.waitForIdle()
    }
}
