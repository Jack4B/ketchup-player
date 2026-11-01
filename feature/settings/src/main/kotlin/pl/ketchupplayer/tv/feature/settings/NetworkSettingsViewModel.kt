package pl.ketchupplayer.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.network.ProviderHeadersRepository
import pl.ketchupplayer.tv.core.network.UserAgentProfile
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import javax.inject.Inject

/** Lista z jej aktualnym nagłówkiem połączenia. */
data class ProviderHeaderRow(
    val providerId: String,
    val name: String,
    val profile: UserAgentProfile,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NetworkSettingsViewModel
    @Inject
    constructor(
        catalog: CatalogRepository,
        private val headers: ProviderHeadersRepository,
    ) : ViewModel() {
        val customs: StateFlow<List<UserAgentProfile>> =
            headers.customs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

        /** Dodaje własny User-Agent; false = niepoprawny (puste, ponad 512 znaków, znaki spoza ASCII). */
        fun addCustom(value: String): Boolean {
            val valid = UserAgentProfiles.validated(value) != null
            if (valid) viewModelScope.launch { headers.addCustom(value) }
            return valid
        }

        fun removeCustom(profile: UserAgentProfile) {
            viewModelScope.launch { headers.removeCustom(profile.value.orEmpty()) }
        }

        val rows: StateFlow<List<ProviderHeaderRow>> =
            catalog.providers
                .flatMapLatest { list ->
                    if (list.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(list.map { rowFlow(it.id.value, it.name) }) { it.toList() }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

        private fun rowFlow(
            id: String,
            name: String,
        ): Flow<ProviderHeaderRow> =
            headers.profile(id).let { f ->
                combine(f, flowOf(name)) { p, n -> ProviderHeaderRow(id, n, p) }
            }

        /** OK na liście: kolejny nagłówek z gotowej puli (bez wpisywania). */
        fun cycle(row: ProviderHeaderRow) {
            val all = UserAgentProfiles.ALL + customs.value
            val next = all[(all.indexOf(row.profile) + 1).mod(all.size)]
            viewModelScope.launch { headers.set(row.providerId, next) }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
