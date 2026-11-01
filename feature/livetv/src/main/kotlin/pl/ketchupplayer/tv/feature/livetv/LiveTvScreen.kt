package pl.ketchupplayer.tv.feature.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import pl.ketchupplayer.tv.core.designsystem.panel
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    // Pojawienie się „Ulubione”/„Ostatnie” na górze listy nie może jej przewinąć (LazyColumn zachowuje pozycję).
    val virtualCount = (if (state.favorites.isNotEmpty()) 1 else 0) + (if (state.recentCount > 0) 1 else 0)
    LaunchedEffect(virtualCount) { if (state.selectedGroup == null) groupsState.scrollToItem(0) }
    LaunchedEffect(Unit) {
        // Wybrana grupa może leżeć daleko na liście (np. 100+ grup); przed nią są pozycje wirtualne.
        val index = state.groups.indexOf(state.selectedGroup)
        if (index >= 0) groupsState.scrollToItem(index + VIRTUAL_GROUPS)
    }
    Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        GroupsPanel(state, onEvent, groupsState, Modifier.width(GROUPS_WIDTH).fillMaxHeight())
        ChannelsPanel(state, onEvent, onOpenChannel, Modifier.weight(1f).fillMaxHeight())
        DetailsPanel(state, Modifier.width(DETAILS_WIDTH).fillMaxHeight())
    }
}

private const val VIRTUAL_GROUPS = 3

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupsPanel(
    state: LiveTvUiState,
    onEvent: (LiveTvEvent) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    Column(modifier.panel().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.livetv_groups), fontSize = 14.sp, color = c.textSecondary)
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites") {
                    GroupItem(
                        stringResource(R.string.livetv_favorites),
                        state.favorites.size,
                        state.favoritesSelected,
                    ) {
                        onEvent(LiveTvEvent.SelectFavorites)
                    }
                }
            }
            if (state.recentCount > 0) {
                item(key = "recent") {
                    GroupItem(stringResource(R.string.livetv_recent), state.recentCount, state.recentSelected) {
                        onEvent(LiveTvEvent.SelectRecent)
                    }
                }
            }
            item(key = "all") {
                GroupItem(
                    stringResource(R.string.livetv_all_groups),
                    state.totalCount,
                    state.selectedGroup == null && !state.favoritesSelected && !state.recentSelected,
                ) { onEvent(LiveTvEvent.SelectGroup(null)) }
            }
            items(state.groups, key = { it }) { g ->
                GroupItem(
                    g.ifBlank { stringResource(R.string.livetv_no_group) },
                    state.groupCounts[g] ?: 0,
                    state.selectedGroup == g,
                ) { onEvent(LiveTvEvent.SelectGroup(g)) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelsPanel(
    state: LiveTvUiState,
    onEvent: (LiveTvEvent) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val title =
        when {
            state.favoritesSelected -> stringResource(R.string.livetv_favorites)
            state.recentSelected -> stringResource(R.string.livetv_recent)
            state.selectedGroup != null -> state.selectedGroup.ifBlank { stringResource(R.string.livetv_no_group) }
            else -> stringResource(R.string.livetv_all_groups)
        }
    Column(modifier.panel().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.livetv_channels, title), fontSize = 14.sp, color = c.textSecondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.channels, key = { it.id }) { ch ->
                val programme = state.onAir[ch.id]
                ChannelRow(
                    number = ch.number ?: 0,
                    name = ch.displayName,
                    onClick = { onOpenChannel(ch.id) },
                    favorite = ch.id in state.favorites,
                    onLongClick = { onEvent(LiveTvEvent.ToggleFavorite(ch.id)) },
                    nowPlaying = programme?.title,
                    progress = programme?.progressAt(state.nowMs),
                    timeRange = programme?.let { "${clock(it.startMs)}–${clock(it.endMs)}" },
                    modifier =
                        Modifier
                            .onFocusChanged { if (it.hasFocus) onEvent(LiveTvEvent.FocusChannel(ch.id)) }
                            .then(if (ch == state.channels.first()) Modifier.requestFocusWhenPlaced() else Modifier),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailsPanel(
    state: LiveTvUiState,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val d = state.details
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (d == null) return@Column
        Box(
            Modifier.fillMaxWidth().height(DETAILS_ART_HEIGHT).background(
                c.channelTile,
                RoundedCornerShape(
                    c.cornerRadius / 2,
                ),
            ),
        ) {
            Text(
                d.channel.displayName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = c.channelTileText,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                stringResource(R.string.livetv_live_badge),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = c.onAccent,
                modifier =
                    Modifier
                        .padding(
                            8.dp,
                        ).background(c.accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(
            d.now?.title ?: d.channel.displayName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
            maxLines = 2,
        )
        val range = d.now?.let { " · ${clock(it.startMs)}–${clock(it.endMs)}" }.orEmpty()
        Text(
            "${d.channel.number ?: ""} · ${d.channel.displayName}$range",
            fontSize = 13.sp,
            color = c.textSecondary,
            maxLines = 1,
        )
        d.now?.let { p ->
            Box(Modifier.fillMaxWidth().height(3.dp).background(c.outline, CircleShape)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p.progressAt(state.nowMs)).background(c.accent, CircleShape))
            }
            p.description?.let { Text(it, fontSize = 14.sp, color = c.textPrimary, maxLines = 4) }
        }
        d.next?.let {
            Text(
                stringResource(R.string.livetv_next, clock(it.startMs), it.title),
                fontSize = 14.sp,
                color = c.textSecondary,
                maxLines = 2,
            )
        }
        if (d.source.isNotEmpty()) {
            Text(
                stringResource(R.string.livetv_source, d.source),
                fontSize = 13.sp,
                color = c.textSecondary,
                maxLines = 1,
            )
        }
    }
}

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupItem(
    text: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        Modifier.fillMaxWidth(),
        selected = selected,
        container = if (selected) c.surface3 else Color.Transparent,
        outlined = false,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text,
                fontSize = 15.sp,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(count.toString(), fontSize = 13.sp, color = c.textSecondary)
        }
    }
}

private val GROUPS_WIDTH = 150.dp
private val DETAILS_WIDTH = 280.dp
private val DETAILS_ART_HEIGHT = 150.dp
