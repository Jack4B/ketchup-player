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
)

sealed interface LiveTvEvent {
    data class SelectGroup(
        val group: String?,
    ) : LiveTvEvent

    data object SelectFavorites : LiveTvEvent

    data class ToggleFavorite(
        val channelId: String,
    ) : LiveTvEvent
}
