package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

/** Plakat (2:3) z tytułem, podtytułem, paskiem postępu i plakietką nowych odcinków; obraz ładuje Coil. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    newEpisodes: Int? = null,
    newEpisodeLabel: String? = null,
    imageUrl: String? = null,
    subtitle: String? = null,
    progress: Float? = null,
    fillWidth: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /** true: tytuł i podtytuł na dolnym gradiencie obrazu (jak karty w poprzedniej aplikacji), bez tekstu pod kartą. */
    overlay: Boolean = false,
    /** Proporcje obrazu: 2:3 plakat albo 16:9 karta pozioma z grafiką w tle. */
    aspect: Float = POSTER_ASPECT,
    cardWidth: androidx.compose.ui.unit.Dp = POSTER_WIDTH,
    poster: @Composable BoxScope.() -> Unit = {},
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        if (fillWidth) modifier.fillMaxWidth() else modifier.width(cardWidth),
        focusScale = 1.06f,
        focusRequester = focusRequester,
        onLongClick = onLongClick,
        container = Color.Transparent,
        outlined = false,
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(c.cornerRadius.coerceAtMost(12.dp)))
                    .background(c.surface3),
            ) {
                PosterImage(imageUrl, title)
                poster()
                if (overlay) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(OVERLAY_SCRIM))))
                            .padding(horizontal = 10.dp)
                            .padding(top = 28.dp, bottom = if (progress != null && progress > 0f) 10.dp else 8.dp),
                    ) {
                        Text(
                            title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle !=
                            null
                        ) {
                            Text(subtitle, fontSize = 13.sp, color = Color(OVERLAY_SUBTITLE), maxLines = 1)
                        }
                    }
                }
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color(0x66000000)),
                    ) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
                    }
                }
                if (newEpisodes != null) {
                    NewEpisodeBadge(Modifier.align(Alignment.TopEnd).padding(8.dp), newEpisodes, newEpisodeLabel)
                }
            }
            if (!overlay) {
                Text(title, fontSize = 13.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

private const val OVERLAY_SCRIM = 0xE6000000
private const val OVERLAY_SUBTITLE = 0xFFE6E6E6

/**
 * Logo kanału z listy dostawcy (Coil, z cache dyskowym i pamięciowym). Płytka w kolorze motywu pod spodem sprawia,
 * że ciemne i jasne logo są czytelne; gdy logo brakuje albo się nie wczyta, widać skrót nazwy kanału.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelLogo(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    labelSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    /** Duże kafle (podgląd, Home): bez logo pokazują pełną nazwę zamiast skrótu. */
    fullName: Boolean = false,
) {
    val c = KetchupTheme.colors
    Box(
        modifier.background(c.channelTile, RoundedCornerShape(LOGO_RADIUS)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            tileLabel(name),
            fontSize = labelSize,
            fontWeight = FontWeight.Bold,
            color = c.channelTileText,
            maxLines = 1,
        )
        if (!logoUrl.isNullOrBlank()) {
            coil3.compose.AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(LOGO_PADDING).background(c.channelTile),
            )
        }
    }
}

private val LOGO_RADIUS = 8.dp
private val LOGO_PADDING = 3.dp

/** Obraz z sieci (Coil); bez adresu lub przy błędzie zostaje tło kafla z inicjałem tytułu. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
    /** false: bez litery zastępczej (duże tła, gdzie napis wyglądałby jak błąd). */
    showInitial: Boolean = true,
) {
    val c = KetchupTheme.colors
    Box(
        modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.surface3, c.surface2))),
        contentAlignment = Alignment.Center,
    ) {
        // Zastępnik bez grafiki: jedna duża, przygaszona litera tytułu zamiast ucinanego słowa.
        if (showInitial) {
            Text(
                title.trim().take(1).uppercase(),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = c.textSecondary.copy(alpha = INITIAL_ALPHA),
            )
        }
        if (!url.isNullOrBlank()) {
            coil3.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val INITIAL_ALPHA = 0.35f
private val POSTER_WIDTH = 170.dp
private const val POSTER_ASPECT = 2f / 3f

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
    favorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    /** Godziny audycji po prawej, np. `19:30–20:00`. */
    timeRange: String? = null,
    /** Jedna linia (nazwa, audycja, godziny, cienki pasek): mieści ok. 7 kanałów na ekranie, jak w TiViMate. */
    compact: Boolean = false,
    logo: String? = null,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        modifier.fillMaxWidth().height(if (compact) COMPACT_ROW_HEIGHT else CHANNEL_ROW_HEIGHT),
        onLongClick = onLongClick,
        outlined = false,
    ) {
        if (compact) {
            CompactChannelRow(number, name, favorite, nowPlaying, progress, timeRange, logo)
            return@FocusItem
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(number.toString(), fontSize = 16.sp, color = c.textSecondary, modifier = Modifier.width(30.dp))
            ChannelLogo(name, logo, Modifier.width(56.dp).height(36.dp), labelSize = 14.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (favorite) "★ $name" else name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (nowPlaying != null) {
                    Text(
                        nowPlaying,
                        fontSize = 14.sp,
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
            if (timeRange != null) {
                Text(timeRange, fontSize = 14.sp, color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

private val CHANNEL_ROW_HEIGHT = 64.dp
private val COMPACT_ROW_HEIGHT = 52.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CompactChannelRow(
    number: Int,
    name: String,
    favorite: Boolean,
    nowPlaying: String?,
    progress: Float?,
    timeRange: String?,
    logo: String?,
) {
    val c = KetchupTheme.colors
    Box(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(number.toString(), fontSize = 16.sp, color = c.textSecondary, modifier = Modifier.width(36.dp))
            ChannelLogo(name, logo, Modifier.width(52.dp).height(32.dp))
            Text(
                if (favorite) "★ $name" else name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(COMPACT_NAME_WEIGHT),
            )
            Text(
                nowPlaying.orEmpty(),
                fontSize = 15.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(COMPACT_TITLE_WEIGHT),
            )
            if (timeRange != null) Text(timeRange, fontSize = 14.sp, color = c.textSecondary, maxLines = 1)
        }
        if (progress != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(c.outline),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(c.accent))
            }
        }
    }
}

private const val COMPACT_NAME_WEIGHT = 1f
private const val COMPACT_TITLE_WEIGHT = 1.3f
private const val CHANNEL_TILE_CHARS = 4

/** Etykieta kafelka: pierwsze słowo (do 4 znaków) albo inicjały, gdy to pojedynczy znak (`4 Fun TV` -> `4FT`). */
internal fun tileLabel(name: String): String {
    val words = name.trim().split(' ').filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> ""
        words.first().length >= 2 -> words.first().take(CHANNEL_TILE_CHARS)
        else -> words.take(3).joinToString("") { it.take(1) }
    }
}

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

/** Zakładka w nagłówku ekranu (Siatka | Radar | Moje zainteresowania); wybrana ma tło i dolną kreskę akcentu. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TabChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        modifier.height(36.dp),
        selected = selected,
        container = if (selected) c.surface3 else Color.Transparent,
        outlined = false,
        focusScale = 1f,
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Text(
                text,
                fontSize = 16.sp,
                color = if (selected) c.textPrimary else c.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (selected) {
                Box(Modifier.matchParentSize().padding(horizontal = 2.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(c.accent, CircleShape))
                }
            }
        }
    }
}

/** Pigułka filtra/sortowania (makieta v3): wybrana jest jasna z ciemnym tekstem, pozostałe przyciemnione. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        modifier.height(34.dp),
        selected = selected,
        container = if (selected) c.textPrimary else c.surface2,
        focusedContainer = if (selected) c.textPrimary else c.surface3,
        outlined = false,
        focusScale = 1.04f,
        cornerRadius = 17.dp,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) c.background else c.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** Pigułka z krótką informacją (rok, czas, ocena); ocena w kolorze akcentu. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InfoPill(
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
