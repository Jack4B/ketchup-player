package pl.ketchupplayer.tv.feature.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.catalog.PlaylistChoice
import pl.ketchupplayer.tv.core.data.catalog.PlaylistScope
import pl.ketchupplayer.tv.core.data.catalog.PlaylistScreen
import pl.ketchupplayer.tv.core.data.groups.GroupKind
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.data.vod.VodRepository
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.VodKind
import javax.inject.Inject

/** Wspólny dla Filmów i Seriali; rodzaj ustawia ekran przez [bind]. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VodBrowseViewModel
    @Inject
    constructor(
        private val repo: VodRepository,
        private val groups: pl.ketchupplayer.tv.core.data.groups.GroupsRepository,
        private val playlists: pl.ketchupplayer.tv.core.data.catalog.PlaylistFilterRepository,
    ) : ViewModel() {
        private val mode = MutableStateFlow<VodMode?>(null)
        private val category = MutableStateFlow<String?>(null)
        private val query = MutableStateFlow("")
        private val sort = MutableStateFlow(VodSort.NEWEST)

        fun bind(value: VodMode) {
            if (mode.value != value) {
                mode.value = value
                category.value = null
                query.value = ""
            }
        }

        val state: StateFlow<VodBrowseUiState> =
            combine(
                mode.filterNotNull().flatMapLatest { m -> visibleSource(m) },
                mode,
                category,
                query,
                sort,
            ) { scoped, m, cat, q, s ->
                build(scoped.entries, m ?: VodMode.MOVIES, cat, q, s).copy(playlist = scoped.playlist)
            }
                // Filtrowanie i sortowanie dużego katalogu poza głównym wątkiem (inaczej UI zamiera).
                .flowOn(kotlinx.coroutines.Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), VodBrowseUiState())

        private class Scoped(
            val entries: List<VodEntry>,
            val playlist: PlaylistChoice = PlaylistChoice(),
        )

        /**
         * Filmy i seriale z wybranej listy, bez ukrytych kategorii; kategoria o nazwie z kilku list jest osobna dla
         * każdej listy (PlaylistScope). Moja lista i Szukaj zostają pełne (to wybór użytkownika / cały katalog).
         */
        private fun visibleSource(mode: VodMode): Flow<Scoped> {
            val (kind, screen) =
                when (mode) {
                    VodMode.MOVIES -> GroupKind.MOVIES to PlaylistScreen.MOVIES
                    VodMode.SERIES -> GroupKind.SERIES to PlaylistScreen.SERIES
                    else -> return source(mode).map { Scoped(it) }
                }
            return combine(source(mode), groups.hidden(kind), playlists.choice(screen)) { all, hidden, choice ->
                Scoped(PlaylistScope.vod(all, choice, hidden), choice)
            }
        }

        private fun source(mode: VodMode): Flow<List<VodEntry>> =
            when (mode) {
                VodMode.MOVIES -> repo.entries(VodKind.MOVIE)
                VodMode.SERIES -> repo.entries(VodKind.SERIES)
                VodMode.MYLIST -> repo.myList()
                VodMode.SEARCH ->
                    combine(repo.entries(VodKind.MOVIE), repo.entries(VodKind.SERIES)) { m, s -> m + s }
            }

        fun onEvent(event: VodBrowseEvent) {
            when (event) {
                is VodBrowseEvent.SelectCategory -> category.value = event.category
                is VodBrowseEvent.Search -> query.value = event.query
                is VodBrowseEvent.Sort -> sort.value = event.sort
                is VodBrowseEvent.SelectPlaylist -> selectPlaylist(event.id)
                is VodBrowseEvent.Visible -> repo.requestMeta(event.itemId)
            }
        }

        /** Zmiana listy wraca do „Wszystkie” kategorie: kategorii poprzedniej listy może tu nie być. */
        private fun selectPlaylist(id: pl.ketchupplayer.tv.core.model.ProviderId?) {
            val screen =
                when (mode.value) {
                    VodMode.MOVIES -> PlaylistScreen.MOVIES
                    VodMode.SERIES -> PlaylistScreen.SERIES
                    else -> return
                }
            category.value = null
            viewModelScope.launch { playlists.select(screen, id) }
        }

        private companion object {
            const val STOP_MS = 5_000L
            const val RECENT_COUNT = 60

            fun build(
                all: List<VodEntry>,
                mode: VodMode,
                category: String?,
                query: String,
                sort: VodSort,
            ): VodBrowseUiState {
                val categories =
                    all
                        .mapNotNull { it.item.category }
                        .groupingBy { it }
                        .eachCount()
                        .toList()
                        .sortedBy { it.first }
                val needle = Channel.normalizeName(query)
                val recent =
                    all
                        .sortedByDescending { it.item.addedMs ?: 0L }
                        .take(
                            RECENT_COUNT,
                        ).map { it.item.id }
                        .toSet()
                val filtered =
                    all.filter { e ->
                        when (category) {
                            null -> true
                            CATEGORY_RECENT -> e.item.id in recent
                            else -> e.item.category == category
                        } &&
                            (needle.isEmpty() || e.item.normTitle.contains(needle))
                    }
                val sorted =
                    when (sort) {
                        // Stabilnie: przy braku dat zostaje kolejność dostawcy (inna niż A–Z).
                        VodSort.NEWEST -> filtered.sortedByDescending { it.item.addedMs ?: 0L }
                        // Klucz liczony raz na pozycję: porównywanie przez normTitle (regex w każdym porównaniu) trwało
                        // sekundy na 50 tys. pozycji i zawieszało aplikację na boxie.
                        VodSort.AZ ->
                            filtered
                                .map {
                                    it.item.title.lowercase() to it
                                }.sortedBy { it.first }
                                .map { it.second }
                        VodSort.RATING ->
                            filtered.sortedWith(
                                compareByDescending<VodEntry> { it.item.rating ?: 0.0 }.thenBy { it.item.title },
                            )
                        VodSort.YEAR ->
                            filtered.sortedWith(
                                compareByDescending<VodEntry> { it.item.year ?: 0 }.thenBy { it.item.title },
                            )
                    }
                return VodBrowseUiState(mode, false, sorted, categories, all.size, category, query, sort)
            }
        }
    }
