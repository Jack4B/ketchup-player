package pl.ketchupplayer.tv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
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
import pl.ketchupplayer.tv.core.data.vod.EpisodeEntry
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.PillChip
import pl.ketchupplayer.tv.core.designsystem.PosterCard
import pl.ketchupplayer.tv.core.designsystem.PosterImage
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.StateKind
import pl.ketchupplayer.tv.core.designsystem.StateView
import pl.ketchupplayer.tv.core.model.VodItem
import java.util.Locale

@Composable
fun DetailsRoute(
    itemId: String,
    onPlay: (itemId: String, fromStart: Boolean) -> Unit,
    onOpen: (String) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(key = itemId),
) {
    viewModel.bind(itemId)
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailsScreen(state, viewModel::onEvent, onPlay, onOpen)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    state: DetailsUiState,
    onEvent: (DetailsEvent) -> Unit,
    onPlay: (itemId: String, fromStart: Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    val entry = state.entry
    when {
        state.loading -> ScreenScaffold { StateView(StateKind.LOADING, stringResource(R.string.details_loading)) }
        entry == null -> ScreenScaffold { StateView(StateKind.EMPTY, stringResource(R.string.details_not_found)) }
        else -> Content(state, onEvent, onPlay, onOpen)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Content(
    state: DetailsUiState,
    onEvent: (DetailsEvent) -> Unit,
    onPlay: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    val c = KetchupTheme.colors
    val entry = checkNotNull(state.entry)
    val item = entry.item
    val primary = remember { FocusRequester() }
    LaunchedEffect(item.id) {
        repeat(FOCUS_RETRIES) {
            withFrameNanos { }
            if (runCatching { primary.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Grafika w tle (backdrop od dostawcy albo plakat), przyciemniona gradientem: tekst zawsze czytelny.
        PosterImage(item.backdrop ?: item.poster, item.title, Modifier.fillMaxSize().alpha(BACKDROP_ALPHA))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(c.background, c.background.copy(alpha = GRADIENT_END_ALPHA))),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, c.background.copy(alpha = GRADIENT_BOTTOM_ALPHA))),
            ),
        )
        DetailsBody(state, item, primary, onEvent, onPlay, onOpen)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailsBody(
    state: DetailsUiState,
    item: pl.ketchupplayer.tv.core.model.VodItem,
    primary: FocusRequester,
    onEvent: (DetailsEvent) -> Unit,
    onPlay: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    val c = KetchupTheme.colors
    ScreenScaffold {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 2)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InfoPill(stringResource(if (state.isSeries) R.string.details_series else R.string.details_movie))
                    item.year?.let { InfoPill(it.toString()) }
                    if (state.isSeries && state.seasons.isNotEmpty()) {
                        InfoPill(stringResource(R.string.details_seasons_count, state.seasons.size))
                    }
                    item.durationMin?.let { InfoPill(stringResource(R.string.details_minutes, it)) }
                    item.rating?.let {
                        InfoPill(
                            "★ " + String.format(java.util.Locale.forLanguageTag("pl"), "%.1f", it),
                            true,
                        )
                    }
                }
                (item.genre ?: item.category)?.let { Text(it, fontSize = 15.sp, color = c.textSecondary) }
                Text(
                    item.plot?.takeIf { it.isNotBlank() } ?: stringResource(R.string.details_no_plot),
                    fontSize = 15.sp,
                    color = c.textPrimary,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                item.cast?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.details_cast), fontSize = 12.sp, color = c.textSecondary)
                    Text(it, fontSize = 14.sp, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                Modifier
                    .width(POSTER_WIDTH)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(c.cornerRadius.coerceAtMost(12.dp))),
            ) {
                PosterImage(item.poster, item.title)
            }
        }
        Buttons(state, primary, onEvent, onPlay)
        if (state.isSeries) Seasons(state, onEvent, onPlay)
        if (state.similar.isNotEmpty()) Similar(state.similar, onOpen)
    }
}

private val POSTER_WIDTH = 130.dp
private const val BACKDROP_ALPHA = 0.5f
private const val GRADIENT_END_ALPHA = 0.1f
private const val GRADIENT_BOTTOM_ALPHA = 0.85f

/** Pigułka z krótką informacją (rok, czas, ocena); ocena w kolorze akcentu. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfoPill(
    text: String,
    highlight: Boolean = false,
) {
    val c = KetchupTheme.colors
    val color = if (highlight) c.accent else c.textPrimary
    Text(
        text,
        fontSize = 14.sp,
        color = color,
        modifier =
            Modifier
                .border(1.dp, if (highlight) c.accent else c.outline, RoundedCornerShape(PILL_RADIUS))
                .background(c.surface2.copy(alpha = PILL_ALPHA), RoundedCornerShape(PILL_RADIUS))
                .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

private val PILL_RADIUS = 18.dp
private const val PILL_ALPHA = 0.7f
private const val FOCUS_RETRIES = 20

@Composable
private fun Buttons(
    state: DetailsUiState,
    primary: FocusRequester,
    onEvent: (DetailsEvent) -> Unit,
    onPlay: (String, Boolean) -> Unit,
) {
    val entry = checkNotNull(state.entry)
    val target = state.target
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (target != null) {
            val label =
                when {
                    target.resuming && target.code != null ->
                        stringResource(R.string.details_resume_episode, target.code, clock(target.positionMs))
                    target.resuming -> stringResource(R.string.details_resume, clock(target.positionMs))
                    target.code != null -> stringResource(R.string.details_play_episode, target.code)
                    else -> stringResource(R.string.details_play)
                }
            KetchupButton(label, { onPlay(target.itemId, false) }, primary = true, focusRequester = primary)
            if (target.resuming) {
                KetchupButton(stringResource(R.string.details_from_start), { onPlay(target.itemId, true) })
            }
        }
        KetchupButton(
            stringResource(if (entry.myList) R.string.details_in_my_list else R.string.details_my_list),
            { onEvent(DetailsEvent.ToggleMyList) },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Seasons(
    state: DetailsUiState,
    onEvent: (DetailsEvent) -> Unit,
    onPlay: (String, Boolean) -> Unit,
) {
    when {
        state.episodesLoading -> StateView(StateKind.LOADING, stringResource(R.string.details_loading))
        state.episodesFailed ->
            StateView(
                StateKind.ERROR,
                stringResource(R.string.details_episodes_error),
                actionLabel = stringResource(R.string.details_retry),
                onAction = { onEvent(DetailsEvent.RetryEpisodes) },
            )
        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.seasons.forEach { s ->
                    PillChip(stringResource(R.string.details_season, s), state.season == s, {
                        onEvent(DetailsEvent.SelectSeason(s))
                    })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.episodes, key = { it.episode.id }) { e -> EpisodeCard(e) { onPlay(e.episode.id, false) } }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    entry: EpisodeEntry,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    val e = entry.episode
    FocusItem(
        onClick,
        Modifier.width(EPISODE_WIDTH),
        container = androidx.compose.ui.graphics.Color.Transparent,
        outlined = false,
    ) {
        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(c.cornerRadius.coerceAtMost(10.dp))),
            ) {
                PosterImage(e.poster, e.title)
                if (entry.progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                androidx.compose.ui.graphics
                                    .Color(0x66000000),
                            ),
                    ) {
                        Box(Modifier.fillMaxWidth(entry.progress).fillMaxHeight().background(c.accent))
                    }
                }
            }
            Text(
                "${e.number}. ${e.title}",
                fontSize = 13.sp,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            e.durationMin?.let {
                Text(
                    stringResource(R.string.details_minutes, it),
                    fontSize = 12.sp,
                    color = c.textSecondary,
                )
            }
        }
    }
}

private val EPISODE_WIDTH = 165.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Similar(
    items: List<VodItem>,
    onOpen: (String) -> Unit,
) {
    val c = KetchupTheme.colors
    Text(
        stringResource(R.string.details_similar),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = c.textPrimary,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { s ->
            PosterCard(s.title, { onOpen(s.id) }, imageUrl = s.poster)
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h >
        0
    ) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
