package pl.ketchupplayer.tv.feature.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.ketchupplayer.tv.core.designsystem.InfoPill
import pl.ketchupplayer.tv.core.model.AccountInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Konto u dostawcy w jednej linii: ile połączeń jest w użyciu i ile wolno, do kiedy konto jest ważne
 * (dane z panelu Xtream; pola, których panel nie podaje, są pomijane).
 */
@Composable
internal fun AccountLine(
    info: AccountInfo,
    nowMs: Long,
) {
    val parts = mutableListOf<String>()
    val fullText = stringResource(R.string.providers_account_full)
    val active = info.activeConnections
    val max = info.maxConnections
    when {
        active != null && max != null && max > 0 ->
            parts += stringResource(R.string.providers_account_connections, active, max)
        active != null -> parts += stringResource(R.string.providers_account_in_use, active)
        max != null && max > 0 -> parts += stringResource(R.string.providers_account_limit, max)
    }
    if (info.connectionsFull) parts += fullText
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
    // Makieta v5: każdy fakt w osobnej pigułce; „wszystkie zajęte” w kolorze akcentu.
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        parts.forEach { InfoPill(it, highlight = info.connectionsFull && it == fullText) }
    }
}

private const val MS_PER_SEC = 1000L
