package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Zielona kropka/pigułka nowych odcinków — jedyne użycie zieleni w UI (KONSPEKT §7.2). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NewEpisodeBadge(
    modifier: Modifier = Modifier,
    count: Int? = null,
    label: String? = null,
) {
    val c = KetchupTheme.colors
    if (count == null) {
        Box(modifier.size(14.dp).background(c.newEpisode, CircleShape))
    } else {
        Row(
            modifier =
                modifier
                    .background(
                        c.newEpisode,
                        RoundedCornerShape(50),
                    ).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label ?: count.toString(), fontSize = 16.sp, color = c.onAccent)
        }
    }
}

/** Plakat (2:3) z tytułem i opcjonalną plakietką nowych odcinków. [poster] rysuje obraz (Coil w kolejnych fazach). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    newEpisodes: Int? = null,
    newEpisodeLabel: String? = null,
    poster: @Composable BoxScope.() -> Unit = {},
) {
    val c = KetchupTheme.colors
    FocusItem(onClick, modifier.width(POSTER_WIDTH), focusScale = 1.06f) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        2f / 3f,
                    ).background(c.surface3, RoundedCornerShape(c.cornerRadius.coerceAtMost(12.dp))),
            ) {
                poster()
                if (newEpisodes != null) {
                    NewEpisodeBadge(Modifier.align(Alignment.TopEnd).padding(8.dp), newEpisodes, newEpisodeLabel)
                }
            }
            Text(title, fontSize = 18.sp, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val POSTER_WIDTH = 170.dp

/** Wiersz kanału: numer, kafel logo, nazwa, bieżąca audycja z paskiem postępu (0..1). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelRow(
    number: Int,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowPlaying: String? = null,
    progress: Float? = null,
) {
    val c = KetchupTheme.colors
    FocusItem(onClick, modifier.fillMaxWidth().height(CHANNEL_ROW_HEIGHT)) {
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(number.toString(), fontSize = 20.sp, color = c.textSecondary, modifier = Modifier.width(48.dp))
            Box(
                Modifier
                    .background(
                        c.channelTile,
                        RoundedCornerShape(8.dp),
                    ).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(name.take(CHANNEL_TILE_CHARS), fontSize = 18.sp, color = c.channelTileText)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontSize = 20.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nowPlaying != null) {
                    Text(
                        nowPlaying,
                        fontSize = 16.sp,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (progress != null) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(c.outline, CircleShape)) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(
                                    progress.coerceIn(0f, 1f),
                                ).background(c.accent, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

private val CHANNEL_ROW_HEIGHT = 72.dp
private const val CHANNEL_TILE_CHARS = 6

/** Przycisk główny (akcent, tekst #0B0B0B/biały wg kontrastu) lub drugorzędny (panel). Wysokość ≥ 56 dp. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KetchupButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick = onClick,
        modifier = modifier.height(BUTTON_HEIGHT),
        focusRequester = focusRequester,
        contentAlignment = Alignment.Center,
        container = if (primary) c.accent else null,
        focusedContainer = if (primary) c.accent else null,
        ringColor = if (primary) c.textPrimary else null,
    ) {
        Text(
            text,
            fontSize = 20.sp,
            color = if (primary) c.onAccent else c.textPrimary,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

private val BUTTON_HEIGHT = 56.dp

/** Pozycja ustawień: tytuł, opis, opcjonalna kontrolka po prawej (przełącznik, strzałka). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsListItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = KetchupTheme.colors
    FocusItem(onClick, modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 20.sp, color = c.textPrimary)
                if (subtitle != null) Text(subtitle, fontSize = 16.sp, color = c.textSecondary)
            }
            trailing?.invoke()
        }
    }
}

enum class StateKind { LOADING, EMPTY, ERROR }

/** Stan ekranu: ładowanie / pusto / błąd (z przyciskiem ponowienia). Teksty dostarcza wywołujący (strings.xml). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StateView(
    kind: StateKind,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = KetchupTheme.colors
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(48.dp).background(if (kind == StateKind.ERROR) c.outline else c.surface3, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (kind) {
                    StateKind.LOADING -> "…"
                    StateKind.EMPTY -> "∅"
                    StateKind.ERROR -> "!"
                },
                fontSize = 24.sp,
                color = c.textPrimary,
            )
        }
        Text(message, fontSize = 20.sp, color = c.textPrimary)
        if (actionLabel != null && onAction != null) KetchupButton(actionLabel, onAction, primary = true)
    }
}
