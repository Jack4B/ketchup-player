package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * Element fokusowalny D-padem i dotykiem: OK/Enter lub tap wywołuje [onClick].
 * Fokus: tło surface3 + obrys 3 dp w kolorze akcentu (dwa sygnały naraz, nie tylko kolor).
 */
@Composable
fun FocusItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val c = KetchupTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(c.cornerRadius)
    val ring =
        when {
            focused -> c.accent
            selected -> c.textSecondary
            else -> c.outline
        }
    Box(
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .background(if (focused) c.surface3 else c.surface2, shape)
                .border(if (focused) 3.dp else 1.dp, ring, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        content(focused)
    }
}
