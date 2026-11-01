package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.data.groups.GroupKind
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.designsystem.TabChip
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced

@Composable
fun GroupsSettingsRoute(
    onBack: () -> Unit,
    viewModel: GroupsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GroupsSettingsScreen(state, viewModel::selectKind, viewModel::toggle, viewModel::setAll, onBack)
}

/**
 * Wybór grup i kategorii: osobno dla kanałów, filmów i seriali. Wyłączona grupa znika z list;
 * OK przełącza, „Pokaż wszystkie” / „Ukryj wszystkie” zmienia całość (potem włączasz tylko to, co oglądasz).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GroupsSettingsScreen(
    state: GroupsUiState,
    onKind: (GroupKind) -> Unit,
    onToggle: (String) -> Unit,
    onAll: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    ScreenScaffold(scrollable = false) {
        ScreenTitle(stringResource(R.string.groups_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(
                stringResource(R.string.groups_tv),
                state.kind == GroupKind.TV,
                { onKind(GroupKind.TV) },
                Modifier.requestFocusWhenPlaced(),
            )
            TabChip(
                stringResource(R.string.groups_movies),
                state.kind == GroupKind.MOVIES,
                { onKind(GroupKind.MOVIES) },
            )
            TabChip(
                stringResource(R.string.groups_series),
                state.kind == GroupKind.SERIES,
                { onKind(GroupKind.SERIES) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KetchupButton(stringResource(R.string.groups_show_all), { onAll(true) })
            KetchupButton(stringResource(R.string.groups_hide_all), { onAll(false) })
        }
        if (state.options.isEmpty()) {
            Text(stringResource(R.string.groups_empty), color = KetchupTheme.colors.textSecondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.options, key = { it.name }) { option ->
                    SettingsListItem(
                        title = option.name,
                        subtitle = stringResource(R.string.groups_count, option.count),
                        onClick = { onToggle(option.name) },
                        trailing = { Switch(checked = option.visible, onCheckedChange = null) },
                    )
                }
            }
        }
    }
}
