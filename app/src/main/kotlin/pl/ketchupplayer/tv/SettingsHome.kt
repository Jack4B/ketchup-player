package pl.ketchupplayer.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced

/** Ustawienia: lista kategorii (następny etap: układ dwupoziomowy wg makiety 09). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsHome(push: (String) -> Unit) {
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.nav_settings))
        val items =
            listOf(
                R.string.start_providers to SettingsPage.PROVIDERS,
                R.string.start_epg_sources to SettingsPage.EPG_SOURCES,
                R.string.start_playback to SettingsPage.PLAYBACK,
                R.string.start_appearance to SettingsPage.APPEARANCE,
            )
        items.forEachIndexed { i, (label, page) ->
            SettingsListItem(
                title = stringResource(label),
                onClick = { push(page.route) },
                modifier = if (i == 0) Modifier.requestFocusWhenPlaced() else Modifier,
            )
        }
    }
}

/** Sekcje, których ekrany powstają w kolejnych etapach (VOD, wyszukiwarka, profile, moja lista). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ComingSoon(route: String) {
    ScreenScaffold {
        ScreenTitle(route)
        Text(stringResource(R.string.coming_soon), color = KetchupTheme.colors.textSecondary)
    }
}
