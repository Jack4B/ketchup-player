package pl.ketchupplayer.tv.feature.playerui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.player.engine.PlayerController
import pl.ketchupplayer.tv.player.engine.TrackInfo

/** Panele opcji odtwarzacza (OSD). */
enum class PlayerPanel { AUDIO, SUBTITLES, SYNC, PICTURE, SPEED, SLEEP, INFO }

enum class AspectMode { FIT, FILL, STRETCH }

enum class SubtitleSize(
    val scale: Float,
) {
    SMALL(0.8f),
    NORMAL(1f),
    LARGE(1.3f),
    ;

    fun next(): SubtitleSize = entries[(ordinal + 1) % entries.size]
}

internal const val AUDIO_STEP_MS = 50
internal const val SUBTITLE_STEP_MS = 100
internal const val MAX_AUDIO_DELAY_MS = 1000
internal const val MAX_SUBTITLE_DELAY_MS = 10_000
internal val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
internal val SLEEP_MINUTES = listOf(0, 15, 30, 60, 90)

/**
 * Prosi o fokus, gdy element jest już podpięty: tuż po pojawieniu się panelu `requestFocus` potrafi rzucić wyjątek
 * (węzeł jeszcze bez położenia), więc ponawiamy przez kilka klatek.
 */
internal suspend fun FocusRequester.requestWhenReady() {
    repeat(FOCUS_RETRIES) {
        if (runCatching { requestFocus() }.isSuccess) return
        androidx.compose.runtime.withFrameNanos { }
    }
}

private const val FOCUS_RETRIES = 10

/** Rząd przycisków pod paskiem OSD; OK otwiera panel, po jego zamknięciu fokus wraca na ten sam przycisk. */
@Composable
fun PlayerChips(
    requesters: Map<PlayerPanel, FocusRequester>,
    onOpen: (PlayerPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels =
        listOf(
            PlayerPanel.AUDIO to R.string.player_chip_audio,
            PlayerPanel.SUBTITLES to R.string.player_chip_subtitles,
            PlayerPanel.SYNC to R.string.player_chip_sync,
            PlayerPanel.PICTURE to R.string.player_chip_picture,
            PlayerPanel.SPEED to R.string.player_chip_speed,
            PlayerPanel.SLEEP to R.string.player_chip_sleep,
            PlayerPanel.INFO to R.string.player_chip_info,
        )
    Row(modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEach { (panel, label) ->
            Chip(
                stringResource(label),
                { onOpen(panel) },
                requesters[panel]?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    FocusItem(onClick, modifier, container = c.scrim.copy(alpha = 0.7f), focusScale = 1f) {
        Text(
            text,
            fontSize = 16.sp,
            color = c.textPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Panel z prawej: lista opcji wybranego obszaru. BACK zamyka (obsługuje ekran). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerPanelView(
    panel: PlayerPanel,
    tracks: TrackInfo,
    controller: PlayerController,
    state: PanelState,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    val firstFocus = remember(panel) { FocusRequester() }
    LaunchedEffect(panel) { firstFocus.requestWhenReady() }
    Column(
        modifier
            .width(PANEL_WIDTH)
            .fillMaxHeight()
            .background(c.scrim.copy(alpha = 0.88f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(panelTitle(panel), fontSize = 20.sp, color = c.textPrimary)
        when (panel) {
            PlayerPanel.AUDIO -> AudioList(tracks, controller, firstFocus)
            PlayerPanel.SUBTITLES -> SubtitleList(tracks, controller, state, firstFocus)
            PlayerPanel.SYNC -> SyncList(tracks, controller, firstFocus)
            PlayerPanel.PICTURE -> PictureList(state, firstFocus)
            PlayerPanel.SPEED -> SpeedList(tracks, controller, firstFocus)
            PlayerPanel.SLEEP -> SleepList(state, firstFocus)
            PlayerPanel.INFO -> InfoList(tracks, firstFocus)
        }
    }
}

/** Ustawienia widoku trzymane przez ekran (nie przez kontroler). */
class PanelState(
    val aspect: AspectMode,
    val subtitleSize: SubtitleSize,
    val sleepMinutes: Int,
    val onAspect: (AspectMode) -> Unit,
    val onSubtitleSize: (SubtitleSize) -> Unit,
    val onSleep: (Int) -> Unit,
)

@Composable
private fun panelTitle(panel: PlayerPanel): String =
    stringResource(
        when (panel) {
            PlayerPanel.AUDIO -> R.string.player_chip_audio
            PlayerPanel.SUBTITLES -> R.string.player_chip_subtitles
            PlayerPanel.SYNC -> R.string.player_chip_sync
            PlayerPanel.PICTURE -> R.string.player_chip_picture
            PlayerPanel.SPEED -> R.string.player_chip_speed
            PlayerPanel.SLEEP -> R.string.player_chip_sleep
            PlayerPanel.INFO -> R.string.player_chip_info
        },
    )

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionRow(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onStep: ((Int) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    val keys =
        if (onStep == null) {
            Modifier
        } else {
            Modifier.onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (e.key) {
                        Key.DirectionLeft -> true.also { onStep(-1) }
                        Key.DirectionRight -> true.also { onStep(1) }
                        else -> false
                    }
                }
            }
        }
    FocusItem(
        onClick,
        modifier.fillMaxWidth().then(keys),
        selected = selected,
        container = if (selected) c.surface3 else null,
        outlined = false,
        focusScale = 1f,
    ) {
        Text(
            (if (selected) "● " else "○ ") + text,
            fontSize = 17.sp,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 14.sp, color = KetchupTheme.colors.textSecondary, modifier = Modifier.padding(4.dp))
}

@Composable
private fun AudioList(
    tracks: TrackInfo,
    controller: PlayerController,
    first: FocusRequester,
) {
    if (tracks.audio.isEmpty()) return Hint(stringResource(R.string.player_no_tracks))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(tracks.audio, key = { it.id }) { t ->
            OptionRow(t.label, t.selected, if (t.selected) Modifier.focusRequester(first) else Modifier) {
                controller.selectAudio(t.id)
            }
        }
    }
    if (tracks.audio.none { it.selected }) LaunchedEffect(Unit) { first.requestWhenReady() }
}

@Composable
private fun SubtitleList(
    tracks: TrackInfo,
    controller: PlayerController,
    state: PanelState,
    first: FocusRequester,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "off") {
            OptionRow(
                stringResource(R.string.player_subtitles_off),
                !tracks.textEnabled,
                Modifier.focusRequester(first),
            ) {
                controller.selectText(null)
            }
        }
        items(tracks.text, key = { it.id }) { t -> OptionRow(t.label, t.selected) { controller.selectText(t.id) } }
        item(key = "size") {
            OptionRow(
                stringResource(R.string.player_subtitles_size, sizeName(state.subtitleSize)),
                false,
                onStep = { state.onSubtitleSize(state.subtitleSize.next()) },
            ) { state.onSubtitleSize(state.subtitleSize.next()) }
        }
    }
}

@Composable
private fun sizeName(size: SubtitleSize): String =
    stringResource(
        when (size) {
            SubtitleSize.SMALL -> R.string.player_size_small
            SubtitleSize.NORMAL -> R.string.player_size_normal
            SubtitleSize.LARGE -> R.string.player_size_large
        },
    )

private fun seconds(ms: Int): String = (if (ms > 0) "+" else "") + "%.2f s".format(ms / 1000f)

@Composable
private fun SyncList(
    tracks: TrackInfo,
    controller: PlayerController,
    first: FocusRequester,
) {
    Hint(stringResource(R.string.player_sync_hint))
    OptionRow(
        stringResource(R.string.player_sync_audio, seconds(tracks.audioDelayMs)),
        false,
        Modifier.focusRequester(first),
        onStep = { dir ->
            controller.setAudioDelayMs(
                (tracks.audioDelayMs + dir * AUDIO_STEP_MS).coerceIn(-MAX_AUDIO_DELAY_MS, MAX_AUDIO_DELAY_MS),
            )
        },
    ) { controller.setAudioDelayMs(0) }
    OptionRow(
        stringResource(R.string.player_sync_subtitles, seconds(tracks.subtitleDelayMs)),
        false,
        onStep = { dir ->
            controller.setSubtitleDelayMs(
                (tracks.subtitleDelayMs + dir * SUBTITLE_STEP_MS).coerceIn(0, MAX_SUBTITLE_DELAY_MS),
            )
        },
    ) { controller.setSubtitleDelayMs(0) }
}

@Composable
private fun PictureList(
    state: PanelState,
    first: FocusRequester,
) {
    val items =
        listOf(
            AspectMode.FIT to R.string.player_aspect_fit,
            AspectMode.FILL to R.string.player_aspect_fill,
            AspectMode.STRETCH to R.string.player_aspect_stretch,
        )
    items.forEach { (mode, label) ->
        OptionRow(
            stringResource(label),
            state.aspect == mode,
            if (state.aspect ==
                mode
            ) {
                Modifier.focusRequester(first)
            } else {
                Modifier
            },
        ) {
            state.onAspect(mode)
        }
    }
}

@Composable
private fun SpeedList(
    tracks: TrackInfo,
    controller: PlayerController,
    first: FocusRequester,
) {
    SPEEDS.forEach { s ->
        val selected = kotlin.math.abs(tracks.speed - s) < SPEED_EPSILON
        OptionRow(
            stringResource(R.string.player_speed_value, "%.2f".format(s).trimEnd('0').trimEnd(',', '.')),
            selected,
            if (selected) Modifier.focusRequester(first) else Modifier,
        ) {
            controller.setSpeed(s)
        }
    }
}

private const val SPEED_EPSILON = 0.01f

@Composable
private fun SleepList(
    state: PanelState,
    first: FocusRequester,
) {
    SLEEP_MINUTES.forEach { m ->
        val label =
            if (m ==
                0
            ) {
                stringResource(R.string.player_sleep_off)
            } else {
                stringResource(R.string.player_sleep_minutes, m)
            }
        OptionRow(
            label,
            state.sleepMinutes == m,
            if (state.sleepMinutes ==
                m
            ) {
                Modifier.focusRequester(first)
            } else {
                Modifier
            },
        ) { state.onSleep(m) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfoList(
    tracks: TrackInfo,
    first: FocusRequester,
) {
    val c = KetchupTheme.colors
    val s = tracks.summary
    val unknown = stringResource(R.string.player_info_unknown)
    FocusItem({}, Modifier.fillMaxWidth().focusRequester(first), outlined = false, focusScale = 1f) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.player_info_video, s.video ?: unknown),
                fontSize = 17.sp,
                color = c.textPrimary,
            )
            Text(
                stringResource(R.string.player_info_audio, s.audio ?: unknown),
                fontSize = 17.sp,
                color = c.textPrimary,
            )
            s.bitrateKbps?.let {
                Text(
                    stringResource(R.string.player_info_bitrate, it),
                    fontSize = 17.sp,
                    color = c.textPrimary,
                )
            }
        }
    }
}

private val PANEL_WIDTH = 380.dp

/** Napisy rysowane przez nas (kolor, rozmiar, tło): tekst z kontrolera po uwzględnieniu opóźnienia. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubtitleOverlay(
    text: String,
    size: SubtitleSize,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return
    Text(
        text,
        fontSize = (SUBTITLE_BASE_SP * size.scale).sp,
        color = androidx.compose.ui.graphics.Color.White,
        modifier =
            modifier
                .background(
                    androidx.compose.ui.graphics.Color.Black
                        .copy(alpha = 0.55f),
                ).padding(horizontal = 10.dp, vertical = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private const val SUBTITLE_BASE_SP = 26f
