package pl.ketchupplayer.tv.feature.playerui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import pl.ketchupplayer.tv.core.designsystem.glassPanel
import pl.ketchupplayer.tv.core.model.Channel

/**
 * Lista kanałów nad obrazem (OK w odtwarzaczu na żywo): ta sama grupa co bieżący kanał, fokus na bieżącym.
 * OK przełącza kanał, BACK zamyka (obsługuje ekran odtwarzacza).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ChannelZapList(
    channels: List<Channel>,
    currentId: String?,
    onPick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    val listState = rememberLazyListState()
    val current = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val index = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        listState.scrollToItem((index - 2).coerceAtLeast(0))
        withFrameNanos { }
        runCatching { current.requestFocus() }
    }
    Column(
        modifier
            .width(LIST_WIDTH)
            .fillMaxHeight()
            .glassPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.player_channels), fontSize = 16.sp, color = c.textSecondary)
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(channels, key = { it.id }) { ch ->
                val selected = ch.id == currentId
                FocusItem(
                    { onPick(ch) },
                    Modifier
                        .fillMaxWidth()
                        .height(
                            ROW_HEIGHT,
                        ).then(if (selected) Modifier.focusRequester(current) else Modifier),
                    selected = selected,
                    container = if (selected) c.surface3 else androidx.compose.ui.graphics.Color.Transparent,
                    outlined = false,
                    focusScale = 1.02f,
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            (ch.number ?: 0).toString(),
                            fontSize = 14.sp,
                            color = c.textSecondary,
                            modifier = Modifier.width(NUMBER_WIDTH),
                        )
                        ChannelLogo(ch.displayName, ch.logo, Modifier.width(LOGO_W).height(LOGO_H))
                        Text(
                            ch.displayName,
                            fontSize = 17.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private val LIST_WIDTH = 380.dp
private val ROW_HEIGHT = 52.dp
private val NUMBER_WIDTH = 30.dp
private val LOGO_W = 48.dp
private val LOGO_H = 32.dp
