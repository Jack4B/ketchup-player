package pl.ketchupplayer.tv.feature.guide

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
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
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ShellPreview
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ProviderId

/** „Na żywo” = widok EPG z grupami: panel grup (LEWO / przycisk), wybór grupy, ulubione długim OK. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GuideGroupsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pid = ProviderId("p")
    private val now = 1_800_000_000_000L
    private val state =
        GuideUiState(
            loading = false,
            channels =
                listOf(
                    Channel("p:1", pid, "TVN 24", "u1", 1, group = "PL | Informacje"),
                    Channel("p:2", pid, "Polsat", "u2", 2, group = "PL | Ogólne"),
                ),
            windowStartMs = now,
            windowEndMs = GuideGeometry.windowEnd(now),
            nowMs = now,
            groups = listOf("PL | Informacje", "PL | Ogólne"),
            groupCounts = mapOf("PL | Informacje" to 24, "PL | Ogólne" to 31),
            totalCount = 55,
            favorites = setOf("p:2"),
        )

    private fun show(
        picks: MutableList<GuideGroup> = mutableListOf(),
        favorites: MutableList<String> = mutableListOf(),
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                ShellPreview("guide") {
                    GuideScreen(
                        state,
                        { emptyList() },
                        {},
                        {},
                        {},
                        onSelectGroup = { picks += it },
                        onToggleFavorite = { favorites += it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) {
        compose.onRoot().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    private fun has(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Lista kanałów: grupy są zawsze widoczne po lewej (bez wysuwania), z licznikami. */
    @Test
    fun grupySaWidoczneBezWysuwania() {
        show()
        assertTrue(has("PL | Informacje"))
    }

    @Test
    fun przyciskGrupWPrzewodnikuPokazujeWybranaGrupe() {
        show()
        compose.onNodeWithText("Przewodnik").performClick()
        compose.waitForIdle()
        assertTrue(has("Grupy: Wszystkie"))
    }

    @Test
    fun lewoNaKanaleWysuwaPanelGrupAOkWybieraGrupe() {
        val picks = mutableListOf<GuideGroup>()
        show(picks)
        key(Key.DirectionLeft)
        assertTrue(has("PL | Informacje"))
        compose.onRoot().captureRoboImage("src/test/screenshots/guide_groups_panel.png")
        key(Key.DirectionDown) // z „Ulubione” (wybrane: Wszystkie) w dół
        key(Key.DirectionCenter)
        assertEquals(1, picks.size)
    }

    @Test
    fun dlugieOkNaKanaleZmieniaUlubiony() {
        val favorites = mutableListOf<String>()
        show(favorites = favorites)
        compose.onAllNodesWithText("TVN 24").filter(hasClickAction())[0].performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(listOf("p:1"), favorites)
    }

    @Test
    fun ulubionyKanalMaGwiazdke() {
        show()
        assertTrue(has("★ Polsat"))
    }
}
