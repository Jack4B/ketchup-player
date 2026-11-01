package pl.ketchupplayer.tv.feature.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.epg.EpgRepository
import pl.ketchupplayer.tv.core.model.EpgProgramme
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveTvViewModel
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val epg: EpgRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val selection = MutableStateFlow(Selection())

        private val onAir: Flow<Pair<Long, Map<String, EpgProgramme>>> =
            ticker().flatMapLatest { now -> epg.onAir(now).map { now to it } }

        val state: StateFlow<LiveTvUiState> =
            combine(
                catalog.liveChannels(),
                catalog.liveGroups(),
                selection,
                onAir,
                catalog.favoriteIds(),
            ) { channels, groups, sel, (now, current), favorites ->
                val effective = sel.group?.takeIf { it in groups }
                val showFavorites = sel.favorites && favorites.isNotEmpty()
                LiveTvUiState(
                    loading = false,
                    groups = orderGroups(groups),
                    selectedGroup = if (showFavorites) null else effective,
                    channels =
                        when {
                            showFavorites -> channels.filter { it.id in favorites }
                            effective == null -> channels
                            else -> channels.filter { it.group == effective }
                        },
                    favorites = favorites,
                    favoritesSelected = showFavorites,
                    onAir = current,
                    nowMs = now,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LiveTvUiState())

        init {
            // Odświeżenie przewodnika w tle, gdy dane są starsze niż 6 godzin (jeden import naraz).
            viewModelScope.launch { epg.refreshIfStale(STALE_MS) }
        }

        fun onEvent(event: LiveTvEvent) {
            when (event) {
                is LiveTvEvent.SelectGroup -> selection.value = Selection(group = event.group)
                LiveTvEvent.SelectFavorites -> selection.value = Selection(favorites = true)
                is LiveTvEvent.ToggleFavorite ->
                    viewModelScope.launch {
                        catalog.setFavorite(event.channelId, event.channelId !in state.value.favorites)
                    }
            }
        }

        /** Co minutę nowy „teraz”, żeby pasek postępu i tytuł audycji się przesuwały. */
        private fun ticker(): Flow<Long> =
            flow {
                while (true) {
                    emit(clock.nowMs())
                    delay(TICK_MS)
                }
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val TICK_MS = 60_000L
            const val STALE_MS = 6L * 60 * 60 * 1000
        }
    }

private val polishGroup = Regex("^(PL|POLSKA|POLAND)([^A-Za-z]|$)", RegexOption.IGNORE_CASE)

/** Grupy polskie (`PL| ...`, `Polska`) na początku, reszta alfabetycznie; sortowanie jest stabilne. */
internal fun orderGroups(groups: List<String>): List<String> =
    groups.sortedWith(compareBy({ !polishGroup.containsMatchIn(it) }, { it.lowercase() }))

/** Wybór po lewej: wszystkie / grupa / ulubione. */
private data class Selection(
    val group: String? = null,
    val favorites: Boolean = false,
)
