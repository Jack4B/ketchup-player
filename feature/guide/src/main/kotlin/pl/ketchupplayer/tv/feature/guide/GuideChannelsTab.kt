package pl.ketchupplayer.tv.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.ChannelLogo
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.player.engine.PlaybackStatus

/**
 * Zakładka „Kanały” (makieta v4): grupy zawsze widoczne po lewej, lista kanałów z tym, co leci teraz, podgląd z opisem.
 * D-pad: GÓRA/DÓŁ kanał, LEWO na wybraną grupę (z grup do szyny), PRAWO z grup na listę, OK otwiera kanał.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ChannelsTab(
    state: GuideUiState,
    loadProgrammes: suspend (String) -> List<EpgProgramme>,
    actions: ChannelsActions,
    rows: FocusRequester,
    preview: PreviewUi,
) {
    var focusedId by remember { mutableStateOf<String?>(null) }
    val selectedGroupFocus = remember { FocusRequester() }
    val groupsList = rememberLazyListState()
    // Nowy wiersz „Ulubione”/„Ostatnie” pojawia się nad widocznym: bez przewinięcia lista zostaje na starym miejscu.
    val hasFavorites = state.favorites.isNotEmpty()
    LaunchedEffect(
        state.selectedGroup,
        state.favoritesSelected,
        state.recentSelected,
        hasFavorites,
        state.recentCount,
    ) {
        val index = state.groups.indexOf(state.selectedGroup)
        groupsList.scrollToItem(if (index >= 0) index + VIRTUAL_GROUPS else 0)
    }
    val current = state.channels.firstOrNull { it.id == focusedId } ?: state.channels.firstOrNull()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
        Box(
            Modifier.width(GROUPS_COLUMN).fillMaxHeight().onPreviewKeyEvent {
                val right = it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown
                right.also { hit -> if (hit) runCatching { rows.requestFocus() } }
            },
        ) {
            GuideGroupsPanel(state, groupsList, selectedGroupFocus, actions.onSelectGroup, Modifier.fillMaxSize())
        }
        LazyColumn(
            Modifier.weight(LIST_WEIGHT).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        ) {
            items(state.channels, key = { it.id }) { ch ->
                val first = ch.id == state.channels.first().id
                ChannelRow(
                    ch,
                    ch.id in state.favorites,
                    state,
                    loadProgrammes,
                    first,
                    Modifier
                        .then(if (first) Modifier.focusRequester(rows) else Modifier)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                focusedId = ch.id
                                actions.onPreviewChannel(ch.id)
                            }
                        }.onPreviewKeyEvent { e ->
                            val left = e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown
                            left.also { hit -> if (hit) runCatching { selectedGroupFocus.requestFocus() } }
                        },
                    onLongClick = { actions.onToggleFavorite(ch.id) },
                ) { actions.onOpenChannel(ch.id) }
            }
        }
        PreviewPane(current, state, loadProgrammes, preview, Modifier.weight(PREVIEW_WEIGHT).fillMaxHeight())
    }
}

/** Akcje zakładki zebrane razem (limit parametrów detekta). */
internal data class ChannelsActions(
    val onOpenChannel: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onSelectGroup: (GuideGroup) -> Unit,
    val onPreviewChannel: (String) -> Unit,
)

private val PANE_GAP = 14.dp
private val GROUPS_COLUMN = 210.dp
private val ROW_SPACING = 6.dp
private const val LIST_WEIGHT = 1.3f
private const val PREVIEW_WEIGHT = 1f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    channel: Channel,
    favorite: Boolean,
    state: GuideUiState,
    loadProgrammes: suspend (String) -> List<EpgProgramme>,
    initialFocus: Boolean,
    modifier: Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    val programmes by produceState<List<EpgProgramme>?>(null, channel.id, state.epgRevision) {
        value = loadProgrammes(channel.id)
    }
    val now = programmes?.firstOrNull { it.isOnAt(state.nowMs) }
    FocusItem(
        onClick,
        modifier
            .fillMaxWidth()
            .height(
                ROW_HEIGHT,
            ).then(if (initialFocus) Modifier.requestFocusWhenPlaced() else Modifier),
        onLongClick = onLongClick,
        container = c.surface2,
        outlined = false,
        focusScale = 1.02f,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                (channel.number ?: 0).toString(),
                fontSize = 14.sp,
                color = c.textSecondary,
                modifier = Modifier.width(NUMBER_WIDTH),
            )
            ChannelLogo(channel.displayName, channel.logo, Modifier.width(LOGO_WIDTH).height(LOGO_HEIGHT))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (favorite) "★ " else "") + channel.displayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (now != null) {
                    Text(
                        now.title,
                        fontSize = 14.sp,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (now != null) ProgressBar(now.progressAt(state.nowMs), Modifier.width(PROGRESS_WIDTH))
        }
    }
}

private val ROW_HEIGHT = 60.dp
private val NUMBER_WIDTH = 30.dp
private val LOGO_WIDTH = 52.dp
private val LOGO_HEIGHT = 36.dp
private val PROGRESS_WIDTH = 44.dp

@Composable
private fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    Box(modifier.height(4.dp).background(c.outline, RoundedCornerShape(2.dp))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(
                    fraction.coerceIn(0f, 1f),
                ).background(c.accent, RoundedCornerShape(2.dp)),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PreviewPane(
    channel: Channel?,
    state: GuideUiState,
    loadProgrammes: suspend (String) -> List<EpgProgramme>,
    preview: PreviewUi,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val programmes by produceState<List<EpgProgramme>?>(null, channel?.id, state.epgRevision) {
        value = channel?.let { loadProgrammes(it.id) }
    }
    val now = programmes?.firstOrNull { it.isOnAt(state.nowMs) }
    val upcoming = programmes.orEmpty().filter { it.startMs >= (now?.endMs ?: state.nowMs) }.take(UPCOMING)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PREVIEW_RATIO)
                .background(c.channelTile, RoundedCornerShape(c.cornerRadius / 2)),
            contentAlignment = Alignment.Center,
        ) {
            ChannelLogo(
                channel?.displayName.orEmpty(),
                channel?.logo,
                Modifier.fillMaxSize(),
                labelSize = 24.sp,
                fullName = true,
            )
            val live = preview.player
            if (live != null && preview.channelId == channel?.id) {
                androidx.media3.ui.compose
                    .PlayerSurface(live, Modifier.fillMaxSize())
            }
            val note =
                when {
                    preview.channelId != channel?.id -> null
                    preview.status == PlaybackStatus.ERROR -> stringResource(R.string.guide_preview_error)
                    preview.status == PlaybackStatus.BUFFERING -> stringResource(R.string.guide_preview_loading)
                    else -> null
                }
            note?.let {
                Text(
                    it,
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(channel?.displayName.orEmpty(), fontSize = 14.sp, color = c.textSecondary, maxLines = 1)
        Text(
            now?.title ?: channel?.displayName.orEmpty(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (now != null) {
            Text("${clock(now.startMs)}–${clock(now.endMs)}", fontSize = 15.sp, color = c.textSecondary, maxLines = 1)
            ProgressBar(now.progressAt(state.nowMs), Modifier.fillMaxWidth())
            now.description?.let {
                Text(it, fontSize = 15.sp, color = c.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        upcoming.forEach {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    clock(it.startMs),
                    fontSize = 14.sp,
                    color = c.textSecondary,
                    modifier = Modifier.width(UPCOMING_TIME),
                )
                Text(it.title, fontSize = 14.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val PREVIEW_RATIO = 16f / 9f
private const val UPCOMING = 3
private val UPCOMING_TIME = 48.dp
