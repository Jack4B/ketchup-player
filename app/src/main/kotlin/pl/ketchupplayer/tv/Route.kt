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
    GROUPS("settings/groups"),
    PLAYBACK("settings/playback"),
    APPEARANCE("settings/look"),
}

object Routes {
    const val HOME = "home"
    private const val PLAYER_PREFIX = "player:"

    fun player(channelId: String) = PLAYER_PREFIX + channelId

    private const val DETAILS_PREFIX = "details:"
    private const val VOD_PREFIX = "vod:"
    private const val VOD_START = "vod0:"

    fun details(itemId: String) = DETAILS_PREFIX + itemId

    fun detailsId(route: String): String? = route.takeIf { it.startsWith(DETAILS_PREFIX) }?.removePrefix(DETAILS_PREFIX)

    /** Odtwarzanie filmu/odcinka; [fromStart] ignoruje zapisaną pozycję wznowienia. */
    fun vodPlayer(
        itemId: String,
        fromStart: Boolean,
    ) = (if (fromStart) VOD_START else VOD_PREFIX) + itemId

    /** (id, odtwarzaj od początku) albo null, gdy to nie trasa odtwarzacza VOD. */
    fun vodPlayerTarget(route: String): Pair<String, Boolean>? =
        when {
            route.startsWith(VOD_START) -> route.removePrefix(VOD_START) to true
            route.startsWith(VOD_PREFIX) -> route.removePrefix(VOD_PREFIX) to false
            else -> null
        }

    fun playerChannel(route: String): String? =
        route.takeIf { it.startsWith(PLAYER_PREFIX) }?.removePrefix(PLAYER_PREFIX)

    fun section(route: String): Section? = Section.entries.firstOrNull { it.route == route }

    /** Sekcja podświetlona na szynie dla trasy (podstrony ustawień należą do „Ustawienia”). */
    fun railSection(route: String): Section? =
        section(route)
            ?: when {
                route.startsWith("settings/") -> Section.SETTINGS
                // Rodzaju nie ma w trasie szczegółów, więc podświetlamy Filmy.
                route.startsWith(DETAILS_PREFIX) -> Section.MOVIES
                else -> null
            }
}

fun railEntries(
    newEpisodes: Boolean,
    label: (Section) -> String,
): List<RailEntry> =
    Section.entries.map { RailEntry(it.route, label(it), it.icon, badge = it == Section.SERIES && newEpisodes) }
