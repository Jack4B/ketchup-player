package pl.ketchupplayer.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.ketchupplayer.tv.core.designsystem.ListBadgeUi

/** Tekst pigułki listy z [ListBadgeViewModel]: z połączeniami, gdy panel je podaje, inaczej sama nazwa. */
@Composable
fun rememberListBadge(viewModel: ListBadgeViewModel = hiltViewModel()): ListBadgeUi? {
    val state by viewModel.badge.collectAsStateWithLifecycle()
    val s = state ?: return null
    val hasConnections = s.activeConnections != null && s.maxConnections != null && s.maxConnections > 0
    val text =
        if (hasConnections) {
            stringResource(R.string.list_badge_connections, s.name, s.activeConnections ?: 0, s.maxConnections ?: 0)
        } else {
            stringResource(R.string.list_badge_name, s.name)
        }
    return ListBadgeUi(text, warn = s.full)
}
