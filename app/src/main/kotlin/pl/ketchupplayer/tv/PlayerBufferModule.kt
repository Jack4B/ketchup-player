package pl.ketchupplayer.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import pl.ketchupplayer.tv.core.data.playback.PlayerPrefsRepository
import pl.ketchupplayer.tv.core.policy.BufferProfile
import pl.ketchupplayer.tv.core.policy.LiveBufferPolicy
import pl.ketchupplayer.tv.player.engine.BufferConfig
import pl.ketchupplayer.tv.player.engine.BufferConfigSource

/**
 * Łączy ustawienie „Bufor” z silnikiem: profil (polityka z `core:policy`) zamieniany na czasy `DefaultLoadControl`.
 * Odczyt jednorazowy przy tworzeniu odtwarzacza (DataStore jest już rozgrzany przy starcie, więc to chwila);
 * zmiana ustawienia działa od następnego odtwarzania.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerBufferModule {
    @Provides
    fun bufferSource(prefs: PlayerPrefsRepository): BufferConfigSource =
        BufferConfigSource {
            val profile = BufferProfile.fromKey(runBlocking { prefs.liveBuffer.first() })
            LiveBufferPolicy.bufferMs(profile).let { BufferConfig(it.minMs, it.maxMs, it.playbackMs, it.rebufferMs) }
        }
}
