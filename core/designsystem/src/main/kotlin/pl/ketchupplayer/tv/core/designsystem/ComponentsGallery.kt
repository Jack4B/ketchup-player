package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

/** Galeria komponentów do zrzutów Roborazzi i podglądów (docs/mockups/components/). */
@Composable
fun ComponentsGallery(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(
        modifier.fillMaxSize().background(KetchupTheme.colors.background).padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            KetchupButton("Oglądaj", onClick, primary = true, focusRequester = first)
            KetchupButton("Od początku", onClick)
            NewEpisodeBadge()
            NewEpisodeBadge(count = 3, label = "3 nowe")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PosterCard("Tytuł filmu", onClick, newEpisodes = 2, newEpisodeLabel = "2 nowe")
            PosterCard("Serial o bardzo długiej nazwie, która się zawija", onClick)
            PosterCard("Trzeci plakat", onClick)
        }
        ChannelRow(101, "JEDEN HD", onClick, nowPlaying = "Wiadomości", progress = 0.4f)
        ChannelRow(102, "INFO24", onClick, nowPlaying = "Studio Świat")
        SettingsListItem("Źródła EPG", onClick, subtitle = "epg.ovh, Teleman, własne")
        StateView(StateKind.ERROR, "Nie udało się pobrać danych", actionLabel = "Spróbuj ponownie", onAction = onClick)
    }
}
