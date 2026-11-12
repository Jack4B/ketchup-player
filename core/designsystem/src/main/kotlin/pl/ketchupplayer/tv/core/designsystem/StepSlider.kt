package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Suwak sterowany D-padem (LEWO/PRAWO ± [step]) i dotykiem (tap/przeciągnięcie).
 * [value] w zakresie 0..1; dla innych zakresów skaluj po stronie wywołującego.
 */
@Composable
fun StepSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 0.05f,
) {
    val c = KetchupTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(c.cornerRadius.coerceAtMost(12.dp))
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(36.dp)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> {
                            onValueChange((value - step).coerceIn(0f, 1f))
                            true
                        }
                        Key.DirectionRight -> {
                            onValueChange((value + step).coerceIn(0f, 1f))
                            true
                        }
                        else -> false
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { o -> onValueChange((o.x / size.width).coerceIn(0f, 1f)) }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }.background(c.surface2, shape)
                .border(if (focused) 3.dp else 1.dp, if (focused) c.accent else c.outline, shape),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0.02f, 1f)).background(c.accent, shape))
    }
}
