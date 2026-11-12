package pl.ketchupplayer.tv.player.engine

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** Przekazuje zdarzenia analityczne Media3 do [PlaybackStatsCollector]. */
@UnstableApi
internal class StatsAnalyticsListener(
    private val collector: PlaybackStatsCollector,
) : AnalyticsListener {
    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int,
    ) = collector.onPlaybackState(state)

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) = collector.onDroppedFrames(droppedFrames)

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long,
    ) = collector.onBandwidth(bitrateEstimate)
}
