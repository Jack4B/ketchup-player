package pl.ketchupplayer.tv.player.failover

import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity
import pl.ketchupplayer.tv.core.policy.FailoverSettings
import pl.ketchupplayer.tv.player.engine.PlaybackStatus
import pl.ketchupplayer.tv.player.engine.PlayerController
import pl.ketchupplayer.tv.player.engine.PlayerState

private class FakeController : PlayerController {
    override val state = MutableStateFlow(PlayerState())
    override val player: Player? = null
    val played = mutableListOf<String>()
    var stops = 0

    override fun play(spec: StreamSpec) {
        played += spec.url
        state.value = PlayerState(PlaybackStatus.BUFFERING)
    }

    override fun pause() = Unit

    override fun resume() = Unit

    override fun stop() {
        stops++
    }

    override fun release() = Unit

    fun set(status: PlaybackStatus) {
        state.value = PlayerState(status)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FailoverCoordinatorTest {
    private fun chain(
        n: Int,
        failResolveAt: Int? = null,
    ) = (0 until n).map { i ->
        SourceCandidate("Lista ${i + 1}") {
            if (i ==
                failResolveAt
            ) {
                Result.failure(IllegalStateException("resolve"))
            } else {
                Result.success(StreamSpec("u$i"))
            }
        }
    }

    private fun TestScope.coordinator(
        c: FakeController,
        settings: FailoverSettings = FailoverSettings(),
    ) = FailoverCoordinator(c, settings, this.backgroundScope)

    @Test
    fun `twardy blad - jedno ponowienie tego samego zrodla, potem Lista 2 (STANDARD)`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c)
            f.start(chain(3))
            runCurrent()
            assertEquals(listOf("u0"), c.played)
            c.set(PlaybackStatus.ERROR)
            runCurrent()
            assertEquals(listOf("u0", "u0"), c.played) // ponowienie
            c.set(PlaybackStatus.ERROR)
            runCurrent()
            assertEquals(listOf("u0", "u0", "u1"), c.played) // przełączenie
            assertEquals(FailoverState.Playing(1, 3, "Lista 2"), f.state.value)
        }

    @Test
    fun `FAST - brak ponowien, pierwszy blad przelacza`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c, FailoverSettings(sensitivity = FailoverSensitivity.FAST))
            f.start(chain(2))
            runCurrent()
            c.set(PlaybackStatus.ERROR)
            runCurrent()
            assertEquals(listOf("u0", "u1"), c.played)
        }

    @Test
    fun `FAST - rebuffer dluzszy niz 1,5 s przelacza od razu`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c, FailoverSettings(sensitivity = FailoverSensitivity.FAST))
            f.start(chain(2))
            runCurrent()
            advanceTimeBy(1_400)
            assertEquals(listOf("u0"), c.played)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("u0", "u1"), c.played)
        }

    @Test
    fun `ciagle buforowanie 25 s wymusza przelaczenie mimo progu STANDARD`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c)
            f.start(chain(2))
            runCurrent()
            advanceTimeBy(24_900)
            runCurrent()
            assertEquals(listOf("u0"), c.played)
            advanceTimeBy(200)
            runCurrent()
            assertEquals(listOf("u0", "u1"), c.played)
            assertEquals("Lista 2", (f.state.value as FailoverState.Playing).label)
        }

    @Test
    fun `krotki stall ponizej minimum nie jest incydentem`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c)
            f.start(chain(2))
            runCurrent()
            advanceTimeBy(2_000)
            c.set(PlaybackStatus.READY)
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(listOf("u0"), c.played)
        }

    @Test
    fun `wszystkie zrodla zawiodly - Exhausted, cooldown, drugi cykl, na koncu Failed`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c, FailoverSettings(sensitivity = FailoverSensitivity.FAST))
            f.start(chain(2))
            runCurrent()
            c.set(PlaybackStatus.ERROR) // 1 -> Lista 2
            runCurrent()
            c.set(PlaybackStatus.ERROR) // Lista 2 -> wyczerpane
            runCurrent()
            assertEquals(FailoverState.Exhausted(1), f.state.value)
            advanceTimeBy(12_000) // cooldown FAST
            runCurrent()
            assertEquals("u0", c.played.last()) // drugi cykl od Listy 1
            c.set(PlaybackStatus.ERROR)
            runCurrent()
            c.set(PlaybackStatus.ERROR)
            runCurrent()
            assertEquals(FailoverState.Failed, f.state.value)
        }

    @Test
    fun `blad rozwiazania adresu to twardy incydent i przejscie dalej`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c, FailoverSettings(sensitivity = FailoverSensitivity.FAST))
            f.start(chain(2, failResolveAt = 0))
            runCurrent()
            assertEquals(listOf("u1"), c.played)
            assertEquals(FailoverState.Playing(1, 2, "Lista 2"), f.state.value)
        }

    @Test
    fun `wylaczony failover - tylko ponowienia tego samego zrodla`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c, FailoverSettings(enabled = false))
            f.start(chain(3))
            runCurrent()
            repeat(4) {
                c.set(PlaybackStatus.ERROR)
                runCurrent()
            }
            assertTrue(c.played.all { it == "u0" })
        }

    @Test
    fun `30 s stabilnego grania zeruje liczniki incydentow`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c)
            f.start(chain(2))
            runCurrent()
            c.set(PlaybackStatus.ERROR)
            runCurrent() // ponowienie (licznik 1)
            c.set(PlaybackStatus.READY)
            advanceTimeBy(31_000)
            runCurrent()
            c.set(PlaybackStatus.ERROR)
            runCurrent() // znów dopuszczone ponowienie, a nie przełączenie
            assertEquals(listOf("u0", "u0", "u0"), c.played)
        }

    @Test
    fun `zdarzenie Switched dla UI i stop zeruje stan`() =
        runTest {
            val c = FakeController()
            val f = coordinator(c, FailoverSettings(sensitivity = FailoverSensitivity.FAST))
            val seen = mutableListOf<FailoverEvent>()
            backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
                f.events.collect {
                    seen +=
                        it
                }
            }
            f.start(chain(2))
            runCurrent()
            c.set(PlaybackStatus.ERROR)
            runCurrent()
            assertEquals(listOf<FailoverEvent>(FailoverEvent.Switched(1, 2, "Lista 2")), seen)
            f.stop()
            assertEquals(FailoverState.Idle, f.state.value)
        }
}
