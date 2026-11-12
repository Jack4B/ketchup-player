package pl.ketchupplayer.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import pl.ketchupplayer.tv.core.designsystem.GlassProbe

/** Zwłoka przed pomiarem: aplikacja ma się uspokoić, a zapisane ustawienia wczytać (wcześniej „decided” bywa false). */
internal const val PROBE_DELAY_MS = 6_000L

/**
 * Pierwszy start: po [PROBE_DELAY_MS] mierzy płynność z rozmyciem i przekazuje werdykt, o ile rozmycie nie zostało
 * jeszcze ustalone ([decided]). Ręczny wybór w Ustawieniach (Wygląd) zawsze ma pierwszeństwo, bo też je ustala.
 */
@Composable
internal fun AutoGlassProbe(
    decided: Boolean,
    onVerdict: (Boolean) -> Unit,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(decided) {
        armed = false
        if (!decided) {
            delay(PROBE_DELAY_MS)
            armed = true
        }
    }
    if (armed && !decided) GlassProbe(onVerdict)
}
