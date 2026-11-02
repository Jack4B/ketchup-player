package pl.ketchupplayer.tv.player.engine

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStatsCollectorTest {
    private var t = 0L
    private val c = PlaybackStatsCollector { t }

    private fun at(ms: Long) {
        t = ms
    }

    @Test
    fun `pierwsze buforowanie po starcie nie jest przerwa`() {
        at(0)
        c.onPlaybackState(Player.STATE_BUFFERING)
        at(1_500)
        c.onPlaybackState(Player.STATE_READY)
        val s = c.snapshot(bufferAheadMs = 4_000)
        assertEquals(0, s.rebuffers)
        assertEquals(0L, s.stallMs)
    }

    @Test
    fun `buforowanie w trakcie grania to przerwa z czasem`() {
        at(0)
        c.onPlaybackState(Player.STATE_BUFFERING)
        at(1_000)
        c.onPlaybackState(Player.STATE_READY)
        at(11_000) // 10 s grania
        c.onPlaybackState(Player.STATE_BUFFERING)
        at(13_000) // 2 s przerwy
        c.onPlaybackState(Player.STATE_READY)
        at(23_000) // kolejne 10 s
        val s = c.snapshot(bufferAheadMs = 8_000)
        assertEquals(1, s.rebuffers)
        assertEquals(2_000L, s.stallMs)
        assertEquals(20_000L, s.playedMs)
        assertEquals(8_000L, s.bufferAheadMs)
    }

    @Test
    fun `trwajaca przerwa wlicza sie do czasu przerw w migawce`() {
        at(0)
        c.onPlaybackState(Player.STATE_READY)
        at(5_000)
        c.onPlaybackState(Player.STATE_BUFFERING)
        at(8_000)
        assertEquals(3_000L, c.snapshot(0).stallMs)
    }

    @Test
    fun `klatki i lacze sa zapamietywane a reset czysci sesje`() {
        c.onDroppedFrames(3)
        c.onDroppedFrames(2)
        assertNull(c.snapshot(0).bandwidthKbps)
        c.onBandwidth(18_500_000)
        val s = c.snapshot(0)
        assertEquals(5L, s.droppedFrames)
        assertEquals(18_500L, s.bandwidthKbps)
        c.reset()
        val cleared = c.snapshot(0)
        assertEquals(0L, cleared.droppedFrames)
        assertNull(cleared.bandwidthKbps)
    }

    @Test
    fun `ujemny bufor jest przycinany do zera`() {
        assertEquals(0L, c.snapshot(-500).bufferAheadMs)
    }
}
