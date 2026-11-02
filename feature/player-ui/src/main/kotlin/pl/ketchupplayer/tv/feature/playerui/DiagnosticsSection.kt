package pl.ketchupplayer.tv.feature.playerui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.AccountInfo
import pl.ketchupplayer.tv.core.policy.StabilityLevel
import pl.ketchupplayer.tv.core.policy.StabilityScore
import pl.ketchupplayer.tv.player.engine.PlaybackStats
import java.util.Locale

/**
 * Diagnostyka odtwarzania w panelu Info: bufor, łącze, przerwy i zgubione klatki tej sesji, ocena stabilności
 * oraz (dla list Xtream) ile połączeń konta jest w użyciu.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun DiagnosticsSection(
    stats: PlaybackStats,
    account: AccountInfo?,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    val score = StabilityScore.of(stats.rebuffers, stats.stallMs, stats.playedMs, stats.droppedFrames)
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.player_diag_title), fontSize = 15.sp, color = c.textSecondary)
        Line(stringResource(R.string.player_diag_buffer, seconds(stats.bufferAheadMs)))
        Line(
            stats.bandwidthKbps?.let { stringResource(R.string.player_diag_bandwidth, megabits(it)) }
                ?: stringResource(R.string.player_diag_bandwidth_unknown),
        )
        Line(
            if (stats.rebuffers == 0) {
                stringResource(R.string.player_diag_stalls_none)
            } else {
                stringResource(R.string.player_diag_stalls, stats.rebuffers, seconds(stats.stallMs))
            },
        )
        Line(stringResource(R.string.player_diag_dropped, stats.droppedFrames))
        Text(
            stringResource(R.string.player_diag_stability, score.percent, stringResource(levelName(score.level))),
            fontSize = 17.sp,
            color = levelColor(score.level),
        )
        account?.let { a -> connections(a)?.let { Line(it) } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Line(text: String) = Text(text, fontSize = 17.sp, color = KetchupTheme.colors.textPrimary)

@Composable
private fun connections(a: AccountInfo): String? {
    val active = a.activeConnections
    val max = a.maxConnections
    return if (active != null && max != null && max > 0) {
        stringResource(R.string.player_diag_connections, active, max)
    } else {
        null
    }
}

private fun levelName(level: StabilityLevel): Int =
    when (level) {
        StabilityLevel.STABLE -> R.string.player_diag_level_stable
        StabilityLevel.FAIR -> R.string.player_diag_level_fair
        StabilityLevel.UNSTABLE -> R.string.player_diag_level_unstable
    }

@Composable
private fun levelColor(level: StabilityLevel): Color =
    when (level) {
        StabilityLevel.STABLE -> KetchupTheme.colors.accent
        StabilityLevel.FAIR -> FAIR_COLOR
        StabilityLevel.UNSTABLE -> BAD_COLOR
    }

private fun seconds(ms: Long): String = String.format(Locale.forLanguageTag("pl"), "%.1f", ms / MS_PER_SEC)

private fun megabits(kbps: Long): String = String.format(Locale.forLanguageTag("pl"), "%.1f", kbps / KBIT_PER_MBIT)

private val FAIR_COLOR = Color(0xFFE0B84A)
private val BAD_COLOR = Color(0xFFFF6B6B)
private const val MS_PER_SEC = 1000.0
private const val KBIT_PER_MBIT = 1000.0
