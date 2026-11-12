package pl.ketchupplayer.tv.feature.guide

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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
import pl.ketchupplayer.tv.core.data.catalog.PlaylistChoice
import pl.ketchupplayer.tv.core.data.catalog.PlaylistOption
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ShellPreview
import pl.ketchupplayer.tv.core.designsystem.TAG_PLAYLIST_PICKER
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ProviderId

/** rc4: wybór listy w Na żywo (dwie syntetyczne listy z tą samą nazwą grupy). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GuidePlaylistTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val a = ProviderId("a")
    private val b = ProviderId("b")
    private val now = 1_800_000_000_000L
    private val playlists = PlaylistChoice(listOf(PlaylistOption(a, "Dom"), PlaylistOption(b, "Sport u znajomego")))
    private val state =
        GuideUiState(
            loading = false,
            channels =
                listOf(
                    Channel("a:1", a, "Kanał A1", "u1", 1, group = "Sport · Dom"),
                    Channel("b:1", b, "Kanał B1", "u2", 1, group = "Sport · Sport u znajomego"),
                ),
            windowStartMs = now,
            windowEndMs = GuideGeometry.windowEnd(now),
            nowMs = now,
            groups = listOf("Sport · Dom", "Sport · Sport u znajomego"),
            groupCounts = mapOf("Sport · Dom" to 1, "Sport · Sport u znajomego" to 1),
            totalCount = 2,
            playlist = playlists,
        )

    private fun show(
        s: GuideUiState,
        picked: MutableList<ProviderId?> = mutableListOf(),
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                ShellPreview("guide") {
                    GuideScreen(s, { emptyList() }, {}, {}, {}, onSelectPlaylist = { picked += it })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun dialogKey(k: Key) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    @Test
    fun grupyZTaSamaNazwaSaOsobneAWyborListyIdziePilotem() {
        val picked = mutableListOf<ProviderId?>()
        show(state, picked)
        compose.onNodeWithText("Sport · Dom").assertExists()
        compose.onNodeWithText("Sport · Sport u znajomego").assertExists()
        compose.onNodeWithText("Wszystkie listy").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Wybierz listę").assertExists()
        dialogKey(Key.DirectionDown) // fokus startuje na „Wszystkie listy”
        dialogKey(Key.DirectionDown)
        dialogKey(Key.DirectionCenter)
        assertEquals(listOf<ProviderId?>(b), picked)
    }

    @Test
    fun jednaListaBezWyboru() {
        show(state.copy(playlist = PlaylistChoice(listOf(PlaylistOption(a, "Dom")))))
        assertEquals(0, compose.onAllNodesWithTag(TAG_PLAYLIST_PICKER).fetchSemanticsNodes().size)
    }

    @Test
    fun pustaWybranaListaMowiToWprostIDajePowrotDoWszystkich() {
        val picked = mutableListOf<ProviderId?>()
        show(
            state.copy(
                channels = emptyList(),
                groups = emptyList(),
                totalCount = 0,
                playlist = playlists.copy(selected = b),
            ),
            picked,
        )
        compose.onNodeWithText("Lista „Sport u znajomego” nie ma kanałów.").assertExists()
        compose.onAllNodesWithText("Pokaż wszystkie listy").filter(hasClickAction())[0].performClick()
        assertEquals(listOf<ProviderId?>(null), picked)
    }

    @Test
    fun screenshotWyborListy() {
        show(state.copy(playlist = playlists.copy(selected = a)))
        compose.onRoot().captureRoboImage("src/test/screenshots/guide_playlist.png")
    }
}
