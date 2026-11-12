package pl.ketchupplayer.tv.feature.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.catalog.AddProviderError
import pl.ketchupplayer.tv.core.data.catalog.AddProviderResult
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.catalog.ProviderEdit
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderType
import javax.inject.Inject

@HiltViewModel
class ProvidersViewModel
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        headers: pl.ketchupplayer.tv.core.data.network.ProviderHeadersRepository,
    ) : ViewModel() {
        private val customs =
            headers.customs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private val local = MutableStateFlow(ProvidersUiState())
        private val accounts = MutableStateFlow<Map<String, pl.ketchupplayer.tv.core.model.AccountInfo>>(emptyMap())

        init {
            // Konta Xtream: połączenia i ważność po każdej zmianie listy dostawców (dodanie, odświeżenie).
            viewModelScope.launch {
                catalog.providers.collectLatest { list ->
                    list.filter { it.type == pl.ketchupplayer.tv.core.model.ProviderType.XTREAM }.forEach { p ->
                        catalog.accountInfo(p.id).onSuccess { info -> accounts.update { it + (p.id.value to info) } }
                    }
                }
            }
        }

        val state: StateFlow<ProvidersUiState> =
            combine(
                catalog.providers,
                local,
                catalog.importStatus,
                customs,
                accounts,
            ) { providers, l, st, c, acc ->
                l.copy(providers = providers, status = st, customAgents = c, accounts = acc)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProvidersUiState())

        private var failedId: ProviderId? = null

        fun onEvent(event: ProvidersEvent) {
            when (event) {
                ProvidersEvent.StartAdd -> local.update { it.copy(form = ProviderForm(), message = null) }
                is ProvidersEvent.StartEdit -> startEdit(event.id)
                ProvidersEvent.Cancel -> local.update { it.copy(form = null) }
                ProvidersEvent.RetryAdd -> retryAdd()
                is ProvidersEvent.NameChanged,
                is ProvidersEvent.UsernameChanged,
                is ProvidersEvent.PasswordChanged,
                is ProvidersEvent.SelectKind,
                is ProvidersEvent.UrlChanged,
                is ProvidersEvent.SelectUserAgent,
                is ProvidersEvent.UserAgentTextChanged,
                is ProvidersEvent.SelectRequestMethod,
                -> local.update { it.copy(form = it.form?.let { f -> edited(f, event) }) }
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

        /**
         * Formularz edycji zapisanej listy: nazwa, rodzaj, metoda i wybrany nagłówek z bazy. Adres, login, hasło
         * i wartość własnego nagłówka zostają puste (to dane dostępu: nie wracają na ekran, puste = bez zmian).
         */
        private fun startEdit(id: ProviderId) {
            val p = state.value.providers.firstOrNull { it.id == id } ?: return
            val ua =
                when (p.userAgentId) {
                    null -> "auto"
                    CUSTOM_STORED -> "custom:"
                    else -> p.userAgentId ?: "auto"
                }
            local.update {
                it.copy(
                    message = null,
                    form =
                        ProviderForm(
                            kind = if (p.type == ProviderType.XTREAM) ProviderKind.XTREAM else ProviderKind.M3U,
                            name = p.name,
                            requestMethod = p.requestMethod,
                            userAgentId = ua,
                            editingId = p.id,
                            editingHost = p.urlHost,
                        ),
                )
            }
        }

        /** Wpisujemy inną listę po błędzie: nieudany wpis znika, żeby nie zostały duplikaty. */
        private fun retryAdd() {
            failedId?.let { id -> viewModelScope.launch { catalog.remove(id) } }
            failedId = null
            local.update { it.copy(form = it.lastForm ?: ProviderForm()) }
        }

        private fun edited(
            form: ProviderForm,
            event: ProvidersEvent,
        ): ProviderForm =
            when (event) {
                is ProvidersEvent.NameChanged -> form.copy(name = event.value, nameError = false)
                is ProvidersEvent.UsernameChanged -> form.copy(username = event.value, credentialsError = false)
                is ProvidersEvent.PasswordChanged -> form.copy(password = event.value, credentialsError = false)
                is ProvidersEvent.SelectKind -> form.copy(kind = event.kind)
                is ProvidersEvent.UrlChanged -> form.copy(url = event.value, urlError = false)
                is ProvidersEvent.SelectUserAgent ->
                    form.copy(userAgentId = event.id, userAgentError = false, userAgentChanged = true)
                is ProvidersEvent.UserAgentTextChanged ->
                    form.copy(userAgentId = "custom:" + event.value, userAgentError = false, userAgentChanged = true)
                is ProvidersEvent.SelectRequestMethod -> form.copy(requestMethod = event.method)
                else -> form
            }

        private fun submit() {
            val form = local.value.form ?: return
            viewModelScope.launch {
                local.update { it.copy(busy = true, lastForm = form) }
                when (val r = if (form.editingId != null) update(form.editingId, form) else add(form)) {
                    is AddProviderResult.Added -> local.update { it.copy(form = null, busy = false, message = null) }
                    is AddProviderResult.SavedWithError -> {
                        // Edytowana lista istniała wcześniej: „Popraw dane” nie może jej usuwać.
                        failedId = r.id.takeIf { form.editingId == null }
                        local.update { it.copy(form = null, busy = false, message = r.message) }
                    }
                    is AddProviderResult.Invalid ->
                        local.update {
                            it.copy(
                                busy = false,
                                form =
                                    it.form?.copy(
                                        nameError = r.error == AddProviderError.INVALID_NAME,
                                        urlError = r.error == AddProviderError.INVALID_URL,
                                        credentialsError = r.error == AddProviderError.INVALID_CREDENTIALS,
                                        userAgentError = r.error == AddProviderError.INVALID_USER_AGENT,
                                    ),
                            )
                        }
                }
            }
        }

        private suspend fun update(
            id: ProviderId,
            form: ProviderForm,
        ): AddProviderResult =
            catalog.update(
                id,
                ProviderEdit(
                    name = form.name,
                    url = form.url,
                    username = form.username,
                    password = form.password,
                    userAgentId = form.userAgentId.takeIf { form.userAgentChanged },
                    requestMethod = form.requestMethod,
                ),
            )

        private suspend fun add(form: ProviderForm): AddProviderResult =
            when (form.kind) {
                ProviderKind.M3U -> catalog.addM3u(form.name, form.url, form.userAgentId, form.requestMethod)
                ProviderKind.XTREAM ->
                    catalog.addXtream(
                        form.name,
                        form.url,
                        form.username,
                        form.password,
                        form.userAgentId,
                        form.requestMethod,
                    )
            }

        private suspend fun runBusy(block: suspend () -> Result<Int>) {
            local.update { it.copy(busy = true) }
            block()
            local.update { it.copy(busy = false) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L

            /** Znacznik zapisanego własnego nagłówka w bazie (jego wartość jest w SecretStore). */
            const val CUSTOM_STORED = "custom"
        }
    }
