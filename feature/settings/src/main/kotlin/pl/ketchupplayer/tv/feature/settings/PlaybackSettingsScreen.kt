package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.Choice
import pl.ketchupplayer.tv.core.designsystem.ChoiceDialog
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.core.policy.BkFailoverPolicy
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
    val preview by viewModel.livePreview.collectAsStateWithLifecycle()
    PlaybackSettingsScreen(
        state,
        viewModel::onEvent,
        onBack,
        clock,
        viewModel::toggleClock,
        debounce,
        viewModel::setRemoteDebounce,
        preview,
        viewModel::toggleLivePreview,
    )
}

/** Ustawienia odtwarzania (failover, zegar, pilot): wartości z listy wyboru (ptaszek), przełączniki OK. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    settings: FailoverSettings,
    onEvent: (PlaybackSettingsEvent) -> Unit,
    onBack: () -> Unit,
    clockEnabled: Boolean = true,
    onToggleClock: () -> Unit = {},
    remoteDebounceMs: Int = 250,
    onSetDebounce: (Int) -> Unit = {},
    livePreview: Boolean = true,
    onToggleLivePreview: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val c = KetchupTheme.colors
    var picker by remember { mutableStateOf<PlaybackPicker?>(null) }
    picker?.let { p ->
        val spec =
            when (p) {
                PlaybackPicker.SENSITIVITY ->
                    PickerSpec(
                        stringResource(R.string.playback_sensitivity),
                        FailoverSensitivity.entries.map {
                            Choice(it.name, stringResource(sensitivityName(it)), stringResource(sensitivityHint(it)))
                        },
                        settings.sensitivity.name,
                    ) { onEvent(PlaybackSettingsEvent.SetSensitivity(FailoverSensitivity.valueOf(it))) }
                PlaybackPicker.DELAY ->
                    PickerSpec(
                        stringResource(R.string.playback_delay),
                        BkFailoverPolicy.TV_DELAY_OPTIONS_SECONDS.map {
                            Choice(it.toString(), stringResource(R.string.playback_seconds, it))
                        },
                        settings.tvDelaySeconds.toString(),
                    ) { onEvent(PlaybackSettingsEvent.SetDelay(it.toInt())) }
                PlaybackPicker.REMOTE ->
                    PickerSpec(
                        stringResource(R.string.playback_remote),
                        REMOTE_DEBOUNCE_OPTIONS.map {
                            Choice(
                                it.toString(),
                                if (it == 0) stringResource(R.string.playback_remote_off) else "$it ms",
                            )
                        },
                        remoteDebounceMs.toString(),
                    ) { onSetDebounce(it.toInt()) }
            }
        ChoiceDialog(
            title = spec.title,
            choices = spec.choices,
            selectedId = spec.selected,
            onSelect = {
                spec.onPick(it)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
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
            title = stringResource(R.string.playback_preview),
            subtitle = stringResource(R.string.playback_preview_hint),
            onClick = onToggleLivePreview,
            trailing = { Switch(checked = livePreview, onCheckedChange = null) },
        )
        SettingsListItem(
            title = stringResource(R.string.playback_sensitivity),
            subtitle = stringResource(sensitivityHint(settings.sensitivity)),
            onClick = { picker = PlaybackPicker.SENSITIVITY },
            trailing = { Text(stringResource(sensitivityName(settings.sensitivity)), color = c.textPrimary) },
        )
        SettingsListItem(
            title = stringResource(R.string.playback_delay),
            subtitle = stringResource(R.string.playback_delay_hint),
            onClick = { picker = PlaybackPicker.DELAY },
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
            onClick = { picker = PlaybackPicker.REMOTE },
            trailing = {
                Text(
                    if (remoteDebounceMs == 0) stringResource(R.string.playback_remote_off) else "$remoteDebounceMs ms",
                    color = c.textPrimary,
                )
            },
        )
    }
}

private enum class PlaybackPicker { SENSITIVITY, DELAY, REMOTE }

private data class PickerSpec(
    val title: String,
    val choices: List<Choice>,
    val selected: String,
    val onPick: (String) -> Unit,
)

private val REMOTE_DEBOUNCE_OPTIONS = listOf(0, 150, 250, 400)

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
