package pl.ketchupplayer.tv.feature.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import javax.inject.Inject

@HiltViewModel
class LiveTvViewModel
    @Inject
    constructor(
        catalog: CatalogRepository,
    ) : ViewModel() {
        private val group = MutableStateFlow<String?>(null)

        val state: StateFlow<LiveTvUiState> =
            combine(catalog.liveChannels(), catalog.liveGroups(), group) { channels, groups, selected ->
                val effective = selected?.takeIf { it in groups }
                LiveTvUiState(
                    loading = false,
                    groups = groups,
                    selectedGroup = effective,
                    channels = if (effective == null) channels else channels.filter { it.group == effective },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LiveTvUiState())

        fun onEvent(event: LiveTvEvent) {
            when (event) {
                is LiveTvEvent.SelectGroup -> group.value = event.group
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
