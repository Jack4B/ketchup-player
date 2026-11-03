package pl.ketchupplayer.tv.player.engine

import androidx.media3.common.C
import androidx.media3.common.Player

/** Mapowanie stanów Media3 na model aplikacji (czysta funkcja, testowalna bez ExoPlayera). */
object PlayerStateMapper {
    fun status(
        playbackState: Int,
        hasError: Boolean,
    ): PlaybackStatus =
        when {
            hasError -> PlaybackStatus.ERROR
            playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            playbackState == Player.STATE_READY -> PlaybackStatus.READY
            playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
            else -> PlaybackStatus.IDLE
        }

    fun duration(durationMs: Long): Long? = durationMs.takeIf { it != C.TIME_UNSET && it > 0 }
}
