package pl.ketchupplayer.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.epg.EpgRepository
import pl.ketchupplayer.tv.core.data.vod.VodRepository
import pl.ketchupplayer.tv.core.model.VodKind
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        catalog: CatalogRepository,
        epg: EpgRepository,
        vod: VodRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val ticker: Flow<Long> =
            flow {
                while (true) {
                    emit(clock.nowMs())
                    delay(TICK_MS)
                }
            }

        private val vodRows =
            combine(
                vod.continueWatching(RECENT_LIMIT),
                vod.myList(),
                vod.entries(VodKind.MOVIE),
            ) { cont, mine, movies ->
                Triple(cont, mine.take(RECENT_LIMIT), movies)
            }

        val state: StateFlow<HomeUiState> =
            combine(
                catalog.providers,
                catalog.recentChannels(RECENT_LIMIT),
                catalog.liveChannels(),
                catalog.favoriteIds(),
                combine(vodRows, ticker.flatMapLatest { now -> epg.onAir(now).map { now to it } }) { v, t -> v to t },
            ) { providers, recent, all, favorites, (vodPart, nowPart) ->
                val (cont, mine, movies) = vodPart
                val (now, onAir) = nowPart

                fun withNow(list: List<pl.ketchupplayer.tv.core.model.Channel>) =
                    list.map { ChannelNow(it, onAir[it.id]) }
                val live = all.filter { it.id in favorites }.ifEmpty { all }.take(LIVE_LIMIT)
                HomeUiState(
                    loading = false,
                    hasProviders = providers.isNotEmpty(),
                    recent = withNow(recent),
                    liveNow = withNow(live),
                    nowMs = now,
                    featured = cont.firstOrNull() ?: movies.maxByOrNull { it.item.rating ?: 0.0 },
                    featuredResumes = cont.isNotEmpty(),
                    continueVod = cont,
                    myList = mine,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

        private companion object {
            const val RECENT_LIMIT = 8
            const val LIVE_LIMIT = 12
            const val TICK_MS = 60_000L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
