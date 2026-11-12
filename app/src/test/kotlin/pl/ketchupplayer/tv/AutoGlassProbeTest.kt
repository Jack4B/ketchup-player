package pl.ketchupplayer.tv

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AutoGlassProbeTest {
    @get:Rule
    val compose = createComposeRule()

    /** Rozmycie nieustalone: dopiero po zwłoce uruchamia się pomiar i werdykt trafia do wywołującego dokładnie raz. */
    @Test
    fun nieustalonePoZwloceMierzyIOdaje() {
        val verdicts = mutableListOf<Boolean>()
        compose.setContent { AutoGlassProbe(decided = false) { verdicts += it } }
        compose.mainClock.advanceTimeBy(PROBE_DELAY_MS - 500)
        assertEquals(0, verdicts.size) // przed zwłoką nic się nie dzieje
        compose.mainClock.advanceTimeBy(PROBE_DELAY_MS)
        compose.waitForIdle()
        assertEquals(1, verdicts.size)
    }

    /** Rozmycie już ustalone (pomiarem albo ręcznie): pomiaru nie ma, więc nic nie zmienia wyboru użytkownika. */
    @Test
    fun ustalonePomijaPomiar() {
        val verdicts = mutableListOf<Boolean>()
        compose.setContent { AutoGlassProbe(decided = true) { verdicts += it } }
        compose.mainClock.advanceTimeBy(PROBE_DELAY_MS * 3)
        compose.waitForIdle()
        assertEquals(0, verdicts.size)
    }
}
