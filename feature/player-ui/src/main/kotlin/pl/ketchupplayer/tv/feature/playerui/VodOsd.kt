package pl.ketchupplayer.tv.feature.playerui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.LocalUiClock
import pl.ketchupplayer.tv.player.engine.PlayerState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * OSD filmu (v5): szklany pas u dołu z tytułem, ikoną akcji (OK: pauza/wznów), osią czasu z gałką i dymkiem celu
 * podczas przewijania, czasem do końca oraz godziną zakończenia seansu.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun VodOsd(
    title: String,
    info: VodInfoUi?,
    state: PlayerState,
    scrub: ScrubUi?,
    modifier: Modifier,
    seek: TouchSeek? = null,
) {
    val c = KetchupTheme.colors
    val uiClock = LocalUiClock.current
    val duration = state.durationMs ?: 0L
    val position = scrub?.targetMs ?: state.positionMs
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val remaining = (duration - position).coerceAtLeast(0)
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = OSD_SCRIM))))
            // Dotyk (telefon): pasek nawigacji i wycięcie nie zasłaniają paska postępu; na TV odsunięcie jest zerowe.
            .then(
                if (seek != null) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    )
                } else {
                    Modifier
                },
            ).padding(horizontal = 32.dp)
            .padding(top = if (seek == null) 48.dp else 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        if (!info?.meta.isNullOrBlank()) Text(info?.meta.orEmpty(), fontSize = 17.sp, color = c.textSecondary)
        Box(Modifier.fillMaxWidth().height(BUBBLE_H)) {
            if (scrub != null) ScrubBubble(scrub, progress)
        }
        Timeline(progress, scrubbing = scrub != null, seek = seek)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (seek == null) PlayPauseBadge(state.isPlaying)
            Text(clock(position), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(
                if (duration > 0) "/ " + clock(duration) else "",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = SECONDARY_ALPHA),
            )
            Spacer(Modifier.weight(1f))
            if (duration > 0) {
                Text(
                    stringResource(R.string.player_remaining, clock(remaining)) + "  ·  " +
                        stringResource(R.string.player_ends_at, uiClock.hhmmAfter(remaining)),
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = SECONDARY_ALPHA),
                )
            }
        }
        if (seek == null) Hints()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Hints() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            R.string.player_hint_seek,
            R.string.player_hint_pause,
            R.string.player_hint_info,
            R.string.player_hint_opts,
        ).forEach { hint ->
            Text(
                stringResource(hint),
                fontSize = 14.sp,
                color = Color.White,
                modifier =
                    Modifier
                        .background(Color.White.copy(alpha = PILL), RoundedCornerShape(PILL_R))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Oś czasu: tor, wypełnienie w akcencie i gałka, która przy przewijaniu rośnie. */
@Composable
private fun Timeline(
    progress: Float,
    scrubbing: Boolean,
    seek: TouchSeek? = null,
) {
    val c = KetchupTheme.colors
    val seekDescription = stringResource(R.string.player_touch_seekbar)
    val thumb = if (scrubbing) THUMB_ACTIVE else THUMB
    // Dotyk: cel gestu ma min. 48 dp wysokości, tor i gałka zostają te same.
    val touch =
        if (seek == null) {
            Modifier.height(THUMB_ACTIVE)
        } else {
            Modifier
                .height(TOUCH_TARGET)
                .testTag(TAG_SEEKBAR)
                .semantics { contentDescription = seekDescription }
                .touchSeekGestures(seek)
        }
    Box(Modifier.fillMaxWidth().then(touch)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR)
                .align(Alignment.CenterStart)
                .background(Color.White.copy(alpha = BAR_TRACK), RoundedCornerShape(BAR)),
        )
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(BAR)
                .align(Alignment.CenterStart)
                .background(c.accent, RoundedCornerShape(BAR)),
        )
        Box(Modifier.fillMaxWidth().align(Alignment.CenterStart).centeredAt(progress)) {
            Box(Modifier.size(thumb).background(Color.White, CircleShape))
        }
    }
}

/** Dymek nad gałką: czas docelowy i przesunięcie („+1:30”, „−0:40”) w akcencie. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScrubBubble(
    scrub: ScrubUi,
    progress: Float,
) {
    val c = KetchupTheme.colors
    val sign = if (scrub.deltaMs >= 0) "+" else "−"
    Box(Modifier.fillMaxWidth().centeredAt(progress)) {
        Row(
            Modifier
                .background(Color.Black.copy(alpha = BUBBLE_ALPHA), RoundedCornerShape(BUBBLE_R))
                .border(1.dp, Color.White.copy(alpha = PILL), RoundedCornerShape(BUBBLE_R))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(clock(scrub.targetMs), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(sign + clock(abs(scrub.deltaMs)), fontSize = 17.sp, color = c.accent)
        }
    }
}

/** Okrągła plakietka z ikoną akcji pod OK: pauza, gdy gra; odtwarzanie, gdy wstrzymano. */
@Composable
internal fun PlayPauseBadge(
    isPlaying: Boolean,
    size: Dp = BADGE,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (isPlaying) R.string.player_a11y_pause else R.string.player_a11y_play)
    Box(
        modifier
            .size(size)
            .background(Color.White.copy(alpha = PILL), CircleShape)
            .border(1.dp, Color.White.copy(alpha = BADGE_EDGE), CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * ICON_RATIO)) {
            val w = this.size.width
            val h = this.size.height
            if (isPlaying) {
                val bar = w * PAUSE_BAR
                drawRoundRect(Color.White, Offset(w * PAUSE_L, 0f), Size(bar, h), CornerRadius(bar / 3))
                drawRoundRect(Color.White, Offset(w * PAUSE_R, 0f), Size(bar, h), CornerRadius(bar / 3))
            } else {
                val p =
                    Path().apply {
                        moveTo(w * PLAY_L, 0f)
                        lineTo(w, h / 2)
                        lineTo(w * PLAY_L, h)
                        close()
                    }
                drawPath(p, Color.White)
            }
        }
    }
}

/** Ustawia zawartość w poziomie tak, by jej środek wypadał w [fraction] szerokości (z przycięciem do krawędzi). */
private fun Modifier.centeredAt(fraction: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        val maxW = constraints.maxWidth
        val x =
            (maxW * fraction - placeable.width / 2f).roundToInt().coerceIn(
                0,
                (maxW - placeable.width).coerceAtLeast(0),
            )
        layout(maxW, placeable.height) { placeable.placeRelative(x, 0) }
    }

private val BUBBLE_H = 40.dp
private val BAR = 6.dp
private val THUMB = 18.dp
private val THUMB_ACTIVE = 26.dp
private val TOUCH_TARGET = 48.dp
private val BADGE = 40.dp
private const val OSD_SCRIM = 0.82f
private const val BAR_TRACK = 0.28f
private const val SECONDARY_ALPHA = 0.8f
private const val PILL = 0.16f
private const val BADGE_EDGE = 0.28f
private const val BUBBLE_ALPHA = 0.7f
private val BUBBLE_R = 14.dp
private val PILL_R = 14.dp
private const val ICON_RATIO = 0.42f
private const val PAUSE_BAR = 0.3f
private const val PAUSE_L = 0.05f
private const val PAUSE_R = 0.65f
private const val PLAY_L = 0.08f
