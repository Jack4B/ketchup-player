@file:Suppress("MatchingDeclarationName")

package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Teksty wyboru listy (ekran podaje je ze swoich zasobów). */
data class PlaylistPickerLabels(
    val label: String,
    val all: String,
    val dialogTitle: String,
)

/**
 * Wybór listy (playlisty) nad grupami: wiersz „Lista: Wszystkie listy” albo nazwa nadana przez użytkownika. OK otwiera
 * okno z listami; „Wszystkie listy” jest zawsze pierwsze. Pokazywany tylko wtedy, gdy list jest więcej niż jedna.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistPicker(
    labels: PlaylistPickerLabels,
    options: List<Choice>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.id == selectedId }?.label ?: labels.all
    FocusItem({ open = true }, modifier.fillMaxWidth().testTag(TAG_PLAYLIST_PICKER)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(labels.label, fontSize = 14.sp, color = c.textSecondary, maxLines = 1)
            Text(
                current,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (open) {
        ChoiceDialog(
            title = labels.dialogTitle,
            choices = listOf(Choice(ALL_ID, labels.all)) + options,
            selectedId = selectedId ?: ALL_ID,
            onSelect = {
                open = false
                onSelect(it.takeIf { id -> id != ALL_ID })
            },
            onDismiss = { open = false },
        )
    }
}

const val TAG_PLAYLIST_PICKER = "playlist_picker"
private const val ALL_ID = "\u0000all"
