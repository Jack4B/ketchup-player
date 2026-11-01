package pl.ketchupplayer.tv.feature.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.PillChip
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ShellHeader
import pl.ketchupplayer.tv.core.designsystem.StateKind
import pl.ketchupplayer.tv.core.designsystem.StateView
import pl.ketchupplayer.tv.core.designsystem.TabChip
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.epg.radar.RadarItem
import pl.ketchupplayer.tv.epg.radar.RadarSlot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GuideRoute(
    onOpenChannel: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onBack: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val radar by viewModel.radar.collectAsStateWithLifecycle()
    GuideScreen(state, viewModel::programmes, onOpenChannel, onOpenProviders, onBack, radar, viewModel::selectRadarSlot)
}

/** Zakładki ekranu Przewodnik (makieta 04): Siatka | Radar | Moje zainteresowania. */
enum class GuideTab { GRID, RADAR, INTERESTS }

/**
 * Przewodnik (makieta v3 04-epg): nagłówek z zakładkami, blok podglądu zaznaczonej audycji, oś czasu 12 h z linią
 * „teraz”, wiersze kanałów. Wszystkie wiersze przewijają się poziomo razem; audycje wiersza ładują się leniwie.
 * D-pad: GÓRA/DÓŁ kanał, LEWO/PRAWO audycja, OK otwiera kanał, BACK wraca do szyny.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GuideScreen(
    state: GuideUiState,
    loadProgrammes: suspend (channelId: String) -> List<EpgProgramme>,
    onOpenChannel: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onBack: () -> Unit,
    radar: RadarUiState = RadarUiState(),
    onRadarSlot: (RadarSlot) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(GuideTab.GRID) }
    ScreenScaffold(scrollable = false) {
        ShellHeader(stringResource(R.string.guide_title), tabs = { Tabs(tab) { tab = it } })
        when {
            state.loading -> StateView(StateKind.LOADING, "")
            state.channels.isEmpty() ->
                StateView(
                    StateKind.EMPTY,
                    stringResource(R.string.guide_empty),
                    actionLabel = stringResource(R.string.guide_open_providers),
                    onAction = onOpenProviders,
                )
            tab == GuideTab.GRID -> Grid(state, loadProgrammes, onOpenChannel)
            tab == GuideTab.RADAR -> RadarTab(radar, state.nowMs, onRadarSlot, onOpenChannel)
            else -> StateView(StateKind.EMPTY, stringResource(R.string.guide_tab_soon))
        }
    }
}

@Composable
private fun Tabs(
    selected: GuideTab,
    onSelect: (GuideTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TabChip(stringResource(R.string.guide_tab_grid), selected == GuideTab.GRID, { onSelect(GuideTab.GRID) })
        TabChip(stringResource(R.string.guide_tab_radar), selected == GuideTab.RADAR, { onSelect(GuideTab.RADAR) })
        TabChip(
            stringResource(R.string.guide_tab_interests),
            selected == GuideTab.INTERESTS,
            { onSelect(GuideTab.INTERESTS) },
        )
    }
}

private data class Focused(
    val channel: Channel,
    val programme: EpgProgramme?,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Grid(
    state: GuideUiState,
    loadProgrammes: suspend (String) -> List<EpgProgramme>,
    onOpenChannel: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    var focused by remember { mutableStateOf<Focused?>(null) }
    Preview(focused ?: state.channels.firstOrNull()?.let { Focused(it, null) }, state.nowMs)
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(CHANNEL_COLUMN), contentAlignment = Alignment.CenterStart) {
            Text(
                SimpleDateFormat("EEE d.MM", Locale.getDefault()).format(Date(state.windowStartMs)),
                fontSize = 13.sp,
                color = KetchupTheme.colors.textSecondary,
            )
        }
        Box(Modifier.weight(1f).horizontalScroll(scroll)) { TimeScale(state) }
    }
    LazyColumn(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        items(state.channels, key = { it.id }) { ch ->
            val first = ch.id == state.channels.first().id
            Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                ChannelCell(
                    ch,
                    ch.id in state.favorites,
                    first,
                    { focused = Focused(ch, null) },
                ) { onOpenChannel(ch.id) }
                val programmes by produceState<List<EpgProgramme>?>(null, ch.id, state.epgRevision) {
                    value = loadProgrammes(ch.id)
                }
                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                    Timeline(state, programmes, first, { focused = Focused(ch, it) }) { onOpenChannel(ch.id) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Preview(
    focused: Focused?,
    nowMs: Long,
) {
    val c = KetchupTheme.colors
    Row(Modifier.fillMaxWidth().height(PREVIEW_HEIGHT), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .width(
                    PREVIEW_ART,
                ).fillMaxHeight()
                .background(c.channelTile, RoundedCornerShape(c.cornerRadius / 2)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                focused?.channel?.displayName.orEmpty(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = c.channelTileText,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val p = focused?.programme
            val kicker =
                when {
                    p == null -> ""
                    p.isOnAt(nowMs) -> stringResource(R.string.guide_now, focused.channel.displayName.uppercase())
                    p.startMs > nowMs -> stringResource(R.string.guide_later, focused.channel.displayName.uppercase())
                    else -> focused.channel.displayName.uppercase()
                }
            Text(kicker, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.textSecondary)
            Text(
                p?.title ?: focused?.channel?.displayName.orEmpty(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 1,
            )
            if (p != null) {
                val meta = listOfNotNull("${clock(p.startMs)}–${clock(p.endMs)}", p.category).joinToString(" · ")
                Text(meta, fontSize = 14.sp, color = c.textSecondary, maxLines = 1)
                p.description?.let {
                    Text(
                        it,
                        fontSize = 14.sp,
                        color = c.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TimeScale(state: GuideUiState) {
    val c = KetchupTheme.colors
    Box(Modifier.width(GuideGeometry.totalWidthDp().dp).height(24.dp)) {
        val steps = GuideGeometry.WINDOW_MINUTES / GuideGeometry.STEP_MINUTES
        for (i in 0 until steps) {
            val minutes = i * GuideGeometry.STEP_MINUTES
            Row(
                Modifier.offset(x = (minutes * GuideGeometry.DP_PER_MINUTE).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(1.dp).height(14.dp).background(c.outline))
                Text(
                    clock(state.windowStartMs + minutes * MS_PER_MINUTE),
                    fontSize = 14.sp,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        NowMarker(state, Modifier.align(Alignment.BottomStart), 6.dp)
    }
}

@Composable
private fun NowMarker(
    state: GuideUiState,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
) {
    val offset = ((state.nowMs - state.windowStartMs) / MS_PER_MINUTE.toFloat() * GuideGeometry.DP_PER_MINUTE)
    if (offset >= 0) {
        Box(
            modifier
                .offset(x = offset.dp)
                .width(2.dp)
                .height(height)
                .background(KetchupTheme.colors.accent),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCell(
    channel: Channel,
    favorite: Boolean,
    initialFocus: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        Modifier
            .width(CHANNEL_COLUMN - 6.dp)
            .height(ROW_HEIGHT)
            .onFocusChanged { if (it.hasFocus) onFocus() }
            .then(if (initialFocus) Modifier.requestFocusWhenPlaced() else Modifier),
        container = c.surface2,
        outlined = false,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                (channel.number ?: 0).toString(),
                fontSize = 14.sp,
                color = c.textSecondary,
                modifier = Modifier.width(26.dp),
            )
            Text(
                (if (favorite) "★ " else "") + channel.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Timeline(
    state: GuideUiState,
    programmes: List<EpgProgramme>?,
    focusOnAir: Boolean,
    onFocus: (EpgProgramme) -> Unit,
    onOpen: () -> Unit,
) {
    val c = KetchupTheme.colors
    Box(Modifier.width(GuideGeometry.totalWidthDp().dp).height(ROW_HEIGHT)) {
        val placed =
            programmes.orEmpty().mapNotNull { p ->
                GuideGeometry.place(p.startMs, p.endMs, state.windowStartMs, state.windowEndMs)?.let { p to it }
            }
        if (programmes != null && placed.isEmpty()) {
            Text(
                stringResource(R.string.guide_no_data),
                fontSize = 14.sp,
                color = c.textSecondary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
            )
        }
        placed.forEach { (p, pos) ->
            val past = p.endMs <= state.nowMs
            val onAir = p.isOnAt(state.nowMs)
            FocusItem(
                onOpen,
                Modifier
                    .offset(x = pos.first.dp)
                    .width((pos.second - BLOCK_GAP).dp)
                    .height(ROW_HEIGHT)
                    .onFocusChanged { if (it.hasFocus) onFocus(p) }
                    .then(if (focusOnAir && onAir) Modifier.requestFocusWhenPlaced() else Modifier),
                container = if (onAir) c.surface3.copy(alpha = ON_AIR_ALPHA) else c.surface2,
                outlined = false,
                focusScale = 1f,
                cornerRadius = CELL_RADIUS,
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        p.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (past) c.textSecondary else c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(clock(p.startMs), fontSize = 13.sp, color = c.textSecondary, maxLines = 1)
                }
            }
        }
        NowMarker(state, Modifier.align(Alignment.TopStart), ROW_HEIGHT)
    }
}

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

private val CHANNEL_COLUMN = 150.dp
private val ROW_HEIGHT = 44.dp
private val ROW_GAP = 4.dp
private val CELL_RADIUS = 6.dp
private val PREVIEW_HEIGHT = 112.dp
private val PREVIEW_ART = 200.dp
private const val BLOCK_GAP = 4f
private const val MS_PER_MINUTE = 60_000L
private const val ON_AIR_ALPHA = 0.9f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RadarTab(
    radar: RadarUiState,
    nowMs: Long,
    onSlot: (RadarSlot) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val slots =
        listOf(
            RadarSlot.TOP_DAY to R.string.guide_radar_top,
            RadarSlot.NOW to R.string.guide_radar_now,
            RadarSlot.SOON to R.string.guide_radar_soon,
            RadarSlot.EVENING to R.string.guide_radar_evening,
        )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { (slot, label) -> PillChip(stringResource(label), radar.slot == slot, { onSlot(slot) }) }
    }
    when {
        radar.loading -> StateView(StateKind.LOADING, "")
        radar.items.isEmpty() -> StateView(StateKind.EMPTY, stringResource(R.string.guide_radar_empty))
        else ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(radar.items, key = { _, entry -> entry.channel.id + entry.programme.startMs }) { i, item ->
                    RadarRow(item, nowMs, i == 0) { onOpenChannel(item.channel.id) }
                }
            }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RadarRow(
    item: RadarItem,
    nowMs: Long,
    first: Boolean,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    val p = item.programme
    FocusItem(
        onClick,
        Modifier
            .fillMaxWidth()
            .height(RADAR_ROW_HEIGHT)
            .then(if (first) Modifier.requestFocusWhenPlaced() else Modifier),
        container = c.surface2,
        outlined = false,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                clockRange(p.startMs, p.endMs),
                fontSize = 14.sp,
                color = c.textSecondary,
                modifier = Modifier.width(110.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    p.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(item.channel.displayName, p.category).joinToString(" · "),
                    fontSize = 13.sp,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p.isOnAt(nowMs)) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(c.outline, CircleShape)) {
                        val done = p.progressAt(nowMs)
                        Box(Modifier.fillMaxHeight().fillMaxWidth(done).background(c.accent, CircleShape))
                    }
                }
            }
            item.ratingTenths?.let {
                val rating = String.format(java.util.Locale.forLanguageTag("pl"), "%.1f", it / 10f)
                Text("★ $rating", fontSize = 15.sp, color = c.textPrimary)
            }
        }
    }
}

private val RADAR_ROW_HEIGHT = 62.dp

private fun clockRange(
    startMs: Long,
    endMs: Long,
): String {
    val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return f.format(java.util.Date(startMs)) + "–" + f.format(java.util.Date(endMs))
}
