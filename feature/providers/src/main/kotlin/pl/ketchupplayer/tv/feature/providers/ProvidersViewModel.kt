package pl.ketchupplayer.tv.feature.providers

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
import pl.ketchupplayer.tv.core.data.catalog.AddProviderError
import pl.ketchupplayer.tv.core.data.catalog.AddProviderResult
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import javax.inject.Inject

@HiltViewModel
class ProvidersViewModel
    @Inject
    constructor(
        private val catalog: CatalogRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(ProvidersUiState())

        val state: StateFlow<ProvidersUiState> =
            combine(catalog.providers, local) { providers, l -> l.copy(providers = providers) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProvidersUiState())

        fun onEvent(event: ProvidersEvent) {
            when (event) {
                ProvidersEvent.StartAdd -> local.update { it.copy(form = ProviderForm(), message = null) }
                ProvidersEvent.Cancel -> local.update { it.copy(form = null) }
                is ProvidersEvent.NameChanged ->
                    local.update {
                        it.copy(
                            form = it.form?.copy(name = event.value, nameError = false),
                        )
                    }
                is ProvidersEvent.UsernameChanged ->
                    local.update { it.copy(form = it.form?.copy(username = event.value, credentialsError = false)) }
                is ProvidersEvent.PasswordChanged ->
                    local.update { it.copy(form = it.form?.copy(password = event.value, credentialsError = false)) }
                is ProvidersEvent.SelectKind -> local.update { it.copy(form = it.form?.copy(kind = event.kind)) }
                is ProvidersEvent.UrlChanged ->
                    local.update {
                        it.copy(
                            form = it.form?.copy(url = event.value, urlError = false),
                        )
                    }
                ProvidersEvent.Submit -> submit()
                is ProvidersEvent.Toggle ->
                    local.update {
                        it.copy(
                            expandedId =
                                if (it.expandedId ==
                                    event.id
                                ) {
                                    null
                                } else {
                                    event.id
                                },
                        )
                    }
                is ProvidersEvent.Refresh -> viewModelScope.launch { runBusy { catalog.refresh(event.id) } }
                is ProvidersEvent.Remove ->
                    viewModelScope.launch {
                        catalog.remove(event.id)
                        local.update { it.copy(expandedId = null) }
                    }
            }
        }

        private fun submit() {
            val form = local.value.form ?: return
            viewModelScope.launch {
                local.update { it.copy(busy = true) }
                when (val r = add(form)) {
                    is AddProviderResult.Added -> local.update { it.copy(form = null, busy = false, message = null) }
                    is AddProviderResult.SavedWithError ->
                        local.update { it.copy(form = null, busy = false, message = r.message) }
                    is AddProviderResult.Invalid ->
                        local.update {
                            it.copy(
                                busy = false,
                                form =
                                    it.form?.copy(
                                        nameError = r.error == AddProviderError.INVALID_NAME,
                                        urlError = r.error == AddProviderError.INVALID_URL,
                                        credentialsError = r.error == AddProviderError.INVALID_CREDENTIALS,
                                    ),
                            )
                        }
                }
            }
        }

        private suspend fun add(form: ProviderForm): AddProviderResult =
            when (form.kind) {
                ProviderKind.M3U -> catalog.addM3u(form.name, form.url)
                ProviderKind.XTREAM -> catalog.addXtream(form.name, form.url, form.username, form.password)
            }

        private suspend fun runBusy(block: suspend () -> Result<Int>) {
            local.update { it.copy(busy = true) }
            block()
            local.update { it.copy(busy = false) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
