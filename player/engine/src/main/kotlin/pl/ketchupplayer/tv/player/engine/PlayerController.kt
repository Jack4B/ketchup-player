package pl.ketchupplayer.tv.player.engine

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow
import pl.ketchupplayer.tv.core.model.StreamSpec

enum class PlaybackStatus { IDLE, BUFFERING, READY, ENDED, ERROR }

data class PlayerState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val errorCode: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
)

/**
 * Kontroler odtwarzania (KONSPEKT §3.4). Wszystkie metody wołać z wątku głównego (wątek aplikacji ExoPlayera).
 * [player] służy wyłącznie do podpięcia powierzchni wideo w UI.
 */
interface PlayerController {
    val state: StateFlow<PlayerState>
    val player: Player?

    fun play(spec: StreamSpec)

    fun pause()

    fun resume()

    fun stop()

    fun release()
}
