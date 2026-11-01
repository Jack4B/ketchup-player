package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced

@Composable
fun NetworkSettingsRoute(
    onBack: () -> Unit,
    viewModel: NetworkSettingsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    NetworkSettingsScreen(rows, viewModel::cycle, onBack)
}

/**
 * Nagłówek połączenia dla każdej listy. „Automatyczny” sam próbuje kolejnych przy błędzie (np. 404/403) i zapamiętuje
 * działający; OK przełącza po gotowej puli (VLC, TiviMate, IPTV Smarters, Kodi, Chrome), bez wpisywania tekstu.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    rows: List<ProviderHeaderRow>,
    onCycle: (ProviderHeaderRow) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = KetchupTheme.colors
    ScreenScaffold(scrollable = false) {
        ScreenTitle(stringResource(R.string.network_title))
        Text(stringResource(R.string.network_hint), color = c.textSecondary)
        if (rows.isEmpty()) {
            Text(stringResource(R.string.network_empty), color = c.textSecondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(rows, key = { _, r -> r.providerId }) { i, row ->
                    SettingsListItem(
                        title = row.name,
                        subtitle = row.profile.value ?: stringResource(R.string.network_auto_hint),
                        onClick = { onCycle(row) },
                        modifier = if (i == 0) Modifier.requestFocusWhenPlaced() else Modifier,
                        trailing = { Text(row.profile.label, color = c.textPrimary) },
                    )
                }
            }
        }
    }
}
