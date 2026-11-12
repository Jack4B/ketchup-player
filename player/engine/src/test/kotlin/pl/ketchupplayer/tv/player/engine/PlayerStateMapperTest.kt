package pl.ketchupplayer.tv.player.engine

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerStateMapperTest {
    @Test
    fun `mapowanie stanow Media3`() {
        assertEquals(PlaybackStatus.IDLE, PlayerStateMapper.status(Player.STATE_IDLE, false))
        assertEquals(PlaybackStatus.BUFFERING, PlayerStateMapper.status(Player.STATE_BUFFERING, false))
        assertEquals(PlaybackStatus.READY, PlayerStateMapper.status(Player.STATE_READY, false))
        assertEquals(PlaybackStatus.ENDED, PlayerStateMapper.status(Player.STATE_ENDED, false))
    }

    @Test
    fun `blad ma pierwszenstwo`() {
        assertEquals(PlaybackStatus.ERROR, PlayerStateMapper.status(Player.STATE_READY, true))
    }

    @Test
    fun `czas trwania live jest nieznany`() {
        assertNull(PlayerStateMapper.duration(C.TIME_UNSET))
        assertNull(PlayerStateMapper.duration(0))
        assertEquals(5000L, PlayerStateMapper.duration(5000))
    }
}
