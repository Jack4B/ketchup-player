@file:Suppress("MatchingDeclarationName")

package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Jedna pozycja listy wyboru: [id] zapisywane w ustawieniach, [label] i opcjonalny [hint] dla użytkownika. */
data class Choice(
    val id: String,
    val label: String,
    val hint: String? = null,
)

/**
 * Lista wyboru jako okno (zamiast przełączania kolejnych wartości klawiszem OK): zaznaczona pozycja ma ptaszek ✓,
 * fokus startuje na niej, OK wybiera i zamyka, BACK zamyka bez zmiany.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChoiceDialog(
    title: String,
    choices: List<Choice>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = KetchupTheme.colors
    val selectedIndex = choices.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(FOCUS_RETRIES) {
            withFrameNanos { }
            if (runCatching { focus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(DIALOG_WIDTH)
                    .heightIn(max = DIALOG_MAX_HEIGHT)
                    .background(
                        c.background.copy(alpha = DIALOG_ALPHA),
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(c.cornerRadius),
                    ).glassPanel()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, fontSize = 22.sp, color = c.textPrimary)
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(choices, key = { _, ch -> ch.id }) { i, ch ->
                        val selected = ch.id == selectedId
                        SettingsListItem(
                            title = ch.label,
                            subtitle = ch.hint,
                            onClick = { onSelect(ch.id) },
                            modifier = if (i == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                            trailing = {
                                if (selected) {
                                    Text(
                                        "✓",
                                        fontSize = 24.sp,
                                        color = c.accent,
                                    )
                                } else {
                                    Text("", color = Color.Transparent)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private val DIALOG_WIDTH = 620.dp
private val DIALOG_MAX_HEIGHT = 480.dp
private const val DIALOG_ALPHA = 0.92f
private const val FOCUS_RETRIES = 20
