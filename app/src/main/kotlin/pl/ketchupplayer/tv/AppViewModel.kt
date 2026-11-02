package pl.ketchupplayer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.appearance.Appearance
import pl.ketchupplayer.tv.core.data.appearance.AppearanceRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        private val repository: AppearanceRepository,
        prefs: pl.ketchupplayer.tv.core.data.playback.PlayerPrefsRepository,
    ) : ViewModel() {
        val remoteDebounceMs: StateFlow<Int> =
            prefs.remoteDebounceMs.stateIn(viewModelScope, SharingStarted.Eagerly, 250)

        /** Wynik pomiaru płynności: zapisuje rozmycie jako ustalone (pomiar nie powtarza się przy starcie). */
        fun applyGlassVerdict(enabled: Boolean) {
            viewModelScope.launch { repository.setGlassBlur(enabled) }
        }

        val appearance: StateFlow<Appearance> =
            repository.appearance
                .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())
    }
