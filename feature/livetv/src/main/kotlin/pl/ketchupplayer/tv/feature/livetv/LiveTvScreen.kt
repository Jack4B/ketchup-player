package pl.ketchupplayer.tv.feature.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

/**
 * Układ jak w TiViMate: u góry podgląd i opis audycji, pod spodem szeroka lista kanałów. Grupy to wysuwany panel
 * z lewej (strzałka w lewo albo przycisk „Grupa”), który nachodzi na listę i przyciemnia ją; OK na grupie zamyka panel.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Content(
    state: LiveTvUiState,
    onEvent: (LiveTvEvent) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    var groupsOpen by rememberSaveable { mutableStateOf(false) }
    val channelsFocus = remember { FocusRequester() }
    val groupsState = rememberLazyListState()
    val selectedGroupFocus = remember { FocusRequester() }
    // Pojawienie się „Ulubione”/„Ostatnie” na górze listy nie może jej przewinąć (LazyColumn zachowuje pozycję).
    val virtualCount = (if (state.favorites.isNotEmpty()) 1 else 0) + (if (state.recentCount > 0) 1 else 0)
    LaunchedEffect(virtualCount) { if (state.selectedGroup == null) groupsState.scrollToItem(0) }
    // Po otwarciu panelu: przewiń do wybranej grupy (może leżeć daleko) i daj jej fokus.
    LaunchedEffect(groupsOpen) {
        if (groupsOpen) {
            val index = state.groups.indexOf(state.selectedGroup)
            if (index >= 0) groupsState.scrollToItem(index + VIRTUAL_GROUPS)
            androidx.compose.runtime.withFrameNanos { }
            runCatching { selectedGroupFocus.requestFocus() }
        }
    }
    BackHandler(enabled = groupsOpen) {
        groupsOpen = false
        runCatching { channelsFocus.requestFocus() }
    }
    val closeGroups: () -> Unit = {
        groupsOpen = false
        runCatching { channelsFocus.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailsPanel(state, { groupsOpen = true }, Modifier.fillMaxWidth().height(DETAILS_HEIGHT))
            ChannelsPanel(
                state,
                onEvent,
                onOpenChannel,
                channelsFocus,
                onOpenGroups = { groupsOpen = true },
                Modifier.weight(1f).fillMaxWidth(),
            )
        }
        AnimatedVisibility(groupsOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)))
        }
        AnimatedVisibility(
            groupsOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            GroupsPanel(
                state,
                onEvent,
                groupsState,
                selectedGroupFocus,
                closeGroups,
                Modifier.width(GROUPS_WIDTH).fillMaxHeight(),
            )
        }
    }
}

private const val VIRTUAL_GROUPS = 3
private const val SCRIM_ALPHA = 0.5f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupChip(
    state: LiveTvUiState,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    FocusItem(onClick, Modifier, outlined = true) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.livetv_groups) + ":", fontSize = 14.sp, color = c.textSecondary)
            Text(
                groupTitle(state),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(CHIP_TEXT_WIDTH),
            )
            Text("◀", fontSize = 12.sp, color = c.textSecondary)
        }
    }
}

@Composable
private fun groupTitle(state: LiveTvUiState): String =
    when {
        state.favoritesSelected -> stringResource(R.string.livetv_favorites)
        state.recentSelected -> stringResource(R.string.livetv_recent)
        state.selectedGroup != null -> state.selectedGroup.ifBlank { stringResource(R.string.livetv_no_group) }
        else -> stringResource(R.string.livetv_all_groups)
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupsPanel(
    state: LiveTvUiState,
    onEvent: (LiveTvEvent) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedFocus: FocusRequester,
    onClosed: () -> Unit,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val pick: (LiveTvEvent) -> Unit = {
        onEvent(it)
        onClosed()
    }
    val allSelected = state.selectedGroup == null && !state.favoritesSelected && !state.recentSelected
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.livetv_groups), fontSize = 15.sp, color = c.textSecondary)
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites") {
                    GroupItem(
                        stringResource(R.string.livetv_favorites),
                        state.favorites.size,
                        state.favoritesSelected,
                        if (state.favoritesSelected) Modifier.focusRequester(selectedFocus) else Modifier,
                    ) { pick(LiveTvEvent.SelectFavorites) }
                }
            }
            if (state.recentCount > 0) {
                item(key = "recent") {
                    GroupItem(
                        stringResource(R.string.livetv_recent),
                        state.recentCount,
                        state.recentSelected,
                        if (state.recentSelected) Modifier.focusRequester(selectedFocus) else Modifier,
                    ) { pick(LiveTvEvent.SelectRecent) }
                }
            }
            item(key = "all") {
                GroupItem(
                    stringResource(R.string.livetv_all_groups),
                    state.totalCount,
                    allSelected,
                    if (allSelected) Modifier.focusRequester(selectedFocus) else Modifier,
                ) { pick(LiveTvEvent.SelectGroup(null)) }
            }
            items(state.groups, key = { it }) { g ->
                val selected = state.selectedGroup == g
                GroupItem(
                    g.ifBlank { stringResource(R.string.livetv_no_group) },
                    state.groupCounts[g] ?: 0,
                    selected,
                    if (selected) Modifier.focusRequester(selectedFocus) else Modifier,
                ) { pick(LiveTvEvent.SelectGroup(g)) }
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
    firstChannelFocus: FocusRequester,
    onOpenGroups: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .panel()
            .padding(10.dp)
            .onPreviewKeyEvent {
                // Strzałka w lewo na liście kanałów wysuwa panel grup (dopiero kolejna idzie do szyny nawigacji).
                if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                    onOpenGroups()
                    true
                } else {
                    false
                }
            },
    ) {
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
                    compact = true,
                    modifier =
                        Modifier
                            .onFocusChanged { if (it.hasFocus) onEvent(LiveTvEvent.FocusChannel(ch.id)) }
                            .then(
                                if (ch == state.channels.first()) {
                                    Modifier.focusRequester(firstChannelFocus).requestFocusWhenPlaced()
                                } else {
                                    Modifier
                                },
                            ),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailsPanel(
    state: LiveTvUiState,
    onOpenGroups: () -> Unit,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val d = state.details
    Row(modifier.panel().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (d == null) {
            Spacer(Modifier.weight(1f))
            GroupChip(state, onOpenGroups)
            return@Row
        }
        Box(
            Modifier.width(DETAILS_ART_WIDTH).fillMaxHeight().background(
                c.channelTile,
                RoundedCornerShape(c.cornerRadius / 2),
            ),
        ) {
            Text(
                d.channel.displayName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = c.channelTileText,
                maxLines = 2,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
            Text(
                stringResource(R.string.livetv_live_badge),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = c.onAccent,
                modifier =
                    Modifier
                        .padding(8.dp)
                        .background(c.accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                d.now?.title ?: d.channel.displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val range = d.now?.let { " · ${clock(it.startMs)}–${clock(it.endMs)}" }.orEmpty()
            val src =
                d.source
                    .takeIf { it.isNotEmpty() }
                    ?.let { " · $it" }
                    .orEmpty()
            Text(
                "${d.channel.number ?: ""} · ${d.channel.displayName}$range$src",
                fontSize = 14.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            d.now?.let { p ->
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.outline, CircleShape)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(p.progressAt(state.nowMs))
                            .background(c.accent, CircleShape),
                    )
                }
                p.description?.let {
                    Text(it, fontSize = 14.sp, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        GroupChip(state, onOpenGroups)
    }
}

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupItem(
    text: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        modifier.fillMaxWidth(),
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
                fontSize = 17.sp,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(count.toString(), fontSize = 13.sp, color = c.textSecondary)
        }
    }
}

private val GROUPS_WIDTH = 360.dp
private val DETAILS_HEIGHT = 104.dp
private val DETAILS_ART_WIDTH = 200.dp
private val CHIP_TEXT_WIDTH = 200.dp
