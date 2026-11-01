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

    /** Przewija do pozycji (filmy i odcinki; kanały na żywo ignorują). */
    fun seekTo(positionMs: Long) {}

    /** Odświeża [state] (pozycja płynie bez zdarzeń odtwarzacza); wołane okresowo przez ekran VOD. */
    fun poll() {}

    /** Ścieżki audio/napisów, tempo, przesunięcia, informacje o strumieniu (OSD). Domyślnie puste. */
    val tracks: StateFlow<TrackInfo> get() = NO_TRACKS

    /** Tekst napisów do pokazania teraz (już z uwzględnieniem opóźnienia napisów); pusty = nic. */
    val subtitle: StateFlow<String> get() = NO_SUBTITLE

    fun selectAudio(id: String) {}

    /** `null` wyłącza napisy. */
    fun selectText(id: String?) {}

    fun setSpeed(speed: Float) {}

    fun setAudioDelayMs(ms: Int) {}

    fun setSubtitleDelayMs(ms: Int) {}

    fun release()
}
