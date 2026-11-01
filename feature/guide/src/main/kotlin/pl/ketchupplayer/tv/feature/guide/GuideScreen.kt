package pl.ketchupplayer.tv.feature.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.StateKind
import pl.ketchupplayer.tv.core.designsystem.StateView
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
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
    GuideScreen(state, viewModel::programmes, onOpenChannel, onOpenProviders, onBack)
}

/**
 * Przewodnik: wiersze kanałów, oś czasu 12 h od pełnej półgodziny. Wszystkie wiersze przewijają się poziomo razem
 * (wspólny [rememberScrollState]); audycje wiersza ładują się dopiero, gdy wiersz trafia na ekran.
 * D-pad: GÓRA/DÓŁ zmienia kanał, LEWO/PRAWO audycję, OK otwiera kanał, BACK wychodzi.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GuideScreen(
    state: GuideUiState,
    loadProgrammes: suspend (channelId: String) -> List<EpgProgramme>,
    onOpenChannel: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    ScreenScaffold(scrollable = false) {
        ScreenTitle(stringResource(R.string.guide_title))
        when {
            state.loading -> StateView(StateKind.LOADING, "")
            state.channels.isEmpty() ->
                StateView(
                    StateKind.EMPTY,
                    stringResource(R.string.guide_empty),
                    actionLabel = stringResource(R.string.guide_open_providers),
                    onAction = onOpenProviders,
                )
            else -> Grid(state, loadProgrammes, onOpenChannel)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Grid(
    state: GuideUiState,
    loadProgrammes: suspend (String) -> List<EpgProgramme>,
    onOpenChannel: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(Modifier.width(CHANNEL_COLUMN))
        Box(Modifier.weight(1f).horizontalScroll(scroll)) { TimeScale(state) }
    }
    LazyColumn(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.channels, key = { it.id }) { ch ->
            val first = ch.id == state.channels.first().id
            Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                ChannelCell(ch, ch.id in state.favorites, first) { onOpenChannel(ch.id) }
                val programmes by produceState<List<EpgProgramme>?>(null, ch.id, state.epgRevision) {
                    value =
                        loadProgrammes(ch.id)
                }
                Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                    Timeline(state, programmes) { onOpenChannel(ch.id) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TimeScale(state: GuideUiState) {
    val c = KetchupTheme.colors
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    Box(Modifier.width(GuideGeometry.totalWidthDp().dp).height(28.dp)) {
        val steps = GuideGeometry.WINDOW_MINUTES / GuideGeometry.STEP_MINUTES
        for (i in 0 until steps) {
            val minutes = i * GuideGeometry.STEP_MINUTES
            Text(
                fmt.format(Date(state.windowStartMs + minutes * MS_PER_MINUTE)),
                fontSize = 16.sp,
                color = c.textSecondary,
                modifier = Modifier.offset(x = (minutes * GuideGeometry.DP_PER_MINUTE).dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCell(
    channel: Channel,
    favorite: Boolean,
    initialFocus: Boolean,
    onClick: () -> Unit,
) {
    FocusItem(
        onClick,
        Modifier
            .width(CHANNEL_COLUMN - 8.dp)
            .height(ROW_HEIGHT)
            .then(if (initialFocus) Modifier.requestFocusWhenPlaced() else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            (if (favorite) "★ " else "") + channel.displayName,
            fontSize = 18.sp,
            color = KetchupTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Timeline(
    state: GuideUiState,
    programmes: List<EpgProgramme>?,
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
                fontSize = 16.sp,
                color = c.textSecondary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
            )
        }
        placed.forEach { (p, pos) ->
            val onAir = p.isOnAt(state.nowMs)
            FocusItem(
                onOpen,
                Modifier.offset(x = pos.first.dp).width((pos.second - BLOCK_GAP).dp).height(ROW_HEIGHT),
                container = if (onAir) c.surface3 else null,
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    p.title,
                    fontSize = 16.sp,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }
}

private val CHANNEL_COLUMN = 220.dp
private val ROW_HEIGHT = 56.dp
private const val BLOCK_GAP = 4f
private const val MS_PER_MINUTE = 60_000L
