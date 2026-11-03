package pl.ketchupplayer.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import pl.ketchupplayer.tv.core.security.InMemorySecretStore
import pl.ketchupplayer.tv.core.security.KeystoreSecretModule
import pl.ketchupplayer.tv.core.security.SecretStore
import javax.inject.Singleton

/** W testach JVM nie ma Android Keystore: sekrety w pamięci. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [KeystoreSecretModule::class])
object FakeSecretModule {
    @Provides
    @Singleton
    fun secretStore(): SecretStore = InMemorySecretStore()
}
