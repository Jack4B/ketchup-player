package pl.ketchupplayer.tv.feature.livetv

import pl.ketchupplayer.tv.core.model.Channel

data class LiveTvUiState(
    val loading: Boolean = true,
    val groups: List<String> = emptyList(),
    /** null = wszystkie grupy. */
    val selectedGroup: String? = null,
    val channels: List<Channel> = emptyList(),
)

sealed interface LiveTvEvent {
    data class SelectGroup(
        val group: String?,
    ) : LiveTvEvent
}
