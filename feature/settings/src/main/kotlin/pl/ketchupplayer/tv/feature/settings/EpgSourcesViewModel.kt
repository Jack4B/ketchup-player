package pl.ketchupplayer.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.epg.AddSourceResult
import pl.ketchupplayer.tv.core.data.epg.EpgSourcesRepository
import javax.inject.Inject

@HiltViewModel
class EpgSourcesViewModel
    @Inject
    constructor(
        private val repository: EpgSourcesRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow<AddForm?>(null)

        val state: StateFlow<EpgSourcesUiState> =
            combine(repository.sources, form) { sources, f ->
                EpgSourcesUiState(sources, f)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EpgSourcesUiState())

        fun onEvent(event: EpgSourcesEvent) {
            when (event) {
                is EpgSourcesEvent.Toggle -> viewModelScope.launch { repository.setEnabled(event.id, event.enabled) }
                is EpgSourcesEvent.Remove -> viewModelScope.launch { repository.removeCustom(event.id) }
                EpgSourcesEvent.StartAdd -> form.value = AddForm()
                is EpgSourcesEvent.NameChanged -> form.update { it?.copy(name = event.value, error = null) }
                is EpgSourcesEvent.UrlChanged -> form.update { it?.copy(url = event.value, error = null) }
                EpgSourcesEvent.Cancel -> form.value = null
                EpgSourcesEvent.Submit -> submit()
            }
        }

        private fun submit() {
            val current = form.value ?: return
            viewModelScope.launch {
                when (repository.addCustom(current.name, current.url)) {
                    AddSourceResult.ADDED -> form.value = null
                    AddSourceResult.INVALID_NAME -> form.update { it?.copy(error = FormError.NAME) }
                    AddSourceResult.INVALID_URL -> form.update { it?.copy(error = FormError.URL) }
                    AddSourceResult.DUPLICATE -> form.update { it?.copy(error = FormError.DUPLICATE) }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
