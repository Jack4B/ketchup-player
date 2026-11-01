package pl.ketchupplayer.tv.player.engine

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import pl.ketchupplayer.tv.core.model.StreamSpec

/** Implementacja na ExoPlayerze: OkHttp jako źródło danych (HTTP i HTTPS), HLS/TS/MP4 wykrywane po adresie i typie. */
@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlayerController(
    context: Context,
    private val client: OkHttpClient,
) : PlayerController {
    private val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val player: Player get() = exo

    init {
        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) = publish()

                override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

                override fun onPlayerError(error: PlaybackException) = publish(error.errorCode)
            },
        )
    }

    override fun play(spec: StreamSpec) {
        val factory = OkHttpDataSource.Factory(client).apply { spec.userAgent?.let(::setUserAgent) }
        val sources = DefaultMediaSourceFactory(factory).setLoadErrorHandlingPolicy(FailFastLoadErrorHandlingPolicy())
        exo.setMediaSource(sources.createMediaSource(MediaItem.fromUri(spec.url)))
        exo.prepare()
        exo.playWhenReady = true
        publish()
    }

    override fun pause() {
        exo.pause()
    }

    override fun resume() {
        exo.play()
    }

    override fun stop() {
        exo.stop()
        publish()
    }

    override fun release() {
        exo.release()
    }

    private fun publish(errorCode: Int? = null) {
        val error = errorCode ?: exo.playerError?.errorCode
        _state.value =
            PlayerState(
                status = PlayerStateMapper.status(exo.playbackState, error != null),
                isPlaying = exo.isPlaying,
                errorCode = error,
                positionMs = exo.currentPosition,
                durationMs = PlayerStateMapper.duration(exo.duration),
            )
    }
}
