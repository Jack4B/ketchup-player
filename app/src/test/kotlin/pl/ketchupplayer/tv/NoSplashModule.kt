package pl.ketchupplayer.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** Testy nie czekają na ekran powitalny z logo. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SplashModule::class])
object NoSplashModule {
    @Provides
    fun splashPolicy(): SplashPolicy =
        object : SplashPolicy {
            override val durationMs = 0L
        }
}
