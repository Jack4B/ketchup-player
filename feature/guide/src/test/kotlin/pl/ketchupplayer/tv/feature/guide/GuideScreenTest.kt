package pl.ketchupplayer.tv.feature.guide

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class GuideScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pid = ProviderId("p")
    private val min = 60_000L
    private val start = 1_800_000_000_000L - Math.floorMod(1_800_000_000_000L, 30 * min)
    private val now = start + 10 * min
    private val channels =
        listOf(
            Channel("p:1", pid, "JEDEN HD", "http://h.example.invalid/1", 1),
            Channel("p:2", pid, "INFO24", "http://h.example.invalid/2", 2),
            Channel("p:3", pid, "SPORT+", "http://h.example.invalid/3", 3),
        )
    private val state =
        GuideUiState(
            loading = false,
            channels = channels,
            favorites = setOf("p:3"),
            windowStartMs = start,
            windowEndMs = GuideGeometry.windowEnd(start),
            nowMs = now,
        )

    private val data: Map<String, List<EpgProgramme>> =
        mapOf(
            "p:1" to
                listOf(
                    EpgProgramme("p:1", start - 20 * min, start + 40 * min, "Wiadomości"),
                    EpgProgramme("p:1", start + 40 * min, start + 100 * min, "Serial: Dom"),
                    EpgProgramme("p:1", start + 100 * min, start + 160 * min, "Film: Noc"),
                ),
            "p:2" to listOf(EpgProgramme("p:2", start, start + 30 * min, "Info")),
        )

    private fun show(
        opened: MutableList<String> = mutableListOf(),
        backs: () -> Unit = {},
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                ShellPreview("guide") { GuideScreen(state, { data[it].orEmpty() }, { opened += it }, {}, backs) }
            }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshotSiatki() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/guide_grid.png")
    }

    @Test
    fun geometriaOknaIBlokow() {
        assertEquals(start, GuideGeometry.windowStart(now))
        // Audycja zaczęta przed oknem jest przycięta do jego początku.
        val (offset, width) =
            GuideGeometry.place(
                start - 20 * min,
                start + 40 * min,
                start,
                GuideGeometry.windowEnd(start),
            )!!
        assertEquals(0f, offset, 0.01f)
        assertEquals(40 * GuideGeometry.DP_PER_MINUTE, width, 0.01f)
        assertNull(GuideGeometry.place(start - 60 * min, start - 30 * min, start, GuideGeometry.windowEnd(start)))
    }

    @Test
    fun oknoObejmuje12GodzinIBlokPozno() {
        assertEquals(start + 12 * 60 * min, GuideGeometry.windowEnd(start))
        val (offset, _) =
            GuideGeometry.place(
                start + 10 * 60 * min,
                start + 11 * 60 * min,
                start,
                GuideGeometry.windowEnd(start),
            )!!
        assertEquals(10 * 60 * GuideGeometry.DP_PER_MINUTE, offset, 0.01f)
        assertNull(
            GuideGeometry.place(start + 12 * 60 * min, start + 13 * 60 * min, start, GuideGeometry.windowEnd(start)),
        )
    }

    /** D-pad: fokus startuje na pierwszym kanale, OK otwiera go. */
    @Test
    fun dpadOkOtwieraKanal() {
        val opened = mutableListOf<String>()
        show(opened)
        key(Key.DirectionCenter)
        assertEquals(listOf("p:1"), opened)
    }

    @Test
    fun dpadPrawoPrzechodziDoAudycjiADolDoKolejnegoKanalu() {
        val opened = mutableListOf<String>()
        show(opened)
        key(Key.DirectionRight) // z komórki kanału na pierwszą audycję
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(2, opened.size)
        assertEquals("p:1", opened.first())
        assertEquals("p:2", opened.last())
    }

    @Test
    fun backWychodzi() {
        var backs = 0
        show(backs = { backs++ })
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }
}
