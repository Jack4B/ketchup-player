package pl.ketchupplayer.tv.feature.livetv

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme

data class LiveTvUiState(
    val loading: Boolean = true,
    val groups: List<String> = emptyList(),
    /** null = wszystkie grupy. */
    val selectedGroup: String? = null,
    val channels: List<Channel> = emptyList(),
    /** Id ulubionych kanałów (gwiazdka); zmiana: długie OK na kanale. */
    val favorites: Set<String> = emptySet(),
    /** Wybrana wirtualna grupa „Ulubione” (wyklucza [selectedGroup]). */
    val favoritesSelected: Boolean = false,
    /** Audycja trwająca teraz: id kanału -> audycja. */
    val onAir: Map<String, EpgProgramme> = emptyMap(),
    val nowMs: Long = 0,
    /** Liczba kanałów w grupach (do liczników na liście grup) i w grupie „Wszystkie”. */
    val groupCounts: Map<String, Int> = emptyMap(),
    val totalCount: Int = 0,
    /** Liczba ostatnio oglądanych kanałów; wirtualna grupa „Ostatnie”. */
    val recentCount: Int = 0,
    val recentSelected: Boolean = false,
    /** Szczegóły kanału pod fokusem (panel po prawej). */
    val details: ChannelDetails? = null,
)

/** Panel szczegółów: audycja teraz i następna, źródło (lista dostawcy). */
data class ChannelDetails(
    val channel: Channel,
    val now: EpgProgramme?,
    val next: EpgProgramme?,
    /** np. „Lista 1 · Dostawca A”. */
    val source: String,
)

sealed interface LiveTvEvent {
    data class SelectGroup(
        val group: String?,
    ) : LiveTvEvent

    data object SelectFavorites : LiveTvEvent

    data object SelectRecent : LiveTvEvent

    /** Fokus wszedł na kanał: panel szczegółów pokazuje jego audycję. */
    data class FocusChannel(
        val channelId: String,
    ) : LiveTvEvent

    data class ToggleFavorite(
        val channelId: String,
    ) : LiveTvEvent
}
