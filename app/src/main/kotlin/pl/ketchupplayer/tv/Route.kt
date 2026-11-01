package pl.ketchupplayer.tv

import pl.ketchupplayer.tv.core.designsystem.RailEntry
import pl.ketchupplayer.tv.core.designsystem.RailIcon

/** Sekcje szyny nawigacji (makiety v3). [route] jest zapisywany w stosie nawigacji (String), żeby przeżył obrót. */
enum class Section(
    val route: String,
    val icon: RailIcon,
) {
    PROFILE("profile", RailIcon.PROFILE),
    SEARCH("search", RailIcon.SEARCH),
    HOME("home", RailIcon.HOME),
    LIVE("live", RailIcon.TV),
    GUIDE("guide", RailIcon.GUIDE),
    MOVIES("movies", RailIcon.MOVIES),
    SERIES("series", RailIcon.SERIES),
    MYLIST("mylist", RailIcon.FAVORITES),
    SETTINGS("settings", RailIcon.SETTINGS),
}

/** Podstrony ustawień (wewnątrz powłoki, z zaznaczoną sekcją „Ustawienia”). */
enum class SettingsPage(
    val route: String,
) {
    PROVIDERS("settings/providers"),
    EPG_SOURCES("settings/epg"),
    PLAYBACK("settings/playback"),
    APPEARANCE("settings/look"),
}

object Routes {
    const val HOME = "home"
    private const val PLAYER_PREFIX = "player:"

    fun player(channelId: String) = PLAYER_PREFIX + channelId

    fun playerChannel(route: String): String? =
        route.takeIf { it.startsWith(PLAYER_PREFIX) }?.removePrefix(PLAYER_PREFIX)

    fun section(route: String): Section? = Section.entries.firstOrNull { it.route == route }

    /** Sekcja podświetlona na szynie dla trasy (podstrony ustawień należą do „Ustawienia”). */
    fun railSection(route: String): Section? =
        section(route) ?: if (route.startsWith("settings/")) Section.SETTINGS else null
}

fun railEntries(label: (Section) -> String): List<RailEntry> =
    Section.entries.map { RailEntry(it.route, label(it), it.icon, badge = it == Section.SERIES && false) }
