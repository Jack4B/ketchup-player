package pl.ketchupplayer.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.metadata.ApiKeys
import pl.ketchupplayer.tv.core.data.metadata.MetadataKeys
import javax.inject.Inject

/** Klucze wpisane przez użytkownika oraz informacja, czy aplikacja ma klucze wbudowane (z kompilacji). */
data class MetadataUiState(
    val tmdb: String = "",
    val omdb: String = "",
    val builtIn: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class MetadataSettingsViewModel
    @Inject
    constructor(
        private val keys: MetadataKeys,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MetadataUiState())
        val state: StateFlow<MetadataUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val user = keys.userEntered()
                _state.value = MetadataUiState(user.tmdb, user.omdb, builtIn = keys.current().any && !user.any)
            }
        }

        fun save(
            tmdb: String,
            omdb: String,
        ) {
            viewModelScope.launch {
                keys.save(ApiKeys(tmdb, omdb))
                _state.value = _state.value.copy(tmdb = tmdb.trim(), omdb = omdb.trim(), saved = true)
            }
        }
    }
