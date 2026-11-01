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
        private val focusedId = MutableStateFlow<String?>(null)

        private val onAir: Flow<Pair<Long, Map<String, EpgProgramme>>> =
            ticker().flatMapLatest { now -> epg.onAir(now).map { now to it } }

        private val base: Flow<Base> =
            combine(
                catalog.liveChannels(),
                catalog.liveGroups(),
                selection,
                catalog.favoriteIds(),
                catalog.recentChannels(RECENT_LIMIT),
            ) { channels, groups, sel, favorites, recent ->
                val effective = sel.group?.takeIf { it in groups }
                val showFavorites = sel.favorites && favorites.isNotEmpty()
                val showRecent = sel.recent && recent.isNotEmpty()
                Base(
                    channels,
                    groups,
                    favorites,
                    recent,
                    effective = if (showFavorites || showRecent) null else effective,
                    showFavorites = showFavorites,
                    showRecent = showRecent,
                )
            }

        private val details: Flow<ChannelDetails?> =
            combine(focusedId, onAir, catalog.providers) { id, air, providers -> Triple(id, air, providers) }
                .flatMapLatest { (id, air, providers) ->
                    flow {
                        val channel = id?.let { catalog.channel(it) }
                        if (channel == null) {
                            emit(null)
                        } else {
                            val (now, current) = air
                            val here = current[channel.id]
                            val next =
                                epg
                                    .range(channel.id, now, now + NEXT_WINDOW_MS)
                                    .firstOrNull { it.startMs >= (here?.endMs ?: now) }
                            val index = providers.indexOfFirst { it.id == channel.providerId }
                            val source = providers.getOrNull(index)?.let { "Lista ${index + 1} · ${it.name}" }.orEmpty()
                            emit(ChannelDetails(channel, here, next, source))
                        }
                    }
                }

        val state: StateFlow<LiveTvUiState> =
            combine(base, onAir, details) { b, (now, current), detail ->
                val visible =
                    when {
                        b.showFavorites -> b.channels.filter { it.id in b.favorites }
                        b.showRecent -> b.recent
                        b.effective == null -> b.channels
                        else -> b.channels.filter { it.group == b.effective }
                    }
                LiveTvUiState(
                    loading = false,
                    groups = orderGroups(b.groups),
                    selectedGroup = b.effective,
                    channels = visible,
                    favorites = b.favorites,
                    favoritesSelected = b.showFavorites,
                    onAir = current,
                    nowMs = now,
                    groupCounts = b.channels.groupingBy { it.group.orEmpty() }.eachCount(),
                    totalCount = b.channels.size,
                    recentCount = b.recent.size,
                    recentSelected = b.showRecent,
                    details = detail,
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
                LiveTvEvent.SelectRecent -> selection.value = Selection(recent = true)
                is LiveTvEvent.FocusChannel -> focusedId.value = event.channelId
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
            const val RECENT_LIMIT = 30
            const val NEXT_WINDOW_MS = 6L * 60 * 60 * 1000
        }
    }

private val polishGroup = Regex("^(PL|POLSKA|POLAND)([^A-Za-z]|$)", RegexOption.IGNORE_CASE)

/** Grupy polskie (`PL| ...`, `Polska`) na początku, reszta alfabetycznie; sortowanie jest stabilne. */
internal fun orderGroups(groups: List<String>): List<String> =
    groups.sortedWith(compareBy({ !polishGroup.containsMatchIn(it) }, { it.lowercase() }))

/** Wybór po lewej: wszystkie / grupa / ulubione / ostatnie. */
private data class Selection(
    val group: String? = null,
    val favorites: Boolean = false,
    val recent: Boolean = false,
)

private data class Base(
    val channels: List<pl.ketchupplayer.tv.core.model.Channel>,
    val groups: List<String>,
    val favorites: Set<String>,
    val recent: List<pl.ketchupplayer.tv.core.model.Channel>,
    val effective: String?,
    val showFavorites: Boolean,
    val showRecent: Boolean,
)
