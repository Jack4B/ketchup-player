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
    val keys: ApiKeys = ApiKeys(),
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
                _state.value = MetadataUiState(user, builtIn = keys.current().any && !user.any)
            }
        }

        fun save(newKeys: ApiKeys) {
            viewModelScope.launch {
                keys.save(newKeys)
                _state.value = _state.value.copy(keys = newKeys.trimmed(), saved = true)
            }
        }
    }

private fun ApiKeys.trimmed() = ApiKeys(tmdb.trim(), omdb.trim(), trakt.trim(), mdblist.trim())
