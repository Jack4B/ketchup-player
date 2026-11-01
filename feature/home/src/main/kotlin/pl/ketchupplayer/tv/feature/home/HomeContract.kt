package pl.ketchupplayer.tv.feature.home

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme

/** Kanał z audycją trwającą teraz (jeśli jest EPG). */
data class ChannelNow(
    val channel: Channel,
    val now: EpgProgramme?,
)

data class HomeUiState(
    val loading: Boolean = true,
    val hasProviders: Boolean = false,
    /** Ostatnio oglądane kanały (najnowszy pierwszy). */
    val recent: List<ChannelNow> = emptyList(),
    /** Ulubione, a gdy ich brak: początek listy kanałów. */
    val liveNow: List<ChannelNow> = emptyList(),
    val nowMs: Long = 0,
)
