package pl.ketchupplayer.tv.feature.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.vod.EpisodeEntry
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.data.vod.VodRepository
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DetailsViewModel
    @Inject
    constructor(
        private val repo: VodRepository,
    ) : ViewModel() {
        private val itemId = MutableStateFlow<String?>(null)
        private val season = MutableStateFlow<Int?>(null)
        private val episodesStatus = MutableStateFlow(EpisodesStatus.IDLE)
        private val similar = MutableStateFlow<List<VodItem>>(emptyList())

        private enum class EpisodesStatus { IDLE, LOADING, FAILED }

        fun bind(id: String) {
            if (itemId.value == id) return
            itemId.value = id
            season.value = null
            viewModelScope.launch { repo.refreshInfo(id) } // opis, obsada i grafika od dostawcy; wynik wpada do bazy
            viewModelScope.launch {
                similar.value = repo.similar(id, SIMILAR_LIMIT)
                loadEpisodesIfSeries(id)
            }
        }

        private suspend fun loadEpisodesIfSeries(id: String) {
            val entry = repo.entry(id).filterNotNull().first()
            if (entry.item.kind != VodKind.SERIES) return
            episodesStatus.value = EpisodesStatus.LOADING
            episodesStatus.value =
                if (repo.refreshEpisodes(id).isSuccess) EpisodesStatus.IDLE else EpisodesStatus.FAILED
            // Otwarcie serialu = nowe odcinki zobaczone (zielona kropka znika z plakatu i z szuflady).
            repo.markSeen(id)
        }

        val state: StateFlow<DetailsUiState> =
            itemId
                .filterNotNull()
                .flatMapLatest { id ->
                    combine(
                        repo.entry(id),
                        repo.episodes(id),
                        season,
                        episodesStatus,
                        similar,
                    ) { entry, episodes, selected, status, sim -> build(entry, episodes, selected, status, sim) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), DetailsUiState())

        fun onEvent(event: DetailsEvent) {
            when (event) {
                is DetailsEvent.SelectSeason -> season.value = event.season
                DetailsEvent.ToggleMyList ->
                    state.value.entry?.let { e -> viewModelScope.launch { repo.setMyList(e.item.id, !e.myList) } }
                DetailsEvent.RetryEpisodes ->
                    itemId.value?.let { id ->
                        viewModelScope.launch { loadEpisodesIfSeries(id) }
                    }
            }
        }

        private fun build(
            entry: VodEntry?,
            episodes: List<EpisodeEntry>,
            selected: Int?,
            status: EpisodesStatus,
            similar: List<VodItem>,
        ): DetailsUiState {
            if (entry == null) return DetailsUiState(loading = false)
            if (entry.item.kind == VodKind.MOVIE) {
                val target =
                    PlayTarget(entry.item.id, null, entry.positionMs.takeIf { it > 0 && entry.progress < DONE } ?: 0)
                return DetailsUiState(false, entry, similar = similar, target = target)
            }
            val seasons = episodes.map { it.episode.season }.distinct().sorted()
            val resume = resumeEpisode(episodes)
            val shown = selected ?: resume?.episode?.season ?: seasons.firstOrNull()
            return DetailsUiState(
                loading = false,
                entry = entry,
                seasons = seasons,
                season = shown,
                episodes = episodes.filter { it.episode.season == shown },
                episodesLoading = status == EpisodesStatus.LOADING && episodes.isEmpty(),
                episodesFailed = status == EpisodesStatus.FAILED && episodes.isEmpty(),
                similar = similar,
                target =
                    resume?.let {
                        PlayTarget(
                            it.episode.id,
                            "S${it.episode.season} E${it.episode.number}",
                            if (it.progress in MIN_PROGRESS..DONE) it.positionMs else 0,
                        )
                    },
            )
        }

        /** Rozpoczęty niedokończony odcinek; w przeciwnym razie pierwszy po ostatnim obejrzanym; w końcu pierwszy. */
        private fun resumeEpisode(episodes: List<EpisodeEntry>): EpisodeEntry? {
            episodes.firstOrNull { it.progress in MIN_PROGRESS..DONE && !it.watched }?.let { return it }
            val lastWatched = episodes.indexOfLast { it.watched }
            return episodes.getOrNull(lastWatched + 1) ?: episodes.firstOrNull()
        }

        private companion object {
            const val STOP_MS = 5_000L
            const val SIMILAR_LIMIT = 12
            const val DONE = 0.9f
            const val MIN_PROGRESS = 0.01f
        }
    }
