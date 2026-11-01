package pl.ketchupplayer.tv

import androidx.media3.common.Player
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.player.engine.PlaybackStatus
import pl.ketchupplayer.tv.player.engine.PlayerController
import pl.ketchupplayer.tv.player.engine.PlayerControllerFactory
import pl.ketchupplayer.tv.player.engine.PlayerModule
import javax.inject.Singleton

/**
 * Sterowalny odtwarzacz do scenariuszy: wynik grania zależy od adresu ([Behaviour]). Rejestruje wszystkie wywołania,
 * dzięki czemu test widzi, co aplikacja próbowała odtworzyć (np. ponowienia i przełączenia failoveru).
 */
object FakePlayers {
    /** Zwraca status, w jaki ma przejść odtwarzanie danego adresu. */
    @Volatile
    var behaviour: (String) -> PlaybackStatus = { PlaybackStatus.READY }

    val played: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    fun reset() {
        behaviour = { PlaybackStatus.READY }
        played.clear()
    }
}

class FakePlayerController : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state =
        MutableStateFlow(
            pl.ketchupplayer.tv.player.engine
                .PlayerState(),
        )
    override val state: StateFlow<pl.ketchupplayer.tv.player.engine.PlayerState> = _state
    override val player: Player? = null

    override fun play(spec: StreamSpec) {
        FakePlayers.played += spec.url
        _state.value =
            pl.ketchupplayer.tv.player.engine
                .PlayerState(PlaybackStatus.BUFFERING)
        _state.value =
            pl.ketchupplayer.tv.player.engine
                .PlayerState(FakePlayers.behaviour(spec.url))
    }

    override fun pause() = Unit

    override fun resume() = Unit

    override fun stop() = Unit

    override fun release() = scope.cancel()

    private companion object {
        const val RESULT_DELAY_MS = 50L
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PlayerModule::class])
object FakePlayerModule {
    @Provides
    @Singleton
    fun factory(): PlayerControllerFactory = PlayerControllerFactory { FakePlayerController() }
}
