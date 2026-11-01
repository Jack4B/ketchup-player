package pl.ketchupplayer.tv.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Czy ekran jest osadzony w [AppShell] (wtedy tło, marginesy i nagłówek z zegarem daje powłoka). */
val LocalInShell = staticCompositionLocalOf { false }

/** Przenosi fokus na szynę (BACK z treści sekcji); dostarcza [AppShell]. */
val LocalFocusRail = staticCompositionLocalOf<() -> Unit> { {} }

/** Pozycja szyny nawigacji; [badge] = zielona kropka (nowe odcinki). */
data class RailEntry(
    val id: String,
    val label: String,
    val icon: RailIcon,
    val badge: Boolean = false,
)

/** Tło aplikacji per motyw (makiety v3): Granat z gradientem, Czerń płaska, Bordo z winietą. */
@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = KetchupTheme.colors
    val brush =
        when (c.theme) {
            ThemeId.GRANAT ->
                Brush.linearGradient(
                    listOf(Color(GRANAT_TOP), Color(GRANAT_BOTTOM)),
                    start = Offset(0f, 0f),
                    end = Offset(GRADIENT_DX, GRADIENT_DY),
                )
            ThemeId.CZERN -> Brush.linearGradient(listOf(Color.Black, Color.Black))
            ThemeId.BORDO -> Brush.radialGradient(listOf(Color(BORDO_CENTER), Color(BORDO_EDGE)), radius = BORDO_RADIUS)
        }
    Box(modifier.fillMaxSize().background(c.background).background(brush), content = content)
}

private const val GRANAT_TOP = 0xFF0F2A4A
private const val GRANAT_BOTTOM = 0xFF07121F
private const val GRADIENT_DX = 900f
private const val GRADIENT_DY = 1900f
private const val BORDO_CENTER = 0xFF5A2A2E
private const val BORDO_EDGE = 0xFF24100F
private const val BORDO_RADIUS = 1800f

/** Panel treści wg motywu: Granat półprzezroczysty, Czerń tylko z obrysem, Bordo pełny i mocno zaokrąglony. */
@Composable
fun Modifier.panel(): Modifier {
    val c = KetchupTheme.colors
    val shape = RoundedCornerShape(c.cornerRadius)
    return when (c.theme) {
        ThemeId.GRANAT -> this.background(c.surface2.copy(alpha = GRANAT_PANEL_ALPHA), shape)
        ThemeId.CZERN -> this.border(1.dp, c.outline, shape)
        ThemeId.BORDO -> this.background(c.surface2, shape)
    }
}

private const val GRANAT_PANEL_ALPHA = 0.72f

private val RAIL_COLLAPSED = 52.dp
private val RAIL_EXPANDED = 168.dp
private val CONTENT_GAP = 20.dp

/**
 * Powłoka aplikacji (makiety v3): szyna nawigacji po lewej (ikony, po fokusie rozwija się z podpisami), treść obok.
 * LEWO z pierwszej kolumny treści trafia w szynę (na aktywną pozycję), BACK z treści też; BACK z szyny: [onRailBack].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppShell(
    entries: List<RailEntry>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onRailBack: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val requesters = remember(entries) { entries.associate { it.id to FocusRequester() } }
    var railFocused by remember { mutableStateOf(false) }
    val toRail: () -> Unit = {
        (requesters[selectedId] ?: requesters.values.firstOrNull())?.let { runCatching { it.requestFocus() } }
    }
    BackHandler(enabled = !railFocused, onBack = toRail)
    BackHandler(enabled = railFocused, onBack = onRailBack)
    ThemedBackground(modifier) {
        CompositionLocalProvider(LocalInShell provides true, LocalFocusRail provides toRail) {
            Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = RAIL_COLLAPSED + CONTENT_GAP),
                    content = content,
                )
                NavRail(
                    entries,
                    selectedId,
                    onSelect,
                    requesters,
                    avatar,
                    Modifier.align(Alignment.CenterStart).onFocusChanged { railFocused = it.hasFocus }.zIndex(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavRail(
    entries: List<RailEntry>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    requesters: Map<String, FocusRequester>,
    avatar: @Composable () -> Unit,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        if (expanded) RAIL_EXPANDED else RAIL_COLLAPSED,
        tween(RAIL_ANIMATION_MS),
        label = "rail",
    )
    val shape = RoundedCornerShape(c.cornerRadius)
    val railFill =
        when (c.theme) {
            ThemeId.GRANAT ->
                Modifier
                    .background(
                        Color(if (expanded) RAIL_GRANAT_OPEN else RAIL_GRANAT),
                        shape,
                    ).border(1.dp, c.outline, shape)
            ThemeId.CZERN -> Modifier.border(1.dp, c.outline, shape)
            ThemeId.BORDO -> Modifier.background(Color(RAIL_BORDO), shape)
        }
    Column(
        modifier
            .width(
                width,
            ).fillMaxHeight()
            .then(railFill)
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(RAIL_AVATAR_BOX), contentAlignment = Alignment.Center) {
            Box(Modifier.size(RAIL_AVATAR).clip(CircleShape), contentAlignment = Alignment.Center) { avatar() }
        }
        val top = entries.filter { it.icon != RailIcon.SETTINGS }
        top.forEach { RailItem(it, it.id == selectedId, expanded, requesters.getValue(it.id)) { onSelect(it.id) } }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(c.outline),
        )
        entries.filter { it.icon == RailIcon.SETTINGS }.forEach {
            RailItem(it, it.id == selectedId, expanded, requesters.getValue(it.id)) { onSelect(it.id) }
        }
    }
}

private const val RAIL_GRANAT = 0xD9092C44 // rgba(9,24,44,.85)
private const val RAIL_GRANAT_OPEN = 0xFF092C44
private const val RAIL_BORDO = 0xFF4A252A
private const val RAIL_ANIMATION_MS = 160
private val RAIL_AVATAR = 40.dp
private val RAIL_AVATAR_BOX = 52.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RailItem(
    entry: RailEntry,
    selected: Boolean,
    expanded: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((c.cornerRadius / 2).coerceAtMost(14.dp))
    val tint = if (selected || focused) c.accent else c.textSecondary
    Row(
        Modifier
            .padding(horizontal = 6.dp)
            .fillMaxWidth()
            .height(RAIL_ITEM_HEIGHT)
            // Spoza szyny fokus przyjmuje tylko aktywna pozycja (makiety v3, NOTATKI §5); w środku wszystkie.
            .focusProperties { canFocus = expanded || selected }
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) {
                contentDescription = entry.label
                this.selected = selected
            }.then(if (selected) Modifier.background(c.surface3, shape) else Modifier)
            .then(if (focused) Modifier.border(2.dp, c.accent, shape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ).padding(start = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            RailIconView(entry.icon, tint)
            if (entry.badge) {
                Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(c.newEpisode, CircleShape))
            }
        }
        if (expanded) {
            Text(
                entry.label,
                fontSize = 15.sp,
                color = if (selected || focused) c.textPrimary else c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private val RAIL_ITEM_HEIGHT = 40.dp

/** Nagłówek ekranu w powłoce: tytuł, opcjonalne zakładki, po prawej zegar. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ShellHeader(
    title: String,
    modifier: Modifier = Modifier,
    tabs: (@Composable () -> Unit)? = null,
) {
    val c = KetchupTheme.colors
    Row(modifier.fillMaxWidth().height(HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
        if (tabs != null) {
            Spacer(Modifier.width(16.dp))
            tabs()
        }
        Spacer(Modifier.weight(1f))
        ClockText()
    }
}

private val HEADER_HEIGHT = 40.dp

/** Zegar HH:mm w nagłówku; odświeża się co kilka sekund. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ClockText(modifier: Modifier = Modifier) {
    val c = KetchupTheme.colors
    val text by produceState(clockNow()) {
        while (true) {
            value = clockNow()
            delay(CLOCK_TICK_MS)
        }
    }
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = c.textPrimary, modifier = modifier)
}

private const val CLOCK_TICK_MS = 5_000L

private fun clockNow(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/** Ekran w powłoce: nagłówek + treść. Marginesy i tło daje [AppShell]. */
@Composable
fun ShellScreen(
    title: String,
    modifier: Modifier = Modifier,
    tabs: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShellHeader(title, tabs = tabs)
        content()
    }
}
