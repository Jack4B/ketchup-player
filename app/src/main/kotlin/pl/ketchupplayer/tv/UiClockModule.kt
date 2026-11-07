package pl.ketchupplayer.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.ketchupplayer.tv.core.designsystem.UiClock
import javax.inject.Singleton

/** Zegar UI (szyna, odtwarzacz): czas i strefa urządzenia. Testy Hilt podmieniają go na stały (`FakeUiClockModule`). */
@Module
@InstallIn(SingletonComponent::class)
object UiClockModule {
    @Provides
    @Singleton
    fun uiClock(): UiClock = UiClock.System
}
