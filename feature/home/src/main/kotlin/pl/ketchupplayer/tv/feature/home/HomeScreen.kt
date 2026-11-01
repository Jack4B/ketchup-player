package pl.ketchupplayer.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.designsystem.ChannelLogo
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.InfoPill
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.PosterCard
import pl.ketchupplayer.tv.core.designsystem.PosterImage
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.panel
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeRoute(
    onOpenChannel: (String) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDetails: (String) -> Unit = {},
    onPlayVod: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, onOpenChannel, onOpenGuide, onOpenProviders, onOpenDetails, onPlayVod)
}

/**
 * Home (makieta v3 02-home): hero „Polecane” + rzędy. Start: pierwszy kafel „Kontynuuj oglądanie”
 * (gdy rząd pusty: przycisk w hero). LEWO z pierwszego kafla trafia w szynę.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenChannel: (String) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenDetails: (String) -> Unit = {},
    onPlayVod: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(stringResource(R.string.home_title))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "hero") {
                if (state.featured != null) {
                    VodHero(state, onPlayVod, onOpenDetails)
                } else {
                    Hero(state, onOpenChannel, onOpenGuide, onOpenProviders)
                }
            }
            if (state.continueVod.isNotEmpty()) {
                item(key = "continue-vod") {
                    PosterRow(
                        stringResource(R.string.home_row_continue),
                        state.continueVod,
                        onOpenDetails,
                        landscape = true,
                    )
                }
            }
            if (state.newEpisodes.isNotEmpty()) {
                item(key = "new-episodes") {
                    PosterRow(
                        stringResource(R.string.home_row_new_episodes),
                        state.newEpisodes,
                        onOpenDetails,
                        landscape = true,
                        newLabel = stringResource(R.string.home_new_episodes_badge),
                    )
                }
            }
            if (state.myList.isNotEmpty()) {
                item(
                    key = "my-list",
                ) { PosterRow(stringResource(R.string.home_row_my_list), state.myList, onOpenDetails) }
            }
            if (state.recent.isNotEmpty()) {
                item(key = "continue") {
                    CardsSection(
                        stringResource(R.string.home_row_recent_channels),
                        state.recent,
                        state.nowMs,
                        onOpenChannel,
                        firstFocus = state.featured == null,
                    )
                }
            }
            if (state.liveNow.isNotEmpty()) {
                item(key = "live") {
                    CardsSection(
                        stringResource(R.string.home_row_live_now),
                        state.liveNow,
                        state.nowMs,
                        onOpenChannel,
                        firstFocus = false,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VodHero(
    state: HomeUiState,
    onPlayVod: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val c = KetchupTheme.colors
    val entry = checkNotNull(state.featured)
    val item = entry.item
    Box(Modifier.fillMaxWidth().height(VOD_HERO_HEIGHT).clip(RoundedCornerShape(c.cornerRadius))) {
        // Grafika na całą szerokość (backdrop albo plakat) z gradientem od lewej: tekst zawsze czytelny.
        PosterImage(item.backdrop ?: item.poster, item.title, Modifier.fillMaxSize(), showInitial = false)
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(c.background.copy(alpha = HERO_SCRIM_LEFT), Color.Transparent)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, c.background.copy(alpha = HERO_SCRIM_BOTTOM))),
            ),
        )
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(HERO_TEXT_FRACTION).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            Text(
                stringResource(
                    if (state.featuredResumes) R.string.home_continue_kicker else R.string.home_featured_kicker,
                ),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = c.textSecondary,
                letterSpacing = 2.sp,
            )
            Text(item.title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item.year?.let { InfoPill(it.toString()) }
                item.rating?.let { InfoPill("★ " + String.format(Locale.forLanguageTag("pl"), "%.1f", it), true) }
                (item.genre ?: item.category)?.let { InfoPill(it) }
            }
            item.plot?.let {
                Text(it, fontSize = 15.sp, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                KetchupButton(
                    stringResource(if (state.featuredResumes) R.string.home_resume else R.string.home_watch),
                    { onPlayVod(item.id) },
                    primary = true,
                    modifier = Modifier.requestFocusWhenPlaced(),
                )
                KetchupButton(stringResource(R.string.home_details), { onOpenDetails(item.id) })
            }
        }
    }
}

private val VOD_HERO_HEIGHT = 250.dp
private const val HERO_SCRIM_LEFT = 0.92f
private const val HERO_SCRIM_BOTTOM = 0.55f
private const val HERO_TEXT_FRACTION = 0.62f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterRow(
    title: String,
    entries: List<VodEntry>,
    onOpen: (String) -> Unit,
    landscape: Boolean = false,
    newLabel: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RowHeader(title, entries.size)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.item.id }) { e ->
                PosterCard(
                    e.item.title,
                    { onOpen(e.item.id) },
                    // Karta pozioma: grafika w tle (backdrop, a gdy go brak plakat), tytuł i ocena na gradiencie.
                    imageUrl = if (landscape) e.item.backdrop ?: e.item.poster else e.item.poster,
                    progress = e.progress.takeIf { it > 0f },
                    subtitle = if (landscape) cardSubtitle(e) else null,
                    overlay = landscape,
                    aspect = if (landscape) LANDSCAPE_ASPECT else POSTER_ASPECT,
                    cardWidth = if (landscape) LANDSCAPE_WIDTH else POSTER_CARD_WIDTH,
                    newEpisodes = if (newLabel != null) 1 else null,
                    newEpisodeLabel = newLabel,
                )
            }
        }
    }
}

private fun cardSubtitle(e: VodEntry): String? {
    val year = e.item.year?.toString()
    val rating = e.item.rating?.let { String.format(Locale.forLanguageTag("pl"), "%.1f", it) }
    return listOfNotNull(year, rating?.let { "★ $it" }).joinToString(" · ").ifEmpty { null }
}

private val LANDSCAPE_WIDTH = 270.dp
private val POSTER_CARD_WIDTH = 170.dp
private const val LANDSCAPE_ASPECT = 16f / 9f
private const val POSTER_ASPECT = 2f / 3f

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Hero(
    state: HomeUiState,
    onOpenChannel: (String) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    val c = KetchupTheme.colors
    val featured = state.recent.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .panel()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (featured != null) {
                Text(
                    stringResource(R.string.home_recent_kicker),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textSecondary,
                )
                Text(
                    featured.channel.displayName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                )
                featured.now?.let { p ->
                    Text(
                        stringResource(R.string.home_now_until, p.title, formatTime(p.endMs)),
                        fontSize = 15.sp,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    p.description?.let {
                        Text(
                            it,
                            fontSize = 15.sp,
                            color = c.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    KetchupButton(
                        stringResource(R.string.home_watch),
                        { onOpenChannel(featured.channel.id) },
                        primary = true,
                        modifier = if (state.recent.isEmpty()) Modifier.requestFocusWhenPlaced() else Modifier,
                    )
                    KetchupButton(stringResource(R.string.home_guide), onOpenGuide)
                }
            } else {
                Text(
                    stringResource(R.string.home_welcome_kicker),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textSecondary,
                )
                Text(
                    stringResource(R.string.home_welcome_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                )
                Text(stringResource(R.string.home_welcome_text), fontSize = 15.sp, color = c.textPrimary, maxLines = 2)
                KetchupButton(
                    stringResource(R.string.home_add_provider),
                    onOpenProviders,
                    primary = true,
                    modifier = Modifier.padding(top = 6.dp).requestFocusWhenPlaced(),
                )
            }
        }
        featured?.let {
            ChannelArt(
                it.channel.displayName,
                it.channel.logo,
                Modifier.width(HERO_ART_WIDTH).fillMaxHeight(),
            )
        }
    }
}

private val HERO_HEIGHT = 190.dp
private val HERO_ART_WIDTH = 220.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CardsSection(
    title: String,
    items: List<ChannelNow>,
    nowMs: Long,
    onOpenChannel: (String) -> Unit,
    firstFocus: Boolean,
) {
    val c = KetchupTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RowHeader(title, items.size)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.channel.id }) { item ->
                val first = firstFocus && item == items.first()
                ChannelCard(
                    item,
                    nowMs,
                    { onOpenChannel(item.channel.id) },
                    if (first) Modifier.requestFocusWhenPlaced() else Modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCard(
    item: ChannelNow,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    FocusItem(onClick, modifier.width(CARD_WIDTH)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ChannelArt(item.channel.displayName, item.channel.logo, Modifier.fillMaxWidth().height(CARD_ART_HEIGHT))
            Text(
                item.channel.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.now?.title ?: stringResource(R.string.home_live_badge),
                fontSize = 13.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.now?.let { p ->
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.outline, CircleShape)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p.progressAt(nowMs)).background(c.accent, CircleShape))
                }
            }
        }
    }
}

private val CARD_WIDTH = 190.dp
private val CARD_ART_HEIGHT = 84.dp

/** Grafika kanału: logo z listy dostawcy na płytce w kolorach motywu (a bez logo skrót nazwy). */
@Composable
private fun ChannelArt(
    name: String,
    logo: String?,
    modifier: Modifier,
) {
    ChannelLogo(name, logo, modifier, labelSize = 22.sp)
}

private fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/** Nagłówek rzędu: akcentowy pasek, tytuł i liczba pozycji po prawej. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RowHeader(
    title: String,
    count: Int,
) {
    val c = KetchupTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).background(c.accent, RoundedCornerShape(2.dp)))
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text(count.toString(), fontSize = 14.sp, color = c.textSecondary)
    }
}
