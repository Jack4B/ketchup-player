package pl.ketchupplayer.tv.feature.details

import pl.ketchupplayer.tv.core.data.vod.EpisodeEntry
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.model.VodItem

/** Co odtworzy przycisk główny: film albo odcinek, z której pozycji i jak go opisać (np. `S3 E2`). */
data class PlayTarget(
    val itemId: String,
    val code: String? = null,
    val positionMs: Long = 0,
) {
    val resuming: Boolean get() = positionMs > 0
}

data class DetailsUiState(
    val loading: Boolean = true,
    val entry: VodEntry? = null,
    val seasons: List<Int> = emptyList(),
    val season: Int? = null,
    /** Odcinki wybranego sezonu. */
    val episodes: List<EpisodeEntry> = emptyList(),
    val episodesLoading: Boolean = false,
    val episodesFailed: Boolean = false,
    val similar: List<VodItem> = emptyList(),
    val target: PlayTarget? = null,
) {
    val isSeries: Boolean get() = entry?.item?.kind == pl.ketchupplayer.tv.core.model.VodKind.SERIES
}

sealed interface DetailsEvent {
    data class SelectSeason(
        val season: Int,
    ) : DetailsEvent

    data object ToggleMyList : DetailsEvent

    data object RetryEpisodes : DetailsEvent
}
