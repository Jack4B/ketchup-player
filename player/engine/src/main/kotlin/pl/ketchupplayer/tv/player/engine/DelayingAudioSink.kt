package pl.ketchupplayer.tv.player.engine

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Przesunięcie dźwięku względem obrazu: znaczniki czasu przekazywane do wyjścia audio są przesunięte o [delayUs].
 * Zegar odtwarzania idzie za dźwiękiem, więc obraz pojawia się odpowiednio wcześniej lub później.
 * Dodatnie opóźnienie = dźwięk słychać później niż obraz.
 */
@UnstableApi
class DelayingAudioSink(
    sink: AudioSink,
) : ForwardingAudioSink(sink) {
    @Volatile
    var delayUs: Long = 0

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean = super.handleBuffer(buffer, presentationTimeUs + delayUs, encodedAccessUnitCount)
}

/** Fabryka rendererów z wyjściem audio, któremu można przesunąć dźwięk w trakcie odtwarzania. */
@UnstableApi
class DelayableRenderersFactory(
    context: Context,
) : DefaultRenderersFactory(context) {
    @Volatile
    var sink: DelayingAudioSink? = null
        private set

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink? {
        val base = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams) ?: return null
        return DelayingAudioSink(base).also { sink = it }
    }
}
