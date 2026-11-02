package pl.ketchupplayer.tv.feature.providers

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.AccountInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Konto u dostawcy w jednej linii: ile połączeń jest w użyciu i ile wolno, do kiedy konto jest ważne
 * (dane z panelu Xtream; pola, których panel nie podaje, są pomijane).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AccountLine(
    info: AccountInfo,
    nowMs: Long,
) {
    val c = KetchupTheme.colors
    val parts = mutableListOf<String>()
    val active = info.activeConnections
    val max = info.maxConnections
    when {
        active != null && max != null && max > 0 ->
            parts += stringResource(R.string.providers_account_connections, active, max)
        active != null -> parts += stringResource(R.string.providers_account_in_use, active)
        max != null && max > 0 -> parts += stringResource(R.string.providers_account_limit, max)
    }
    if (info.connectionsFull) parts += stringResource(R.string.providers_account_full)
    info.expiresEpochSec?.let { sec ->
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(sec * MS_PER_SEC))
        val expired = sec * MS_PER_SEC < nowMs
        parts +=
            stringResource(
                if (expired) R.string.providers_account_expired else R.string.providers_account_expires,
                date,
            )
    }
    if (info.trial) parts += stringResource(R.string.providers_account_trial)
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        fontSize = 15.sp,
        color = if (info.connectionsFull) ERROR_COLOR else c.textSecondary,
        modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
    )
}

private const val MS_PER_SEC = 1000L
