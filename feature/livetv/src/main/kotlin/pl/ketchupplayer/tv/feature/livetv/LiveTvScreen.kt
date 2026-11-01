package pl.ketchupplayer.tv.feature.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.ChannelRow
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.StateKind
import pl.ketchupplayer.tv.core.designsystem.StateView
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced

@Composable
fun LiveTvRoute(
    onOpenChannel: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onBack: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveTvScreen(state, viewModel::onEvent, onOpenChannel, onOpenProviders, onBack)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTvScreen(
    state: LiveTvUiState,
    onEvent: (LiveTvEvent) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    ScreenScaffold(scrollable = false) {
        ScreenTitle(stringResource(R.string.livetv_title))
        when {
            state.loading -> StateView(StateKind.LOADING, stringResource(R.string.livetv_loading))
            state.channels.isEmpty() && state.groups.isEmpty() ->
                StateView(
                    StateKind.EMPTY,
                    stringResource(R.string.livetv_empty),
                    actionLabel = stringResource(R.string.livetv_open_providers),
                    onAction = onOpenProviders,
                )
            else -> Content(state, onEvent, onOpenChannel)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Content(
    state: LiveTvUiState,
    onEvent: (LiveTvEvent) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val groupsState = rememberLazyListState()
    LaunchedEffect(Unit) {
        // Wybrana grupa może leżeć daleko na liście (np. 100+ grup); pozycja 0 to „Wszystkie”.
        val index = state.groups.indexOf(state.selectedGroup)
        if (index >= 0) groupsState.scrollToItem(index + 1)
    }
    Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyColumn(
            Modifier.width(GROUPS_WIDTH).fillMaxHeight(),
            groupsState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                GroupItem(
                    stringResource(R.string.livetv_all_groups),
                    state.selectedGroup == null && !state.favoritesSelected,
                ) { onEvent(LiveTvEvent.SelectGroup(null)) }
            }
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites") {
                    GroupItem(
                        stringResource(R.string.livetv_favorites, state.favorites.size),
                        state.favoritesSelected,
                    ) { onEvent(LiveTvEvent.SelectFavorites) }
                }
            }
            items(state.groups, key = { it }) { g ->
                GroupItem(
                    g.ifBlank {
                        stringResource(R.string.livetv_no_group)
                    },
                    state.selectedGroup == g,
                ) { onEvent(LiveTvEvent.SelectGroup(g)) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.channels, key = { it.id }) { ch ->
                ChannelRow(
                    number = ch.number ?: 0,
                    name = ch.displayName,
                    onClick = { onOpenChannel(ch.id) },
                    favorite = ch.id in state.favorites,
                    onLongClick = { onEvent(LiveTvEvent.ToggleFavorite(ch.id)) },
                    nowPlaying = state.onAir[ch.id]?.title,
                    progress = state.onAir[ch.id]?.progressAt(state.nowMs),
                    modifier = if (ch == state.channels.first()) Modifier.requestFocusWhenPlaced() else Modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FocusItem(onClick, Modifier.fillMaxWidth(), selected = selected) {
        Text(
            text,
            fontSize = 18.sp,
            color = KetchupTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

private val GROUPS_WIDTH = 280.dp
