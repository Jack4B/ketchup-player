package pl.ketchupplayer.tv.feature.home

import pl.ketchupplayer.tv.core.data.vod.VodEntry
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
    /** Hero: film do wznowienia, a gdy brak - najwyżej oceniana pozycja katalogu. */
    val featured: VodEntry? = null,
    val featuredResumes: Boolean = false,
    val continueVod: List<VodEntry> = emptyList(),
    val myList: List<VodEntry> = emptyList(),
    /** Seriale z nowymi odcinkami od ostatniej wizyty w szczegółach (zielona kropka). */
    val newEpisodes: List<VodEntry> = emptyList(),
)
