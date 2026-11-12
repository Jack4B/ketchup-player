package pl.ketchupplayer.tv.player.engine

import androidx.media3.common.Player

/**
 * Zbiera statystyki sesji z zdarzeń odtwarzacza. Przerwa (rebuffer) to wejście w buforowanie PO tym, jak materiał
 * już grał; pierwsze buforowanie po starcie i po przewinięciu nie liczy się jako awaria łącza.
 * Czysta logika bez ExoPlayera: zdarzenia podaje [StatsAnalyticsListener], czas dostarcza [now].
 */
class PlaybackStatsCollector(
    private val now: () -> Long,
) {
    private var started = false
    private var lastState = Player.STATE_IDLE
    private var bufferingSince = 0L
    private var countedAsRebuffer = false
    private var playingSince = 0L
    private var rebuffers = 0
    private var stallMs = 0L
    private var dropped = 0L
    private var bandwidthKbps: Long? = null
    private var playedBeforeMs = 0L

    fun reset() {
        started = false
        lastState = Player.STATE_IDLE
        bufferingSince = 0L
        countedAsRebuffer = false
        playingSince = 0L
        rebuffers = 0
        stallMs = 0L
        dropped = 0L
        bandwidthKbps = null
        playedBeforeMs = 0L
    }

    fun onPlaybackState(state: Int) {
        val t = now()
        when (state) {
            Player.STATE_BUFFERING -> {
                if (lastState == Player.STATE_READY && started) {
                    playedBeforeMs += t - playingSince
                    rebuffers++
                    countedAsRebuffer = true
                } else {
                    countedAsRebuffer = false
                }
                bufferingSince = t
            }
            Player.STATE_READY -> {
                if (lastState == Player.STATE_BUFFERING && countedAsRebuffer) stallMs += t - bufferingSince
                countedAsRebuffer = false
                started = true
                playingSince = t
            }
        }
        lastState = state
    }

    fun onDroppedFrames(count: Int) {
        dropped += count
    }

    fun onBandwidth(bitsPerSecond: Long) {
        if (bitsPerSecond > 0) bandwidthKbps = bitsPerSecond / BITS_PER_KBIT
    }

    /** Migawka z bieżącą długością bufora (podaje ją odtwarzacz); trwająca przerwa wlicza się do czasu przerw. */
    fun snapshot(bufferAheadMs: Long): PlaybackStats {
        val t = now()
        val ongoingStall = if (lastState == Player.STATE_BUFFERING && countedAsRebuffer) t - bufferingSince else 0L
        val playing = if (lastState == Player.STATE_READY && started) t - playingSince else 0L
        return PlaybackStats(
            bufferAheadMs = bufferAheadMs.coerceAtLeast(0),
            bandwidthKbps = bandwidthKbps,
            droppedFrames = dropped,
            rebuffers = rebuffers,
            stallMs = stallMs + ongoingStall,
            playedMs = playedBeforeMs + playing,
        )
    }

    private companion object {
        const val BITS_PER_KBIT = 1000L
    }
}
