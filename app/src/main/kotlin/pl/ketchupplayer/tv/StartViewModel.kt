package pl.ketchupplayer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.model.Channel
import javax.inject.Inject

@HiltViewModel
class StartViewModel
    @Inject
    constructor(
        catalog: CatalogRepository,
    ) : ViewModel() {
        /** Ostatnio oglądane kanały do wiersza „Ostatnio oglądane”. */
        val recent: StateFlow<List<Channel>> =
            catalog
                .recentChannels(RECENT_LIMIT)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

        private companion object {
            const val RECENT_LIMIT = 5
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
