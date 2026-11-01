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
import kotlinx.coroutines.flow.stateIn
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
            ) { all, m, cat, q, s -> build(all, m ?: VodMode.MOVIES, cat, q, s) }
                // Filtrowanie i sortowanie dużego katalogu poza głównym wątkiem (inaczej UI zamiera).
                .flowOn(kotlinx.coroutines.Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), VodBrowseUiState())

        /** Filmy i seriale bez ukrytych kategorii; Moja lista zostaje pełna (to wybór użytkownika). */
        private fun visibleSource(mode: VodMode): Flow<List<VodEntry>> {
            val kind =
                when (mode) {
                    VodMode.MOVIES -> pl.ketchupplayer.tv.core.data.groups.GroupKind.MOVIES
                    VodMode.SERIES -> pl.ketchupplayer.tv.core.data.groups.GroupKind.SERIES
                    else -> return source(mode)
                }
            return combine(source(mode), groups.hidden(kind)) { all, hidden ->
                if (hidden.isEmpty()) all else all.filter { it.item.category !in hidden }
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
            }
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
                        VodSort.NEWEST ->
                            filtered.sortedWith(
                                compareByDescending<VodEntry> { it.item.addedMs ?: 0L }.thenBy { it.item.title },
                            )
                        // Klucz liczony raz na pozycję: porównywanie przez normTitle (regex w każdym porównaniu) trwało
                        // sekundy na 50 tys. pozycji i zawieszało aplikację na boxie.
                        VodSort.AZ ->
                            filtered
                                .map {
                                    it.item.title.lowercase() to it
                                }.sortedBy { it.first }
                                .map { it.second }
                        VodSort.RATING -> filtered.sortedByDescending { it.item.rating ?: 0.0 }
                        VodSort.YEAR -> filtered.sortedByDescending { it.item.year ?: 0 }
                    }
                return VodBrowseUiState(mode, false, sorted, categories, all.size, category, query, sort)
            }
        }
    }
