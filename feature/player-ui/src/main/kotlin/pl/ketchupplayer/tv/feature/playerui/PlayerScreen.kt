package pl.ketchupplayer.tv.feature.playerui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
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
    val info by viewModel.vodInfo.collectAsStateWithLifecycle()
    val clock by viewModel.clockEnabled.collectAsStateWithLifecycle()
    PlayerScreen(
        ui,
        viewModel.controller,
        viewModel::retry,
        onBack = {
            viewModel.saveNow()
            onBack()
        },
        vodTitle = title,
        showClock = clock,
        vodInfo = info,
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
    val clock by viewModel.clockEnabled.collectAsStateWithLifecycle()
    PlayerScreen(ui, viewModel.controller, viewModel::retry, onBack, notice, showClock = clock)
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
    showClock: Boolean = true,
    vodInfo: VodInfoUi? = null,
) {
    BackHandler(onBack = onBack)
    // Po wyjściu z aplikacji (HOME, inna aplikacja, radio) dźwięk nie może grać w tle: pauza przy ON_STOP,
    // a po powrocie wznowienie tylko wtedy, gdy wcześniej coś grało.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        var resumeOnStart = false
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        resumeOnStart = controller.state.value.isPlaying
                        controller.pause()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        if (resumeOnStart) controller.resume()
                        resumeOnStart = false
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val playback by controller.state.collectAsStateWithLifecycle()
    val tracks by controller.tracks.collectAsStateWithLifecycle()
    val subtitle by controller.subtitle.collectAsStateWithLifecycle()
    var osdVisible by remember { mutableStateOf(true) }
    var osdTick by remember { mutableStateOf(0) }
    var chipsOpen by remember { mutableStateOf(false) }
    // Karta z opisem filmu: na starcie na kilka sekund, potem GÓRA albo INFO ją przywołuje.
    var infoOpen by remember { mutableStateOf(false) }
    LaunchedEffect(vodInfo?.plot != null) {
        if (vodInfo?.plot != null) {
            infoOpen = true
            delay(INFO_AUTOHIDE_MS)
            infoOpen = false
        }
    }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var aspect by rememberSaveable { mutableStateOf(AspectMode.FIT) }
    var subtitleSize by rememberSaveable { mutableStateOf(SubtitleSize.NORMAL) }
    var sleepMinutes by rememberSaveable { mutableStateOf(0) }
    var sleepAtMs by remember { mutableLongStateOf(0L) }
    val rootFocus = remember { FocusRequester() }
    val chipFocus = remember { PlayerPanel.entries.associateWith { FocusRequester() } }
    LaunchedEffect(osdTick) {
        osdVisible = true
        delay(OSD_MS)
        // Przy otwartych opcjach pasek zostaje: zamykają je BACK albo GÓRA.
        if (!chipsOpen && panel == null) osdVisible = false
    }
    LaunchedEffect(Unit) { rootFocus.requestWhenReady() }
    LaunchedEffect(chipsOpen) { if (chipsOpen) chipFocus.getValue(PlayerPanel.AUDIO).requestWhenReady() }
    LaunchedEffect(sleepAtMs) {
        if (sleepAtMs > 0) {
            delay((sleepAtMs - System.currentTimeMillis()).coerceAtLeast(0))
            controller.pause()
            onBack()
        }
    }
    BackHandler(enabled = chipsOpen && panel == null) {
        chipsOpen = false
        runCatching { rootFocus.requestFocus() }
    }
    BackHandler(enabled = panel != null) {
        val closed = panel
        panel = null
        // Fokus wraca na przycisk, z którego otwarto panel (przywracanie fokusu jak w TiViMate).
        closed?.let { runCatching { chipFocus.getValue(it).requestFocus() } }
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
                val down = event.type == KeyEventType.KeyDown
                when {
                    failed || !down -> false
                    panel != null -> false
                    chipsOpen ->
                        (event.key == Key.DirectionUp).also {
                            if (it) {
                                chipsOpen = false
                                runCatching { rootFocus.requestFocus() }
                            }
                        }
                    event.key == Key.DirectionDown || event.key == Key.Menu -> true.also { chipsOpen = true }
                    vodTitle != null && (event.key == Key.DirectionUp || event.key == Key.Info) ->
                        true.also { infoOpen = !infoOpen }
                    infoOpen && event.key == Key.Back -> true.also { infoOpen = false }
                    else -> vodTitle != null && handleVodKey(event.key, controller, event.nativeKeyEvent.repeatCount)
                }
            }.focusRequester(rootFocus)
            .focusable(),
    ) {
        controller.player?.let { p ->
            val presentation = rememberPresentationState(p)
            val scale =
                when (aspect) {
                    AspectMode.FIT -> ContentScale.Fit
                    AspectMode.FILL -> ContentScale.Crop
                    AspectMode.STRETCH -> ContentScale.FillBounds
                }
            PlayerSurface(p, Modifier.fillMaxSize().resizeWithContentScale(scale, presentation.videoSizeDp))
        }
        if (showClock) PlayerClock(Modifier.align(Alignment.TopEnd))
        if (tracks.textEnabled) {
            SubtitleOverlay(
                subtitle,
                subtitleSize,
                Modifier
                    .align(
                        Alignment.BottomCenter,
                    ).padding(bottom = if (osdVisible) SUBTITLE_LIFT else SUBTITLE_BOTTOM),
            )
        }
        val name = channelName(ui)
        if (infoOpen && vodInfo != null) {
            VodInfoCard(vodInfo, Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = INFO_BOTTOM))
        }
        if (osdVisible && vodTitle != null) {
            VodOsd(vodTitle, vodInfo, playback, Modifier.align(Alignment.BottomCenter))
        } else if (osdVisible && name.isNotEmpty()) {
            Osd(name, Modifier.align(Alignment.TopStart))
        }
        if (chipsOpen) {
            PlayerChips(
                chipFocus,
                { panel = it },
                Modifier.align(Alignment.BottomStart).padding(
                    bottom =
                        if (vodTitle !=
                            null
                        ) {
                            CHIPS_ABOVE_VOD
                        } else {
                            CHIPS_BOTTOM
                        },
                ),
            )
        }
        panel?.let { p ->
            PlayerPanelView(
                p,
                tracks,
                controller,
                PanelState(
                    aspect,
                    subtitleSize,
                    sleepMinutes,
                    onAspect = { aspect = it },
                    onSubtitleSize = { subtitleSize = it },
                    onSleep = { m ->
                        sleepMinutes = m
                        sleepAtMs = if (m == 0) 0L else System.currentTimeMillis() + m * MS_PER_MINUTE
                    },
                ),
                Modifier.align(Alignment.CenterEnd),
            )
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

/**
 * Krok przewijania zależny od tego, jak długo klawisz jest trzymany (liczba powtórzeń): pojedyncze naciśnięcie
 * i początek trzymania 10 s, potem 30 s, 60 s i 90 s, żeby dojść do połowy filmu w kilka sekund.
 */
internal fun seekStepMs(repeatCount: Int): Long =
    when {
        repeatCount < SEEK_FAST_AFTER -> SEEK_MS
        repeatCount < SEEK_FASTER_AFTER -> SEEK_MS * 3
        repeatCount < SEEK_FASTEST_AFTER -> SEEK_MS * 6
        else -> SEEK_MS * 9
    }

private const val SEEK_FAST_AFTER = 6
private const val SEEK_FASTER_AFTER = 14
private const val SEEK_FASTEST_AFTER = 28

/** LEWO/PRAWO przewijają (przy trzymaniu coraz szybciej), OK i Play/Pauza wstrzymują; true = klawisz obsłużony. */
private fun handleVodKey(
    key: Key,
    controller: PlayerController,
    repeatCount: Int,
): Boolean {
    val seek = seekStepMs(repeatCount)
    val st = controller.state.value
    val duration = st.durationMs ?: Long.MAX_VALUE
    return when (key) {
        Key.DirectionLeft -> controller.seekTo((st.positionMs - seek).coerceAtLeast(0)).let { true }
        Key.DirectionRight -> controller.seekTo((st.positionMs + seek).coerceAtMost(duration)).let { true }
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
    info: VodInfoUi?,
    state: PlayerState,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val duration = state.durationMs ?: 0L
    val progress = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    // Dolny pas: gradient od przezroczystości do ciemności zamiast płaskiego prostokąta; tytuł, wiersz meta, pasek z
    // „gałką”, czasy i podpowiedzi klawiszy w pigułkach.
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = OSD_SCRIM))))
            .padding(horizontal = 32.dp)
            .padding(top = 48.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        if (!info?.meta.isNullOrBlank()) Text(info?.meta.orEmpty(), fontSize = 17.sp, color = c.textSecondary)
        Box(Modifier.fillMaxWidth().height(THUMB)) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(progress.coerceAtLeast(MIN_WEIGHT)))
                Box(Modifier.size(THUMB).background(Color.White, CircleShape))
                Spacer(Modifier.weight((1f - progress).coerceAtLeast(MIN_WEIGHT)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                (if (state.isPlaying) "" else stringResource(R.string.player_paused) + " · ") + clock(state.positionMs),
                fontSize = 18.sp,
                color = Color.White,
            )
            Text(
                stringResource(R.string.player_remaining, clock((duration - state.positionMs).coerceAtLeast(0))),
                fontSize = 18.sp,
                color = Color.White,
            )
        }
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
                            .background(
                                Color.White.copy(alpha = PILL),
                                RoundedCornerShape(PILL_R),
                            ).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Karta „Co to za film”: tytuł, wiersz meta, opis (do 6 linii), obsada i źródło opisu, na szklanym tle. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VodInfoCard(
    info: VodInfoUi,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    Column(
        modifier
            .width(
                INFO_WIDTH,
            ).background(Color.Black.copy(alpha = INFO_SCRIM), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.player_info_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = c.accent,
        )
        Text(
            info.plot ?: stringResource(R.string.player_info_none),
            fontSize = 16.sp,
            color = Color.White,
            maxLines = INFO_PLOT_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        info.cast?.let {
            Text(
                stringResource(R.string.player_info_cast, it),
                fontSize = 14.sp,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        info.source?.let { Text(it, fontSize = 12.sp, color = c.textSecondary) }
    }
}

private val INFO_WIDTH = 560.dp
private val INFO_BOTTOM = 190.dp
private val THUMB = 18.dp
private val BAR = 6.dp
private const val INFO_AUTOHIDE_MS = 7_000L
private const val INFO_PLOT_LINES = 6
private const val INFO_SCRIM = 0.72f
private const val OSD_SCRIM = 0.88f
private const val BAR_TRACK = 0.28f
private const val PILL = 0.16f
private val PILL_R = 14.dp
private const val MIN_WEIGHT = 0.0001f

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
    Column(
        modifier.padding(horizontal = 24.dp, vertical = 14.dp).background(c.scrim.copy(alpha = 0.7f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(name, fontSize = 24.sp, color = c.textPrimary)
        Text(stringResource(R.string.player_hint_options), fontSize = 14.sp, color = c.textSecondary)
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
private const val MS_PER_MINUTE = 60_000L
private val SUBTITLE_LIFT = 130.dp
private val SUBTITLE_BOTTOM = 40.dp
private val CHIPS_ABOVE_VOD = 118.dp
private val CHIPS_BOTTOM = 24.dp
private const val POLL_MS = 500L
private const val SEEK_MS = 10_000L
private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/** Zegar w prawym górnym rogu: biały, zawsze czytelny na jasnym i ciemnym obrazie (cień i półprzezroczyste tło). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerClock(modifier: Modifier) {
    val time by androidx.compose.runtime.produceState(clockNow()) {
        while (true) {
            value = clockNow()
            delay(CLOCK_REFRESH_MS)
        }
    }
    Text(
        time,
        fontSize = CLOCK_SP.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = Color.White,
        style =
            androidx.compose.ui.text.TextStyle(
                shadow =
                    androidx.compose.ui.graphics
                        .Shadow(
                            Color.Black,
                            androidx.compose.ui.geometry
                                .Offset(2f, 2f),
                            CLOCK_SHADOW,
                        ),
            ),
        modifier =
            modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .background(Color.Black.copy(alpha = CLOCK_SCRIM_ALPHA), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

private fun clockNow(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

private const val CLOCK_REFRESH_MS = 10_000L
private const val CLOCK_SP = 28
private const val CLOCK_SHADOW = 4f
private const val CLOCK_SCRIM_ALPHA = 0.35f
