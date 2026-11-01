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
import javax.inject.Inject

@HiltViewModel
class GuideViewModel
    @Inject
    constructor(
        catalog: CatalogRepository,
        private val epg: EpgRepository,
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

        /** Audycje jednego wiersza; wołane leniwie dla widocznych kanałów. */
        suspend fun programmes(channelId: String): List<EpgProgramme> {
            val s = state.value
            return epg.range(channelId, s.windowStartMs, s.windowEndMs)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val STALE_MS = 6L * 60 * 60 * 1000
        }
    }
