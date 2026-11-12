package pl.ketchupplayer.tv.feature.playerui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme

/**
 * Tryb dotyku odtwarzacza. Włącza go dotknięcie ekranu palcem albo rysikiem (myszy nie liczymy), wyłącza dowolny
 * klawisz poza BACK i głośnością. Na TV, gdzie nikt nie dotyka ekranu, stan zostaje `false`, więc nic się nie zmienia:
 * przyciski dotykowe nie są nawet komponowane, a obsługa pilota ([PlayerScreen]) jest nietknięta.
 */
@Stable
internal class TouchMode {
    var active by mutableStateOf(false)
        private set
    private var swallowTap = false

    /** Dotknięcie ekranu. Zwraca `true`, gdy właśnie włączyło tryb dotyku (to dotknięcie tylko pokazuje OSD). */
    fun onTouchDown(): Boolean {
        val entering = !active
        active = true
        swallowTap = entering
        return entering
    }

    fun onKey() {
        active = false
    }

    /** Pierwsze stuknięcie po wejściu w tryb dotyku nie chowa OSD, które zdążyło się pokazać. */
    fun consumeSwallowedTap(): Boolean = swallowTap.also { swallowTap = false }
}

private val KEYS_KEEPING_TOUCH = setOf(Key.Back, Key.VolumeUp, Key.VolumeDown, Key.VolumeMute)

/**
 * Śledzi rodzaj wejścia: dotyk włącza [TouchMode] (i woła [onEnter] tylko przy wejściu), klawisz wyłącza.
 * Niczego nie konsumuje i niczego nie zmienia w obsłudze klawiszy: to osobny, wcześniejszy `onPreviewKeyEvent`.
 */
internal fun Modifier.trackTouchMode(
    mode: TouchMode,
    onEnter: () -> Unit,
): Modifier =
    pointerInput(mode) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val touched =
                    event.type == PointerEventType.Press &&
                        event.changes.any { it.type == PointerType.Touch || it.type == PointerType.Stylus }
                if (touched && mode.onTouchDown()) onEnter()
            }
        }
    }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key !in KEYS_KEEPING_TOUCH) mode.onKey()
        false
    }

/** Gesty na pasku postępu: ułamek szerokości pod palcem (0..1). Pozycja zmienia się dopiero w [onCommit]. */
internal class TouchSeek(
    val onScrub: (Float) -> Unit,
    val onCommit: (Float) -> Unit,
    val onCancel: () -> Unit,
)

internal fun fractionOf(
    x: Float,
    width: Int,
): Float = if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)

/** Stuknięcie przewija od razu; przeciąganie pokazuje cel i przewija raz, po puszczeniu palca. */
@Composable
internal fun Modifier.touchSeekGestures(seek: TouchSeek): Modifier {
    val current by rememberUpdatedState(seek)
    return pointerInput(Unit) {
        detectTapGestures(onTap = { offset -> current.onCommit(fractionOf(offset.x, size.width)) })
    }.pointerInput(Unit) {
        var last = 0f
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                last = fractionOf(offset.x, size.width)
                current.onScrub(last)
            },
            onDragEnd = { current.onCommit(last) },
            onDragCancel = { current.onCancel() },
            onHorizontalDrag = { change, _ ->
                last = fractionOf(change.position.x, size.width)
                current.onScrub(last)
                change.consume()
            },
        )
    }
}

/**
 * Przycisk dotykowy bez węzła fokusu (fokus zostaje na korzeniu ekranu, więc pilot działa tak samo). Jedna pozycja
 * w drzewie dostępności z opisem [description]; [tag] służy testom E2E.
 */
@Composable
private fun TouchButton(
    description: String,
    tag: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val current by rememberUpdatedState(onActivate)
    Box(
        modifier
            .testTag(tag)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    current()
                    true
                }
            }.pointerInput(Unit) { detectTapGestures(onTap = { current() }) },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Środek ekranu: −10 s, odtwórz/pauza, +10 s. Na żywo (brak przewijania) tylko odtwórz/pauza. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TouchTransport(
    isPlaying: Boolean,
    seekable: Boolean,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(TRANSPORT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (seekable) {
            SkipButton(-SKIP_SECONDS, stringResource(R.string.player_touch_back10), TAG_BACK10, onSkip)
        }
        TouchButton(
            stringResource(if (isPlaying) R.string.player_a11y_pause else R.string.player_a11y_play),
            TAG_PLAY_PAUSE,
            onPlayPause,
            Modifier.size(PLAY_SIZE),
        ) { PlayPauseBadge(isPlaying, PLAY_SIZE) }
        if (seekable) {
            SkipButton(SKIP_SECONDS, stringResource(R.string.player_touch_forward10), TAG_FORWARD10, onSkip)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SkipButton(
    seconds: Int,
    description: String,
    tag: String,
    onSkip: (Int) -> Unit,
) {
    val sign = if (seconds < 0) "−" else "+"
    TouchButton(description, tag, { onSkip(seconds) }, Modifier.defaultMinSize(SKIP_W, SKIP_H)) {
        Text(
            "$sign${kotlin.math.abs(seconds)} s",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier =
                Modifier
                    .background(Color.White.copy(alpha = SURFACE), RoundedCornerShape(SKIP_H / 2))
                    .border(1.dp, Color.White.copy(alpha = EDGE), RoundedCornerShape(SKIP_H / 2))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/**
 * Górny pas: przycisk wyjścia i (na żywo) nazwa kanału. Wyjście robi to samo co BACK.
 *
 * Odsunięty od pasków systemu i wycięcia aparatu (`safeDrawing`): aplikacja celuje w SDK 35, więc na telefonie
 * z Androidem 15 rysuje pod paskiem stanu, a w poziomie pod wycięciem. Bez odsunięcia przycisk w lewym górnym rogu
 * leżał pod paskiem stanu albo aparatem i stuknięcie trafiało w system, nie w przycisk. Na TV odsunięcie jest zerowe.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TouchTopBar(
    channelName: String?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** Odsunięcie od pasków systemu; testy podają stałe wartości, aplikacja bierze je z okna. */
    insets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    Row(
        modifier
            .windowInsetsPadding(insets)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TouchButton(
            stringResource(R.string.player_touch_exit),
            TAG_EXIT,
            onExit,
            Modifier.defaultMinSize(minWidth = EXIT_W, minHeight = EXIT_H),
        ) {
            Text(
                "←  " + stringResource(R.string.player_back),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = EXIT_SCRIM), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = EDGE), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (!channelName.isNullOrEmpty()) {
            Text(
                channelName,
                fontSize = 22.sp,
                color = KetchupTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal const val TAG_PLAY_PAUSE = "player_touch_play_pause"
internal const val TAG_BACK10 = "player_touch_back10"
internal const val TAG_FORWARD10 = "player_touch_forward10"
internal const val TAG_EXIT = "player_touch_exit"
internal const val TAG_SEEKBAR = "player_touch_seekbar"

internal const val SKIP_SECONDS = 10
private val PLAY_SIZE: Dp = 72.dp
private val TRANSPORT_GAP = 24.dp
private val SKIP_W = 72.dp
private val SKIP_H = 56.dp
private val EXIT_W = 96.dp
private val EXIT_H = 48.dp
private const val SURFACE = 0.16f
private const val EDGE = 0.28f
private const val EXIT_SCRIM = 0.55f
