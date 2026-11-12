package pl.ketchupplayer.tv.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.ketchupplayer.tv.core.common.Clock

/**
 * Zegar aplikacji. Osobny moduł, żeby scenariusze E2E mogły go podmienić na sterowany (np. „obejrzano 95 minut”
 * bez czekania 95 minut); w aplikacji to zawsze zegar systemowy.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun clock(): Clock = Clock.System
}
