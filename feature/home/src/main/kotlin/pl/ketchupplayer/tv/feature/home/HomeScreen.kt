package pl.ketchupplayer.tv.feature.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, onOpenChannel, onOpenGuide, onOpenProviders)
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
) {
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(stringResource(R.string.home_title))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "hero") { Hero(state, onOpenChannel, onOpenGuide, onOpenProviders) }
            if (state.recent.isNotEmpty()) {
                item(key = "continue") {
                    CardsSection(
                        stringResource(R.string.home_row_continue),
                        state.recent,
                        state.nowMs,
                        onOpenChannel,
                        firstFocus = true,
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
        featured?.let { ChannelArt(it.channel.displayName, Modifier.width(HERO_ART_WIDTH).fillMaxHeight()) }
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
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
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
            ChannelArt(item.channel.displayName, Modifier.fillMaxWidth().height(CARD_ART_HEIGHT))
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

/** Grafika kanału (do czasu logo z dostawcy): płytka z nazwą w kolorach motywu, jak w liście kanałów. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelArt(
    name: String,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    Box(
        modifier.background(c.channelTile, RoundedCornerShape(c.cornerRadius / 2)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(ART_CHARS),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = c.channelTileText,
            maxLines = 1,
        )
    }
}

private const val ART_CHARS = 8

private fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
