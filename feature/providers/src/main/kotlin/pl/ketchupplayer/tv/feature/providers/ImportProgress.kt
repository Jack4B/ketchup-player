package pl.ketchupplayer.tv.feature.providers

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.ketchupplayer.tv.core.data.catalog.ImportPhase
import pl.ketchupplayer.tv.core.data.catalog.ImportStatus
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme

private const val TICK_MS = 1_000L
private const val SECONDS_PER_MINUTE = 60

/** Etap importu z licznikiem czasu: przy dużej liście użytkownik widzi, że aplikacja pracuje. */
@Composable
internal fun ImportProgress(
    status: ImportStatus?,
    fontSizeSp: Int = 18,
) {
    val c = KetchupTheme.colors
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(status != null) {
        seconds = 0
        while (status != null) {
            delay(TICK_MS)
            seconds++
        }
    }
    val time = "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    val line =
        when (status?.phase) {
            ImportPhase.SAVING_CHANNELS -> stringResource(R.string.import_saving_channels, time, status.count)
            ImportPhase.SAVING_VOD -> stringResource(R.string.import_saving_vod, time, status.count)
            else -> stringResource(R.string.import_downloading, time)
        }
    Column {
        Text(line, fontSize = fontSizeSp.sp, color = c.textSecondary)
        Text(stringResource(R.string.import_hint), fontSize = (fontSizeSp - 2).sp, color = c.textSecondary)
    }
}
