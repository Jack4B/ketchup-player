package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Standardowe pozycje szyny (etykiety PL) do podglądów i testów ekranów w powłoce. */
fun previewRailEntries(): List<RailEntry> =
    listOf(
        RailEntry("profile", "Profil", RailIcon.PROFILE),
        RailEntry("search", "Szukaj", RailIcon.SEARCH),
        RailEntry("home", "Home", RailIcon.HOME),
        RailEntry("live", "Na żywo", RailIcon.TV),
        RailEntry("guide", "Przewodnik", RailIcon.GUIDE),
        RailEntry("movies", "Filmy", RailIcon.MOVIES),
        RailEntry("series", "Seriale", RailIcon.SERIES, badge = true),
        RailEntry("mylist", "Moja lista", RailIcon.FAVORITES),
        RailEntry("settings", "Ustawienia", RailIcon.SETTINGS),
    )

/**
 * Ekran w powłoce z szyną, jak w aplikacji: do zrzutów Roborazzi porównywanych z makietami v3.
 * Zegar w nagłówku jest zamrożony ([UiClock.Frozen]), żeby zrzuty nie zależały od godziny uruchomienia testu.
 */
@Composable
fun ShellPreview(
    selected: String,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalUiClock provides UiClock.Frozen) {
        AppShell(previewRailEntries(), selected, {}, {}) { content() }
    }
}
