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
fun Modifier.requestFocusWhenPlaced(enabled: Boolean = true): Modifier =
    composed {
        val requester = remember { FocusRequester() }
        var done by remember { mutableStateOf(false) }
        if (!enabled || LocalBreadcrumb.current != null) {
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
