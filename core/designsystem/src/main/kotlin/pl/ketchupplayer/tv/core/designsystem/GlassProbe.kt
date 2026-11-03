package pl.ketchupplayer.tv.core.designsystem

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pomiar płynności z rozmyciem: przez ~2 s renderuje animowany, rozmyty obszar (prawie niewidoczny) i mierzy czas
 * klatek, po czym woła [onVerdict]. Poniżej Androida 12 rozmycia nie ma, więc od razu „nie”.
 */
@Composable
fun GlassProbe(
    onVerdict: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest by rememberUpdatedState(onVerdict)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(Unit) { latest(false) }
        return
    }
    val phase by
        rememberInfiniteTransition(label = "probe").animateFloat(
            0f,
            1f,
            infiniteRepeatable(tween(PROBE_CYCLE_MS, easing = LinearEasing), RepeatMode.Reverse),
            label = "phase",
        )
    Box(
        modifier
            .size(PROBE_WIDTH, PROBE_HEIGHT)
            .alpha(PROBE_ALPHA)
            .blur(PROBE_BLUR)
            .background(
                Brush.linearGradient(
                    listOf(Color.White, Color.DarkGray, Color.Black),
                    Offset(phase * PROBE_SHIFT, 0f),
                    Offset(PROBE_SHIFT, phase * PROBE_SHIFT),
                ),
            ),
    )
    LaunchedEffect(Unit) {
        val deltas = ArrayList<Long>(PROBE_FRAMES)
        var last = withFrameNanos { it }
        val start = last
        while (deltas.size < PROBE_FRAMES && last - start < PROBE_TIMEOUT_NS) {
            val now = withFrameNanos { it }
            deltas += (now - last) / NS_PER_MS
            last = now
        }
        latest(GlassVerdict.decide(deltas))
    }
}

private const val PROBE_CYCLE_MS = 700
private val PROBE_WIDTH = 640.dp
private val PROBE_HEIGHT = 360.dp
private val PROBE_BLUR = 18.dp
private const val PROBE_ALPHA = 0.02f
private const val PROBE_SHIFT = 900f
private const val PROBE_FRAMES = 90
private const val PROBE_TIMEOUT_NS = 3_000_000_000L
private const val NS_PER_MS = 1_000_000L
