package pl.ketchupplayer.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.groups.GroupKind
import pl.ketchupplayer.tv.core.data.groups.GroupOption
import pl.ketchupplayer.tv.core.data.groups.GroupsRepository
import javax.inject.Inject

/** Wybrany rodzaj (Kanały / Filmy / Seriale) i jego grupy z przełącznikami widoczności. */
data class GroupsUiState(
    val kind: GroupKind = GroupKind.TV,
    val options: List<GroupOption> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsSettingsViewModel
    @Inject
    constructor(
        private val repo: GroupsRepository,
    ) : ViewModel() {
        private val kind = MutableStateFlow(GroupKind.TV)

        val state: StateFlow<GroupsUiState> =
            combine(kind, kind.flatMapLatest { repo.options(it) }) { k, o -> GroupsUiState(k, o) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), GroupsUiState())

        fun selectKind(value: GroupKind) {
            kind.value = value
        }

        fun toggle(name: String) {
            val current = state.value
            val visible = current.options.firstOrNull { it.name == name }?.visible ?: return
            viewModelScope.launch { repo.setVisible(current.kind, name, !visible) }
        }

        fun setAll(visible: Boolean) {
            viewModelScope.launch { repo.setAllVisible(kind.value, visible) }
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
