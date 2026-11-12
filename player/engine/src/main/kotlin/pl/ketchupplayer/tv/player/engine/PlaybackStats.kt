package pl.ketchupplayer.tv.player.engine

/**
 * Migawka diagnostyki odtwarzania (panel Info w odtwarzaczu): ile materiału jest w buforze, jak szybkie jest łącze,
 * ile było przerw i zgubionych klatek w tej sesji.
 */
data class PlaybackStats(
    val bufferAheadMs: Long = 0,
    /** Szacowana przepustowość łącza w kb/s (z odczytów segmentów); null, dopóki nic nie pobrano. */
    val bandwidthKbps: Long? = null,
    val droppedFrames: Long = 0,
    val rebuffers: Int = 0,
    val stallMs: Long = 0,
    val playedMs: Long = 0,
)

internal val NO_STATS_FLOW: kotlinx.coroutines.flow.StateFlow<PlaybackStats> =
    kotlinx.coroutines.flow.MutableStateFlow(PlaybackStats())
