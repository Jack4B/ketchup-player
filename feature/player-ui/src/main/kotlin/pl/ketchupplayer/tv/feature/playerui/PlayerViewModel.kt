package pl.ketchupplayer.tv.feature.playerui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.player.engine.PlayerController
import pl.ketchupplayer.tv.player.engine.PlayerControllerFactory
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
        factory: PlayerControllerFactory,
    ) : ViewModel() {
        val controller: PlayerController = factory.create()
        private val _ui = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving(""))
        val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
        private var currentId: String? = null

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
                _ui.value = PlayerUiState.Resolving(channel.name)
                catalog.resolveStream(channelId).fold(
                    onSuccess = {
                        controller.play(it)
                        _ui.value = PlayerUiState.Playing(channel.name)
                    },
                    onFailure = { _ui.value = PlayerUiState.Failed(channel.name, unknownChannel = false) },
                )
            }
        }

        fun retry() = currentId?.let { start(it, force = true) }

        override fun onCleared() {
            controller.release()
        }
    }
