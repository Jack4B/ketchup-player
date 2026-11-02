package pl.ketchupplayer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.model.AccountInfo
import pl.ketchupplayer.tv.core.model.ProviderType
import javax.inject.Inject

/** Dane pigułki listy w nagłówku: nazwa aktywnej listy i (dla Xtream) zajęte/dozwolone połączenia. */
data class ListBadgeState(
    val name: String,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val full: Boolean,
)

/**
 * Pigułka „Lista X · połączenia N z M” (makieta v5). Aktywna lista = pierwsza dodana. Stan konta dla Xtream
 * odświeża się co minutę, ale tylko gdy nagłówek jest widoczny; M3U nie ma limitu, więc pokazuje samą nazwę.
 */
@HiltViewModel
class ListBadgeViewModel
    @Inject
    constructor(
        private val catalog: CatalogRepository,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val badge: StateFlow<ListBadgeState?> =
            catalog.providers
                .flatMapLatest { list ->
                    val p = list.firstOrNull()
                    flow {
                        if (p == null) {
                            emit(null)
                            return@flow
                        }
                        emit(ListBadgeState(p.name, null, null, false))
                        if (p.type != ProviderType.XTREAM) return@flow
                        while (true) {
                            catalog.accountInfo(p.id).onSuccess { emit(it.toBadge(p.name)) }
                            delay(REFRESH_MS)
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private fun AccountInfo.toBadge(name: String) =
            ListBadgeState(name, activeConnections, maxConnections, connectionsFull)

        private companion object {
            const val REFRESH_MS = 60_000L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
