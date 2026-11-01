package pl.ketchupplayer.tv.feature.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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
    ) : ViewModel() {
        private val kind = MutableStateFlow<VodKind?>(null)
        private val category = MutableStateFlow<String?>(null)
        private val query = MutableStateFlow("")
        private val sort = MutableStateFlow(VodSort.NEWEST)

        fun bind(value: VodKind) {
            if (kind.value != value) {
                kind.value = value
                category.value = null
                query.value = ""
            }
        }

        val state: StateFlow<VodBrowseUiState> =
            combine(
                kind.filterNotNull().flatMapLatest { k -> repo.entries(k) },
                kind,
                category,
                query,
                sort,
            ) { all, k, cat, q, s -> build(all, k ?: VodKind.MOVIE, cat, q, s) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), VodBrowseUiState())

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
                kind: VodKind,
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
                        VodSort.AZ -> filtered.sortedBy { it.item.normTitle }
                        VodSort.RATING -> filtered.sortedByDescending { it.item.rating ?: 0.0 }
                        VodSort.YEAR -> filtered.sortedByDescending { it.item.year ?: 0 }
                    }
                return VodBrowseUiState(kind, false, sorted, categories, all.size, category, query, sort)
            }
        }
    }
