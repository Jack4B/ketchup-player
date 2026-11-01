package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity
import pl.ketchupplayer.tv.core.policy.FailoverSettings

@Composable
fun PlaybackSettingsRoute(
    onBack: () -> Unit,
    viewModel: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clock by viewModel.clockEnabled.collectAsStateWithLifecycle()
    val debounce by viewModel.remoteDebounceMs.collectAsStateWithLifecycle()
    PlaybackSettingsScreen(
        state,
        viewModel::onEvent,
        onBack,
        clock,
        viewModel::toggleClock,
        debounce,
        viewModel::cycleRemoteDebounce,
    )
}

/** Ustawienia odtwarzania: automatyczne przełączanie źródeł (failover). Każdy wiersz: OK zmienia wartość. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    settings: FailoverSettings,
    onEvent: (PlaybackSettingsEvent) -> Unit,
    onBack: () -> Unit,
    clockEnabled: Boolean = true,
    onToggleClock: () -> Unit = {},
    remoteDebounceMs: Int = 250,
    onCycleDebounce: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val c = KetchupTheme.colors
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.playback_title))
        SettingsListItem(
            title = stringResource(R.string.playback_failover),
            subtitle = stringResource(R.string.playback_failover_hint),
            onClick = { onEvent(PlaybackSettingsEvent.ToggleEnabled) },
            modifier = Modifier.requestFocusWhenPlaced(),
            trailing = { Switch(checked = settings.enabled, onCheckedChange = null) },
        )
        SettingsListItem(
            title = stringResource(R.string.playback_clock),
            subtitle = stringResource(R.string.playback_clock_hint),
            onClick = onToggleClock,
            trailing = { Switch(checked = clockEnabled, onCheckedChange = null) },
        )
        SettingsListItem(
            title = stringResource(R.string.playback_sensitivity),
            subtitle = stringResource(sensitivityHint(settings.sensitivity)),
            onClick = { onEvent(PlaybackSettingsEvent.CycleSensitivity) },
            trailing = { Text(stringResource(sensitivityName(settings.sensitivity)), color = c.textPrimary) },
        )
        SettingsListItem(
            title = stringResource(R.string.playback_delay),
            subtitle = stringResource(R.string.playback_delay_hint),
            onClick = { onEvent(PlaybackSettingsEvent.CycleDelay) },
            trailing = {
                Text(
                    stringResource(R.string.playback_seconds, settings.tvDelaySeconds),
                    color = c.textPrimary,
                )
            },
        )
        SettingsListItem(
            title = stringResource(R.string.playback_remote),
            subtitle = stringResource(R.string.playback_remote_hint),
            onClick = onCycleDebounce,
            trailing = {
                Text(
                    if (remoteDebounceMs == 0) stringResource(R.string.playback_remote_off) else "$remoteDebounceMs ms",
                    color = c.textPrimary,
                )
            },
        )
    }
}

private fun sensitivityName(s: FailoverSensitivity): Int =
    when (s) {
        FailoverSensitivity.FAST -> R.string.playback_sens_fast
        FailoverSensitivity.STANDARD -> R.string.playback_sens_standard
        FailoverSensitivity.CAUTIOUS -> R.string.playback_sens_cautious
    }

private fun sensitivityHint(s: FailoverSensitivity): Int =
    when (s) {
        FailoverSensitivity.FAST -> R.string.playback_sens_fast_hint
        FailoverSensitivity.STANDARD -> R.string.playback_sens_standard_hint
        FailoverSensitivity.CAUTIOUS -> R.string.playback_sens_cautious_hint
    }
