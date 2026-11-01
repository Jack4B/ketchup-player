package pl.ketchupplayer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pl.ketchupplayer.tv.core.data.appearance.Appearance
import pl.ketchupplayer.tv.core.data.appearance.AppearanceRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        repository: AppearanceRepository,
        prefs: pl.ketchupplayer.tv.core.data.playback.PlayerPrefsRepository,
    ) : ViewModel() {
        val remoteDebounceMs: StateFlow<Int> =
            prefs.remoteDebounceMs.stateIn(viewModelScope, SharingStarted.Eagerly, 250)

        val appearance: StateFlow<Appearance> =
            repository.appearance
                .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
    }
