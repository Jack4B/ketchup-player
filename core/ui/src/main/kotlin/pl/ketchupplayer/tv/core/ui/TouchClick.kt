package pl.ketchupplayer.tv.core.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Obsługa dotyku dla komponentów `tv-material` (Button, ListItem, Surface), które reagują tylko na D-pad/klawisze.
 * D-pad + OK zostaje po stronie komponentu TV; ta modyfikacja dokłada wyłącznie tapnięcie palcem (telefon/tablet).
 */
fun Modifier.touchClickable(onClick: () -> Unit): Modifier =
    pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
