package pl.ketchupplayer.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.metadata.ApiKeys
import pl.ketchupplayer.tv.core.data.metadata.KeyCheck
import pl.ketchupplayer.tv.core.data.metadata.KeyProbe
import pl.ketchupplayer.tv.core.data.metadata.MetadataKeys
import javax.inject.Inject

/** Klucze wpisane przez użytkownika oraz informacja, czy aplikacja ma klucze wbudowane (z kompilacji). */
data class MetadataUiState(
    val keys: ApiKeys = ApiKeys(),
    val builtIn: Boolean = false,
    val saved: Boolean = false,
    /** Wynik „Sprawdź klucze”; null, dopóki użytkownik nie sprawdzał. */
    val checks: List<KeyCheck>? = null,
    val checking: Boolean = false,
)

@HiltViewModel
class MetadataSettingsViewModel
    @Inject
    constructor(
        private val keys: MetadataKeys,
        private val probe: KeyProbe,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MetadataUiState())
        val state: StateFlow<MetadataUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val user = keys.userEntered()
                _state.value = MetadataUiState(user, builtIn = keys.current().any && !user.any)
            }
        }

        /** Zapisuje wpisane klucze i próbuje każdego jednym znanym zapytaniem (Matrix); pokazuje, który nie działa. */
        fun check(newKeys: ApiKeys) {
            viewModelScope.launch {
                keys.save(newKeys)
                _state.value = _state.value.copy(keys = newKeys.trimmed(), checking = true, checks = null)
                val result = probe.check(newKeys.trimmed())
                _state.value = _state.value.copy(checks = result, checking = false, saved = false)
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
