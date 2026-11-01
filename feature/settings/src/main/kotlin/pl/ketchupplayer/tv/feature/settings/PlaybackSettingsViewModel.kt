package pl.ketchupplayer.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.playback.FailoverSettingsRepository
import pl.ketchupplayer.tv.core.policy.FailoverSettings
import javax.inject.Inject

@HiltViewModel
class PlaybackSettingsViewModel
    @Inject
    constructor(
        private val repository: FailoverSettingsRepository,
        private val prefs: pl.ketchupplayer.tv.core.data.playback.PlayerPrefsRepository,
    ) : ViewModel() {
        val clockEnabled: StateFlow<Boolean> =
            prefs.clockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

        fun toggleClock() {
            viewModelScope.launch { prefs.setClockEnabled(!prefs.clockEnabled.first()) }
        }

        val state: StateFlow<FailoverSettings> =
            repository.settings.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                FailoverSettings(),
            )

        fun onEvent(event: PlaybackSettingsEvent) {
            viewModelScope.launch {
                val next = repository.settings.first().after(event)
                repository.setEnabled(next.enabled)
                repository.setSensitivity(next.sensitivity)
                repository.setTvDelaySeconds(next.tvDelaySeconds)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
