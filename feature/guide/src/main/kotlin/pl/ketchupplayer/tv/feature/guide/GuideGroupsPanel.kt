package pl.ketchupplayer.tv.feature.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.panel

/** Pozycje wirtualne przed grupami dostawcy: Ulubione, Ostatnie, Wszystkie. */
internal const val VIRTUAL_GROUPS = 3

/** Tytuł bieżącego wyboru grupy (na przycisku „Grupy”). */
@Composable
internal fun groupTitle(state: GuideUiState): String =
    when {
        state.favoritesSelected -> stringResource(R.string.guide_group_favorites)
        state.recentSelected -> stringResource(R.string.guide_group_recent)
        state.selectedGroup != null -> state.selectedGroup.ifBlank { stringResource(R.string.guide_group_none) }
        else -> stringResource(R.string.guide_group_all)
    }

/** Wysuwany panel grup: pełne nazwy (do 2 linii), liczniki, fokus na wybranej grupie. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun GuideGroupsPanel(
    state: GuideUiState,
    listState: LazyListState,
    selectedFocus: FocusRequester,
    onPick: (GuideGroup) -> Unit,
    modifier: Modifier,
) {
    val c = KetchupTheme.colors
    val allSelected = state.selectedGroup == null && !state.favoritesSelected && !state.recentSelected

    fun mark(selected: Boolean) = if (selected) Modifier.focusRequester(selectedFocus) else Modifier
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.guide_groups), fontSize = 15.sp, color = c.textSecondary)
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.favorites.isNotEmpty()) {
                item(key = "favorites") {
                    GroupRow(
                        stringResource(R.string.guide_group_favorites),
                        state.favorites.size,
                        state.favoritesSelected,
                        mark(state.favoritesSelected),
                    ) { onPick(GuideGroup.Favorites) }
                }
            }
            if (state.recentCount > 0) {
                item(key = "recent") {
                    GroupRow(
                        stringResource(R.string.guide_group_recent),
                        state.recentCount,
                        state.recentSelected,
                        mark(state.recentSelected),
                    ) { onPick(GuideGroup.Recent) }
                }
            }
            item(key = "all") {
                GroupRow(stringResource(R.string.guide_group_all), state.totalCount, allSelected, mark(allSelected)) {
                    onPick(GuideGroup.All)
                }
            }
            items(state.groups, key = { it }) { g ->
                val selected = state.selectedGroup == g
                GroupRow(
                    g.ifBlank { stringResource(R.string.guide_group_none) },
                    state.groupCounts[g] ?: 0,
                    selected,
                    mark(selected),
                ) { onPick(GuideGroup.Named(g)) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupRow(
    text: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = KetchupTheme.colors
    FocusItem(
        onClick,
        modifier.fillMaxWidth(),
        selected = selected,
        container = if (selected) c.surface3 else Color.Transparent,
        outlined = false,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                fontSize = 17.sp,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(count.toString(), fontSize = 13.sp, color = c.textSecondary)
        }
    }
}
