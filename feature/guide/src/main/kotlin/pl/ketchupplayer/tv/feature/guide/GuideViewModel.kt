package pl.ketchupplayer.tv.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.epg.EpgRepository
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.epg.radar.InterestMatcher
import pl.ketchupplayer.tv.epg.radar.RadarEngine
import pl.ketchupplayer.tv.epg.radar.RadarPolicy
import pl.ketchupplayer.tv.epg.radar.RadarSlot
import javax.inject.Inject

@HiltViewModel
class GuideViewModel
    @Inject
    constructor(
        catalog: CatalogRepository,
        private val epg: EpgRepository,
        private val interestsRepo: pl.ketchupplayer.tv.core.data.interests.InterestsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        /** Okno liczone raz na wejście do ekranu; przewijanie czasu w przód to kolejna iteracja. */
        private val windowStart = GuideGeometry.windowStart(clock.nowMs())

        private val epgRevision = MutableStateFlow(0)

        init {
            // Przewodnik może być pierwszym ekranem po starcie: dane EPG nie mogą zależeć od wizyty w „Na żywo”.
            // Po imporcie wiersze przeładowują audycje (inaczej zostałby napis „Brak danych przewodnika”).
            viewModelScope.launch {
                epg.refreshIfStale(STALE_MS)
                epgRevision.value++
            }
        }

        val state: StateFlow<GuideUiState> =
            combine(catalog.liveChannels(), catalog.favoriteIds(), epgRevision) { channels, favorites, revision ->
                GuideUiState(
                    loading = false,
                    channels = channels.sortedByDescending { it.id in favorites },
                    favorites = favorites,
                    windowStartMs = windowStart,
                    windowEndMs = GuideGeometry.windowEnd(windowStart),
                    nowMs = clock.nowMs(),
                    epgRevision = revision,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                GuideUiState(windowStartMs = windowStart, windowEndMs = GuideGeometry.windowEnd(windowStart)),
            )

        private val radarSlot = MutableStateFlow(RadarSlot.TOP_DAY)

        /** Radar: wybrany slot, przeliczany po imporcie EPG, zmianie ulubionych albo kanałów. */
        val radar: StateFlow<RadarUiState> =
            combine(
                catalog.liveChannels(),
                catalog.favoriteIds(),
                radarSlot,
                epgRevision,
            ) { channels, favorites, slot, _ ->
                val now = clock.nowMs()
                val window = radarWindow(now)
                val programmes = epg.window(window.dayStart, window.dayEnd)
                RadarUiState(slot, false, RadarEngine.build(slot, now, window, channels, programmes, favorites))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RadarUiState())

        /** Moje zainteresowania: słowa kluczowe + audycje z najbliższych 24 h, które do nich pasują. */
        val interests: StateFlow<InterestsUiState> =
            combine(interestsRepo.keywords, catalog.liveChannels(), epgRevision) { keywords, channels, _ ->
                val now = clock.nowMs()
                val programmes = if (keywords.isEmpty()) emptyList() else epg.window(now, now + 24 * HOUR_MS)
                InterestsUiState(keywords, InterestMatcher.match(keywords, channels, programmes, now))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InterestsUiState())

        fun addInterest(keyword: String) {
            viewModelScope.launch { interestsRepo.add(keyword) }
        }

        fun removeInterest(keyword: String) {
            viewModelScope.launch { interestsRepo.remove(keyword) }
        }

        fun selectRadarSlot(slot: RadarSlot) {
            radarSlot.value = slot
        }

        /** Dzień = lokalna doba, wieczór = 18:00–23:00 lokalnie (strefa urządzenia). */
        private fun radarWindow(now: Long): RadarPolicy.Window {
            val cal =
                java.util.Calendar
                    .getInstance()
                    .apply { timeInMillis = now }
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            val hour = HOUR_MS
            return RadarPolicy.Window(
                dayStart,
                dayStart + 24 * hour,
                dayStart + EVENING_FROM_H * hour,
                dayStart + EVENING_TO_H * hour,
            )
        }

        /** Audycje jednego wiersza; wołane leniwie dla widocznych kanałów. */
        suspend fun programmes(channelId: String): List<EpgProgramme> {
            val s = state.value
            return epg.range(channelId, s.windowStartMs, s.windowEndMs)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val STALE_MS = 6L * 60 * 60 * 1000
            const val HOUR_MS = 60L * 60 * 1000
            const val EVENING_FROM_H = 18
            const val EVENING_TO_H = 23
        }
    }
