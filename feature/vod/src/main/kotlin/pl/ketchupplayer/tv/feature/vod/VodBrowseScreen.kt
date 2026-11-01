package pl.ketchupplayer.tv.feature.vod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.PillChip
import pl.ketchupplayer.tv.core.designsystem.PosterCard
import pl.ketchupplayer.tv.core.designsystem.PosterImage
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.SearchField
import pl.ketchupplayer.tv.core.designsystem.ShellHeader
import pl.ketchupplayer.tv.core.designsystem.StateKind
import pl.ketchupplayer.tv.core.designsystem.StateView
import pl.ketchupplayer.tv.core.designsystem.glassBlur
import pl.ketchupplayer.tv.core.designsystem.glassPanel
import java.util.Locale

@Composable
fun VodBrowseRoute(
    mode: VodMode,
    onOpen: (String) -> Unit,
    onOpenProviders: () -> Unit,
    viewModel: VodBrowseViewModel = hiltViewModel(key = mode.name),
) {
    viewModel.bind(mode)
    val state by viewModel.state.collectAsStateWithLifecycle()
    VodBrowseScreen(state, viewModel::onEvent, onOpen, onOpenProviders)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VodBrowseScreen(
    state: VodBrowseUiState,
    onEvent: (VodBrowseEvent) -> Unit,
    onOpen: (String) -> Unit,
    onOpenProviders: () -> Unit,
) {
    val title =
        when (state.mode) {
            VodMode.MOVIES -> R.string.vod_movies
            VodMode.SERIES -> R.string.vod_series
            VodMode.MYLIST -> R.string.vod_my_list
            VodMode.SEARCH -> R.string.vod_search
        }
    val hint =
        when (state.mode) {
            VodMode.MOVIES -> R.string.vod_search_movies
            VodMode.SERIES -> R.string.vod_search_series
            VodMode.MYLIST -> R.string.vod_search_mylist
            VodMode.SEARCH -> R.string.vod_search_all
        }
    val empty =
        when (state.mode) {
            VodMode.MOVIES -> R.string.vod_empty_movies
            VodMode.SERIES -> R.string.vod_empty_series
            VodMode.MYLIST -> R.string.vod_empty_mylist
            VodMode.SEARCH -> R.string.vod_empty_all
        }
    var focused by remember { mutableStateOf<VodEntry?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    // Kategorie chowają się, gdy fokus wchodzi na miniatury (siatka dostaje całą szerokość: 6 kolumn zamiast 4),
    // i wracają po LEWO z pierwszej kolumny albo BACK.
    var categoriesOpen by remember { mutableStateOf(true) }
    val categoryFocus = remember { FocusRequester() }
    val gridFirst = remember { FocusRequester() }
    var revealTick by remember { mutableIntStateOf(0) }
    val reveal: () -> Unit = {
        categoriesOpen = true
        revealTick++
    }
    BackHandler(enabled = !categoriesOpen, onBack = reveal)
    LaunchedEffect(revealTick, state.loading, state.total == 0) {
        // Start i powrót: fokus na wybranej kategorii (panel potrzebuje chwili na złożenie).
        repeat(FOCUS_SETTLE_FRAMES) { withFrameNanos { } }
        repeat(FOCUS_RETRIES) {
            withFrameNanos { }
            if (runCatching { categoryFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Grafika zaznaczonego tytułu pod całą treścią: ciemniejsza po lewej (czytelne panele), płynnie ciemna u dołu.
        Backdrop(focused)
        ScreenScaffold(scrollable = false) {
            ShellHeader(stringResource(title), tabs = {
                SearchField(
                    hint = stringResource(hint),
                    value = state.query,
                    onChange = { onEvent(VodBrowseEvent.Search(it)) },
                    modifier = Modifier.width(SEARCH_WIDTH),
                )
            })
            when {
                state.loading -> StateView(StateKind.LOADING, stringResource(R.string.vod_loading))
                state.total == 0 ->
                    StateView(
                        StateKind.EMPTY,
                        stringResource(empty),
                        actionLabel = stringResource(R.string.vod_open_providers),
                        onAction = onOpenProviders,
                    )
                else ->
                    Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                        AnimatedVisibility(
                            visible = categoriesOpen,
                            enter = expandHorizontally(tween(PANEL_ANIMATION_MS)),
                            exit = shrinkHorizontally(tween(PANEL_ANIMATION_MS)),
                        ) {
                            CategoriesPanel(
                                state,
                                onEvent,
                                Modifier.padding(end = 16.dp).width(CATEGORIES_WIDTH).fillMaxHeight(),
                                categoryFocus,
                                gridFirst,
                            )
                        }
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Toolbar(state, onEvent, focusedIndex)
                            Grid(
                                state,
                                onOpen,
                                if (categoriesOpen) COLUMNS else COLUMNS_WIDE,
                                reveal,
                                gridFirst,
                            ) { index, entry ->
                                categoriesOpen = false
                                focusedIndex = index
                                focused = entry
                            }
                        }
                    }
            }
        }
    }
}

/** Pełnoekranowa grafika zaznaczonego tytułu z gradientami; bez zaznaczenia nic nie rysuje. */
@Composable
private fun Backdrop(entry: VodEntry?) {
    val c = KetchupTheme.colors
    val item = entry?.item ?: return
    val url = item.backdrop ?: item.poster ?: return
    Box(Modifier.fillMaxSize()) {
        PosterImage(url, item.title, Modifier.fillMaxSize().glassBlur().alpha(BACKDROP_ALPHA), showInitial = false)
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(c.background, c.background.copy(alpha = 0.7f), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, c.background.copy(alpha = 0.85f))),
            ),
        )
    }
}

private const val BACKDROP_ALPHA = 0.55f

private val CATEGORIES_WIDTH = 300.dp
private const val COLUMNS = 4
private const val COLUMNS_WIDE = 6
private const val PANEL_ANIMATION_MS = 220
private const val FOCUS_RETRIES = 20
private const val FOCUS_SETTLE_FRAMES = 3

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoriesPanel(
    state: VodBrowseUiState,
    onEvent: (VodBrowseEvent) -> Unit,
    modifier: Modifier,
    focus: FocusRequester,
    toGrid: FocusRequester,
) {
    // PRAWO z dowolnej kategorii wchodzi na pierwszą miniaturę (a nie na chipy sortowania obok górnych kategorii).
    Column(
        modifier
            .glassPanel()
            .padding(10.dp)
            .onPreviewKeyEvent { k ->
                val right = k.type == KeyEventType.KeyDown && k.key == Key.DirectionRight
                if (right) runCatching { toGrid.requestFocus() }
                right
            },
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "all") {
                CategoryItem(
                    stringResource(R.string.vod_all),
                    state.category == null,
                    focus.takeIf {
                        state.category ==
                            null
                    },
                ) {
                    onEvent(VodBrowseEvent.SelectCategory(null))
                }
            }
            item(key = "recent") {
                val recent = state.category == CATEGORY_RECENT
                CategoryItem(stringResource(R.string.vod_recent), recent, focus.takeIf { recent }) {
                    onEvent(VodBrowseEvent.SelectCategory(CATEGORY_RECENT))
                }
            }
            items(state.categories, key = { it.first }) { (name, _) ->
                val selected = state.category == name
                CategoryItem(name, selected, focus.takeIf { selected }) { onEvent(VodBrowseEvent.SelectCategory(name)) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryItem(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        Modifier.fillMaxWidth(),
        selected = selected,
        focusRequester = focusRequester,
        container = if (selected) c.surface3 else Color.Transparent,
        outlined = false,
    ) {
        Text(
            text,
            fontSize = 17.sp,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun Toolbar(
    state: VodBrowseUiState,
    onEvent: (VodBrowseEvent) -> Unit,
    focusedIndex: Int,
) {
    val sorts =
        listOf(
            VodSort.NEWEST to R.string.vod_sort_newest,
            VodSort.AZ to R.string.vod_sort_az,
            VodSort.RATING to R.string.vod_sort_rating,
            VodSort.YEAR to R.string.vod_sort_year,
        )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sorts.forEach { (sort, label) ->
            PillChip(stringResource(label), state.sort == sort, { onEvent(VodBrowseEvent.Sort(sort)) })
        }
        Spacer(Modifier.weight(1f))
        // Licznik jak w poprzedniej aplikacji: „12 / 340” (pozycja zaznaczonej karty i liczba wyników).
        Text(
            "${(focusedIndex + 1).coerceAtMost(state.entries.size)} / ${state.entries.size}",
            fontSize = 14.sp,
            color = KetchupTheme.colors.textSecondary,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

private val SEARCH_WIDTH = 320.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Grid(
    state: VodBrowseUiState,
    onOpen: (String) -> Unit,
    columns: Int,
    onLeftEdge: () -> Unit,
    firstFocus: FocusRequester,
    onFocus: (Int, VodEntry) -> Unit,
) {
    if (state.entries.isEmpty()) {
        StateView(StateKind.EMPTY, stringResource(R.string.vod_no_results, state.query))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(state.entries, key = { _, e -> e.item.id }) { index, e ->
            PosterCard(
                title = e.item.title,
                onClick = { onOpen(e.item.id) },
                focusRequester = if (index == 0) firstFocus else null,
                modifier =
                    Modifier
                        .onFocusChanged { if (it.hasFocus) onFocus(index, e) }
                        .onPreviewKeyEvent { k ->
                            // LEWO z pierwszej kolumny wysuwa kategorie i daje im fokus.
                            val leftEdge =
                                k.type == KeyEventType.KeyDown && k.key == Key.DirectionLeft && index % columns == 0
                            if (leftEdge) onLeftEdge()
                            leftEdge
                        },
                overlay = true,
                imageUrl = e.item.poster,
                subtitle = subtitle(e),
                progress = e.progress.takeIf { it > 0f },
                newEpisodes = if (e.hasNewEpisodes) 1 else null,
                newEpisodeLabel = if (e.hasNewEpisodes) stringResource(R.string.vod_new_episodes) else null,
                fillWidth = true,
            )
        }
    }
}

private fun subtitle(e: VodEntry): String? {
    val year = e.item.year?.toString()
    val rating = e.item.rating?.let { String.format(Locale.forLanguageTag("pl"), "%.1f", it) }
    return listOfNotNull(year, rating?.let { "★ $it" }).joinToString(" · ").ifEmpty { null }
}
