package pl.ketchupplayer.tv.player.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
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
    private val renderers = DelayableRenderersFactory(context.applicationContext)
    private val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext, renderers).build()
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()
    override val player: Player get() = exo
    private val _tracks = MutableStateFlow(TrackInfo())
    override val tracks: StateFlow<TrackInfo> = _tracks.asStateFlow()
    private val _subtitle = MutableStateFlow("")
    override val subtitle: StateFlow<String> = _subtitle.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private var subtitleDelayMs = 0
    private var audioDelayMs = 0

    init {
        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) = publish()

                override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

                override fun onPlayerError(error: PlaybackException) = publish(error.errorCode)

                override fun onTracksChanged(tracks: Tracks) = publishTracks()

                override fun onCues(cueGroup: CueGroup) = showCues(cueGroup)

                override fun onPlaybackParametersChanged(
                    playbackParameters: androidx.media3.common.PlaybackParameters,
                ) = publishTracks()
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

    override fun seekTo(positionMs: Long) {
        exo.seekTo(positionMs)
        publish()
    }

    override fun poll() = publish()

    override fun stop() {
        exo.stop()
        publish()
    }

    override fun release() {
        main.removeCallbacksAndMessages(null)
        exo.release()
    }

    override fun selectAudio(id: String) {
        val (group, index) = parse(id) ?: return
        val g = exo.currentTracks.groups.getOrNull(group) ?: return
        exo.trackSelectionParameters =
            exo.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, index))
                .build()
    }

    override fun selectText(id: String?) {
        val params = exo.trackSelectionParameters.buildUpon()
        val parsed = id?.let(::parse)
        val g = parsed?.let { exo.currentTracks.groups.getOrNull(it.first) }
        if (g == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            _subtitle.value = ""
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            params.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, parsed!!.second))
        }
        exo.trackSelectionParameters = params.build()
    }

    override fun setSpeed(speed: Float) {
        exo.setPlaybackSpeed(speed)
        publishTracks()
    }

    override fun setAudioDelayMs(ms: Int) {
        audioDelayMs = ms
        renderers.sink?.delayUs = ms * MICROS_PER_MILLI
        publishTracks()
    }

    override fun setSubtitleDelayMs(ms: Int) {
        subtitleDelayMs = ms.coerceAtLeast(0)
        publishTracks()
    }

    /** Opóźnienie napisów: tekst pokazujemy i chowamy z tym samym opóźnieniem (kolejność zdarzeń bez zmian). */
    private fun showCues(group: CueGroup) {
        val text = group.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
        if (subtitleDelayMs == 0) {
            _subtitle.value = text
        } else {
            main.postDelayed({ _subtitle.value = text }, subtitleDelayMs.toLong())
        }
    }

    private fun parse(id: String): Pair<Int, Int>? {
        val parts = id.split(':')
        val group = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return group to index
    }

    private fun publishTracks() {
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        exo.currentTracks.groups.forEachIndexed { g, group ->
            for (i in 0 until group.length) {
                val f = group.getTrackFormat(i)
                val id = "$g:$i"
                when (group.type) {
                    C.TRACK_TYPE_AUDIO ->
                        audio +=
                            TrackOption(
                                id,
                                TrackLabels.audio(f.language, f.label, f.channelCount, f.sampleMimeType, audio.size),
                                group.isTrackSelected(i),
                            )
                    C.TRACK_TYPE_TEXT ->
                        text +=
                            TrackOption(id, TrackLabels.text(f.language, f.label, text.size), group.isTrackSelected(i))
                }
            }
        }
        _tracks.value =
            TrackInfo(
                audio = audio,
                text = text,
                textEnabled = text.any { it.selected },
                speed = exo.playbackParameters.speed,
                audioDelayMs = audioDelayMs,
                subtitleDelayMs = subtitleDelayMs,
                summary = summary(),
            )
    }

    private fun summary(): StreamSummary {
        val v = exo.videoFormat
        val a = exo.audioFormat
        val video =
            v?.let {
                listOfNotNull(
                    if (it.width >
                        0
                    ) {
                        "${it.width}×${it.height}"
                    } else {
                        null
                    },
                    fps(it.frameRate),
                ).joinToString(" ")
            }
        val audio = a?.let { TrackLabels.codec(it.sampleMimeType) }
        val bitrate = (v?.bitrate?.takeIf { it > 0 } ?: a?.bitrate?.takeIf { it > 0 })?.div(BITS_PER_KBIT)
        return StreamSummary(video?.ifEmpty { null }, audio, bitrate)
    }

    private fun fps(rate: Float): String? = if (rate > 0) "%.0f kl/s".format(rate) else null

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

private const val MICROS_PER_MILLI = 1000L
private const val BITS_PER_KBIT = 1000
