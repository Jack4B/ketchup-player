package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Czas animacji fokusu (konspekt §4.1: ≤ 150 ms). */
const val FOCUS_ANIMATION_MS = 120

/**
 * Element fokusowalny D-padem i dotykiem: OK/Enter lub tap wywołuje [onClick].
 * Fokus daje kilka sygnałów naraz (konspekt §4.1): skala [focusScale], obrys 3 dp, rozjaśnione tło.
 * [container]/[focusedContainer]/[ringColor] pozwalają przebarwić element (np. przycisk główny w kolorze akcentu).
 */
@Composable
fun FocusItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    container: Color? = null,
    focusedContainer: Color? = null,
    ringColor: Color? = null,
    focusScale: Float = 1.04f,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val c = KetchupTheme.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, tween(FOCUS_ANIMATION_MS), label = "focusScale")
    val shape = RoundedCornerShape(c.cornerRadius)
    val ring =
        when {
            focused -> ringColor ?: c.accent
            selected -> c.textSecondary
            else -> c.outline
        }
    val fill = if (focused) focusedContainer ?: c.surface3 else container ?: c.surface2
    Box(
        modifier =
            modifier
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .background(fill, shape)
                .border(if (focused) 3.dp else 1.dp, ring, shape)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                // focusable() po clickable: clickable sam jest fokusowalny tylko w trybie klawiatury, a nie dotyku.
                .focusable(),
    ) {
        content(focused)
    }
}
