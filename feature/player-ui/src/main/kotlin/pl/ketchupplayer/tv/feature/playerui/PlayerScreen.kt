package pl.ketchupplayer.tv.feature.playerui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import pl.ketchupplayer.tv.player.engine.PlayerState

@Composable
fun VodPlayerRoute(
    itemId: String,
    fromStart: Boolean,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.startVod(itemId, fromStart) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val title by viewModel.vodTitle.collectAsStateWithLifecycle()
    PlayerScreen(
        ui,
        viewModel.controller,
        viewModel::retry,
        onBack = {
            viewModel.saveNow()
            onBack()
        },
        vodTitle = title,
    )
}

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
    vodTitle: String? = null,
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
    if (vodTitle != null) {
        // Pozycja płynie bez zdarzeń odtwarzacza: odświeżamy ją, dopóki ekran jest otwarty.
        LaunchedEffect(Unit) {
            while (true) {
                controller.poll()
                delay(POLL_MS)
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                osdTick++
                vodTitle != null && !failed && event.type == KeyEventType.KeyDown && handleVodKey(event.key, controller)
            }.focusable(),
    ) {
        controller.player?.let { PlayerSurface(it, Modifier.fillMaxSize()) }
        val name = channelName(ui)
        if (osdVisible && vodTitle != null) {
            VodOsd(vodTitle, playback, Modifier.align(Alignment.BottomCenter))
        } else if (osdVisible && name.isNotEmpty()) {
            Osd(name, Modifier.align(Alignment.TopStart))
        }
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

/** LEWO/PRAWO przewijają o [SEEK_MS], OK i Play/Pauza wstrzymują; true = klawisz obsłużony. */
private fun handleVodKey(
    key: Key,
    controller: PlayerController,
): Boolean {
    val st = controller.state.value
    val duration = st.durationMs ?: Long.MAX_VALUE
    return when (key) {
        Key.DirectionLeft -> controller.seekTo((st.positionMs - SEEK_MS).coerceAtLeast(0)).let { true }
        Key.DirectionRight -> controller.seekTo((st.positionMs + SEEK_MS).coerceAtMost(duration)).let { true }
        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause ->
            (if (st.isPlaying) controller.pause() else controller.resume()).let { true }
        else -> false
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
private fun VodOsd(
    title: String,
    state: PlayerState,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val duration = state.durationMs ?: 0L
    val progress = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(
        modifier.fillMaxWidth().background(c.scrim.copy(alpha = 0.75f)).padding(horizontal = 48.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 24.sp, color = c.textPrimary, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(6.dp).background(c.outline, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(c.accent, RoundedCornerShape(3.dp)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                (
                    if (state.isPlaying) {
                        ""
                    } else {
                        stringResource(
                            R.string.player_paused,
                        ) + " · "
                    }
                ) + clock(state.positionMs) +
                    " / " + clock(duration),
                fontSize = 18.sp,
                color = c.textPrimary,
            )
            Text(stringResource(R.string.player_vod_hint), fontSize = 16.sp, color = c.textSecondary)
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / MS_PER_SECOND
    val h = total / SECONDS_PER_HOUR
    val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val sec = total % SECONDS_PER_MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
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
private const val POLL_MS = 500L
private const val SEEK_MS = 10_000L
private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
