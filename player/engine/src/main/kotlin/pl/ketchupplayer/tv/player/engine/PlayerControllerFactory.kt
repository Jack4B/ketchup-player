package pl.ketchupplayer.tv.player.engine

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.ketchupplayer.tv.core.network.NetworkClients
import javax.inject.Inject

/** Tworzy kontroler odtwarzania; każdy ekran odtwarzacza ma własny (zwalniany w `onCleared`). */
fun interface PlayerControllerFactory {
    fun create(): PlayerController
}

class ExoPlayerControllerFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PlayerControllerFactory {
        override fun create(): PlayerController =
            ExoPlayerController(context, NetworkClients.stream(NetworkClients.PLAYER_USER_AGENT))
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {
    @Binds
    abstract fun bindFactory(impl: ExoPlayerControllerFactory): PlayerControllerFactory
}
