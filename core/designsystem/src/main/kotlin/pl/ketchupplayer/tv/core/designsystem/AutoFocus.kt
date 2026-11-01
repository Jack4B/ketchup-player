package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Ustawia fokus na elemencie dokładnie raz, gdy jest już ułożony na ekranie ([enabled] = true).
 * Na stronie osadzonej w panelu ([LocalBreadcrumb], np. opcje w Ustawieniach) nic nie robi: fokus ma zostać na liście
 * po lewej, a do opcji wchodzi się strzałką w prawo.
 * Bezpieczne dla leniwych list: requester jest na pewno podpięty, więc nie ma `FocusRequester is not initialized`.
 */
fun Modifier.requestFocusWhenPlaced(
    enabled: Boolean = true,
    /** true: fokus także na stronie osadzonej w panelu (po akcji użytkownika, np. otwarciu formularza). */
    force: Boolean = false,
): Modifier =
    composed {
        val requester = remember { FocusRequester() }
        var done by remember { mutableStateOf(false) }
        if (!enabled || (!force && LocalBreadcrumb.current != null)) {
            this
        } else {
            this.focusRequester(requester).onGloballyPositioned {
                if (!done) {
                    done = true
                    requester.requestFocus()
                }
            }
        }
    }

/**
 * Początkowy fokus ekranu przez [requester] (po zmianie [key] ponownie). Na stronie osadzonej w panelu
 * ([LocalBreadcrumb]) nic nie robi: fokus zostaje na liście kategorii po lewej.
 */
@androidx.compose.runtime.Composable
fun InitialFocusEffect(
    requester: FocusRequester,
    key: Any? = Unit,
    enabled: Boolean = true,
) {
    val embedded = LocalBreadcrumb.current != null
    androidx.compose.runtime.LaunchedEffect(key, enabled, embedded) {
        if (enabled && !embedded) requester.requestFocus()
    }
}
