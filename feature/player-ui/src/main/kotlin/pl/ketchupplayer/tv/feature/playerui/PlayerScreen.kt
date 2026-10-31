package pl.ketchupplayer.tv.feature.playerui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.player.engine.PlaybackStatus
import pl.ketchupplayer.tv.player.engine.PlayerController

@Composable
fun PlayerRoute(
    channelId: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    LaunchedEffect(channelId) { viewModel.start(channelId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    PlayerScreen(ui, viewModel.controller, viewModel::retry, onBack, notice)
}

/** Odtwarzacz pełnoekranowy. Dowolny klawisz pokazuje nakładkę z nazwą kanału na [OSD_MS]; BACK wychodzi. */
@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    ui: PlayerUiState,
    controller: PlayerController,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    notice: String? = null,
) {
    BackHandler(onBack = onBack)
    val playback by controller.state.collectAsStateWithLifecycle()
    var osdVisible by remember { mutableStateOf(true) }
    var osdTick by remember { mutableStateOf(0) }
    LaunchedEffect(osdTick) {
        osdVisible = true
        delay(OSD_MS)
        osdVisible = false
    }
    val failed = ui is PlayerUiState.Failed || playback.status == PlaybackStatus.ERROR
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent {
                osdTick++
                false
            }.focusable(),
    ) {
        controller.player?.let { PlayerSurface(it, Modifier.fillMaxSize()) }
        val name = channelName(ui)
        if (osdVisible && name.isNotEmpty()) Osd(name, Modifier.align(Alignment.TopStart))
        if (notice != null) {
            StatusText(
                stringResource(R.string.player_switched, notice),
                Modifier.align(Alignment.BottomCenter).padding(bottom = 27.dp),
            )
        }
        when {
            failed -> ErrorPanel(ui, onRetry, onBack, Modifier.align(Alignment.Center))
            ui is PlayerUiState.Resolving ->
                StatusText(
                    stringResource(R.string.player_loading),
                    Modifier.align(Alignment.Center),
                )
            playback.status == PlaybackStatus.BUFFERING ->
                StatusText(
                    stringResource(R.string.player_buffering),
                    Modifier.align(Alignment.Center),
                )
        }
    }
}

private fun channelName(ui: PlayerUiState): String =
    when (ui) {
        is PlayerUiState.Resolving -> ui.channelName
        is PlayerUiState.Playing -> ui.channelName
        is PlayerUiState.Failed -> ui.channelName
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Osd(
    name: String,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    Box(modifier.padding(horizontal = 48.dp, vertical = 27.dp).background(c.scrim.copy(alpha = 0.7f)).padding(16.dp)) {
        Text(name, fontSize = 24.sp, color = c.textPrimary)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusText(
    text: String,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    Box(modifier.background(c.scrim.copy(alpha = 0.7f)).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(text, fontSize = 22.sp, color = c.textPrimary)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorPanel(
    ui: PlayerUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val unknown = (ui as? PlayerUiState.Failed)?.unknownChannel == true
    Column(
        modifier.background(c.scrim.copy(alpha = 0.85f)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (unknown) R.string.player_error_unknown_channel else R.string.player_error_title),
            fontSize = 24.sp,
            color = c.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!unknown) {
                KetchupButton(
                    stringResource(R.string.player_retry),
                    onRetry,
                    Modifier.requestFocusWhenPlaced(),
                    primary = true,
                )
            }
            KetchupButton(
                stringResource(R.string.player_back),
                onBack,
                if (unknown) Modifier.requestFocusWhenPlaced() else Modifier,
            )
        }
    }
}

private const val OSD_MS = 4_000L
