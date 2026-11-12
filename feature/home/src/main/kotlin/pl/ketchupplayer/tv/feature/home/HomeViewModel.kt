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
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.epg.EpgRepository
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationRepository
import pl.ketchupplayer.tv.core.data.vod.VodRepository
import pl.ketchupplayer.tv.core.model.VodKind
import pl.ketchupplayer.tv.core.model.personalization.HomeSnapshot
import pl.ketchupplayer.tv.core.model.personalization.SportCard
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        catalog: CatalogRepository,
        epg: EpgRepository,
        private val vod: VodRepository,
        private val clock: Clock,
        private val personalization: PersonalizationRepository,
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
                vod.entries(VodKind.SERIES),
                vod.timedMovies(TIMED_LIMIT),
            ) { cont, mine, movies, series, timed ->
                VodRows(
                    cont,
                    mine.take(RECENT_LIMIT),
                    movies,
                    series.filter { it.hasNewEpisodes }.take(RECENT_LIMIT),
                    timed,
                    series,
                )
            }

        /** Gotowy pas personalizacji z bazy: ekran niczego nie liczy, tylko zamienia id na pozycje katalogu. */
        private val personal =
            combine(personalization.snapshot, personalization.liked) { snapshot, liked ->
                snapshot to
                    liked
            }

        fun like(
            itemId: String,
            liked: Boolean,
        ) {
            viewModelScope.launch { personalization.setLiked(itemId, liked) }
        }

        fun hide(itemId: String) {
            viewModelScope.launch { personalization.hide(itemId) }
        }

        fun follow(teamIds: List<String>) {
            viewModelScope.launch { teamIds.forEach { personalization.follow(it) } }
        }

        fun decline(teamIds: List<String>) {
            viewModelScope.launch { personalization.reject(teamIds) }
        }

        init {
            // Czas trwania filmów dociągamy w tle, po kilkadziesiąt naraz. Katalog VOD dochodzi dopiero po imporcie
            // listy, więc pętla powtarza próbę (przy braku pracy to tylko puste zapytanie do bazy).
            viewModelScope.launch {
                delay(ENRICH_DELAY_MS)
                while (true) {
                    runCatching { vod.enrichDurations(ENRICH_LIMIT) }
                    delay(ENRICH_EVERY_MS)
                }
            }
        }

        val state: StateFlow<HomeUiState> =
            combine(
                catalog.providers,
                catalog.recentChannels(RECENT_LIMIT),
                catalog.liveChannels(),
                catalog.favoriteIds(),
                combine(
                    vodRows,
                    ticker.flatMapLatest { now -> epg.onAir(now).map { now to it } },
                    personal,
                ) { v, t, p -> Triple(v, t, p) },
            ) { providers, recent, all, favorites, (vodPart, nowPart, personalPart) ->
                val cont = vodPart.cont
                val mine = vodPart.mine
                val movies = vodPart.movies
                val fresh = vodPart.fresh
                val (now, onAir) = nowPart

                fun withNow(list: List<pl.ketchupplayer.tv.core.model.Channel>) =
                    list.map { ChannelNow(it, onAir[it.id]) }
                val live = all.filter { it.id in favorites }.ifEmpty { all }.take(LIVE_LIMIT)
                val sport = sportRow(personalPart.first, now)
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
                    newEpisodes = fresh,
                    timed = vodPart.timed,
                    because = becauseRow(personalPart.first, personalPart.second, vodPart),
                    sport = sport,
                    sportSoon = sport.any { it.kickoffUtcMs - now < SPORT_SOON_MS },
                    followSuggestion = personalPart.first.suggestion,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

        /** Karty „Bo oglądasz” tylko dla pozycji, które nadal są w katalogu; kolejność z rankingu zostaje. */
        private fun becauseRow(
            snapshot: HomeSnapshot,
            liked: Set<String>,
            rows: VodRows,
        ): List<BecauseEntry> {
            if (snapshot.because.isEmpty()) return emptyList()
            val wanted = snapshot.because.map { it.itemId }.toSet()
            val shown = (rows.cont + rows.mine).map { it.item.id }.toSet()
            val found =
                (rows.movies.asSequence() + rows.series)
                    .filter {
                        it.item.id in wanted
                    }.associateBy { it.item.id }
            return snapshot.because.mapNotNull { card ->
                val entry = found[card.itemId]?.takeIf { card.itemId !in shown } ?: return@mapNotNull null
                BecauseEntry(entry, card.why, card.explore, card.itemId in liked)
            }
        }

        /** Mecze, które jeszcze trwają albo dopiero będą; „na żywo” liczone z bieżącej godziny, nie z chwili zapisu. */
        private fun sportRow(
            snapshot: HomeSnapshot,
            now: Long,
        ): List<SportCard> =
            snapshot.sport
                .filter { (it.endUtcMs ?: (it.kickoffUtcMs + DEFAULT_MATCH_MS)) > now }
                .map { it.copy(live = it.live || it.kickoffUtcMs <= now) }

        private data class VodRows(
            val cont: List<pl.ketchupplayer.tv.core.data.vod.VodEntry>,
            val mine: List<pl.ketchupplayer.tv.core.data.vod.VodEntry>,
            val movies: List<pl.ketchupplayer.tv.core.data.vod.VodEntry>,
            val fresh: List<pl.ketchupplayer.tv.core.data.vod.VodEntry>,
            val timed: List<pl.ketchupplayer.tv.core.data.vod.VodEntry>,
            val series: List<pl.ketchupplayer.tv.core.data.vod.VodEntry> = emptyList(),
        )

        private companion object {
            const val RECENT_LIMIT = 8
            const val TIMED_LIMIT = 300
            const val ENRICH_LIMIT = 40
            const val ENRICH_DELAY_MS = 4_000L
            const val ENRICH_EVERY_MS = 15_000L
            const val LIVE_LIMIT = 12
            const val TICK_MS = 60_000L
            const val STOP_TIMEOUT_MS = 5_000L
            const val SPORT_SOON_MS = 48L * 60 * 60 * 1000
            const val DEFAULT_MATCH_MS = 2L * 60 * 60 * 1000
        }
    }
