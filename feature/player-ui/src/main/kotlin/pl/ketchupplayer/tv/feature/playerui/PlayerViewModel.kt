package pl.ketchupplayer.tv.feature.playerui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.playback.FailoverSettingsRepository
import pl.ketchupplayer.tv.core.policy.FailoverSettings
import pl.ketchupplayer.tv.player.engine.PlayerController
import pl.ketchupplayer.tv.player.engine.PlayerControllerFactory
import pl.ketchupplayer.tv.player.failover.FailoverCoordinator
import pl.ketchupplayer.tv.player.failover.FailoverEvent
import pl.ketchupplayer.tv.player.failover.FailoverState
import pl.ketchupplayer.tv.player.failover.SourceCandidate
import javax.inject.Inject

/** Stan rozwiązywania kanału; stan samego odtwarzania jest w [PlayerController.state]. */
sealed interface PlayerUiState {
    data class Resolving(
        val channelName: String,
    ) : PlayerUiState

    data class Playing(
        val channelName: String,
    ) : PlayerUiState

    data class Failed(
        val channelName: String,
        val unknownChannel: Boolean,
    ) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val failoverSettings: FailoverSettingsRepository,
        factory: PlayerControllerFactory,
    ) : ViewModel() {
        val controller: PlayerController = factory.create()
        private val _ui = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving(""))
        val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
        private val _notice = MutableStateFlow<String?>(null)

        /** Krótka informacja o przełączeniu źródła („Lista 2 · nazwa”); znika po [NOTICE_MS]. */
        val notice: StateFlow<String?> = _notice.asStateFlow()
        private val failover = FailoverCoordinator(controller, FailoverSettings(), viewModelScope)
        private var currentId: String? = null
        private var channelName = ""

        init {
            viewModelScope.launch {
                failover.state.collect { st ->
                    when (st) {
                        is FailoverState.Playing -> _ui.value = PlayerUiState.Playing(channelName)
                        is FailoverState.Failed -> _ui.value = PlayerUiState.Failed(channelName, unknownChannel = false)
                        else -> Unit
                    }
                }
            }
            viewModelScope.launch {
                failover.events.collect { ev ->
                    if (ev is FailoverEvent.Switched) {
                        _notice.value = ev.label
                        delay(NOTICE_MS)
                        _notice.value = null
                    }
                }
            }
        }

        /** Rozpoczyna odtwarzanie kanału; to samo id drugi raz (np. po obrocie) jest ignorowane, chyba że [force]. */
        fun start(
            channelId: String,
            force: Boolean = false,
        ) {
            if (!force && channelId == currentId) return
            currentId = channelId
            viewModelScope.launch {
                val channel = catalog.channel(channelId)
                if (channel == null) {
                    _ui.value = PlayerUiState.Failed("", unknownChannel = true)
                    return@launch
                }
                channelName = channel.name
                _ui.value = PlayerUiState.Resolving(channel.name)
                val chain = catalog.equivalents(channelId).ifEmpty { listOf(channel) }
                failover.settings = failoverSettings.settings.first()
                failover.start(
                    chain.mapIndexed { i, ch ->
                        val label = if (i == 0 && chain.size == 1) ch.name else "Lista ${i + 1} · ${ch.name}"
                        SourceCandidate(label) { catalog.resolveStream(ch.id) }
                    },
                )
            }
        }

        fun retry() = currentId?.let { start(it, force = true) }

        override fun onCleared() {
            failover.stop()
            controller.release()
        }

        private companion object {
            const val NOTICE_MS = 4_000L
        }
    }
