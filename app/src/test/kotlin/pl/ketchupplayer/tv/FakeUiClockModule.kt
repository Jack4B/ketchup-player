package pl.ketchupplayer.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import pl.ketchupplayer.tv.core.designsystem.UiClock
import javax.inject.Singleton

/** Zrzuty Roborazzi nie mogą zależeć od godziny uruchomienia testu: zegar UI jest stały (12:34, Europe/Warsaw). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [UiClockModule::class])
object FakeUiClockModule {
    @Provides
    @Singleton
    fun uiClock(): UiClock = UiClock.Frozen
}
