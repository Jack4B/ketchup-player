package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/** Zegar UI dla [ClockText] i zegara odtwarzacza; domyślnie czas urządzenia ([UiClock.System]). */
val LocalUiClock = staticCompositionLocalOf { UiClock.System }
