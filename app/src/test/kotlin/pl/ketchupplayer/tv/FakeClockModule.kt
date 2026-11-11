package pl.ketchupplayer.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.ClockModule
import java.util.concurrent.atomic.AtomicLong

/**
 * Zegar scenariuszy: czas systemowy plus przesunięcie, które test może zwiększać. Bez przesunięcia zachowuje się jak
 * prawdziwy zegar, więc pozostałe scenariusze działają jak dotąd.
 */
object TestClock : Clock {
    private val offset = AtomicLong()

    override fun nowMs(): Long = System.currentTimeMillis() + offset.get()

    fun advance(ms: Long) {
        offset.addAndGet(ms)
    }

    fun reset() = offset.set(0)
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ClockModule::class])
object FakeClockModule {
    @Provides
    fun clock(): Clock = TestClock
}
