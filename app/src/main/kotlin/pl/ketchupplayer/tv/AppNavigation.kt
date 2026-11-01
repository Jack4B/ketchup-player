package pl.ketchupplayer.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.ketchupplayer.tv.core.designsystem.AppShell
import pl.ketchupplayer.tv.feature.details.DetailsRoute
import pl.ketchupplayer.tv.feature.guide.GuideRoute
import pl.ketchupplayer.tv.feature.home.HomeRoute
import pl.ketchupplayer.tv.feature.livetv.LiveTvRoute
import pl.ketchupplayer.tv.feature.playerui.PlayerRoute
import pl.ketchupplayer.tv.feature.playerui.VodPlayerRoute
import pl.ketchupplayer.tv.feature.providers.OnboardingRoute
import pl.ketchupplayer.tv.feature.vod.VodBrowseRoute
import pl.ketchupplayer.tv.feature.vod.VodMode

/**
 * Nawigacja: stos tras (String, przeżywa obrót) + powłoka z szyną (makiety v3). Odtwarzacz jest pełnoekranowy,
 * bez powłoki. Feature nie zna innych feature: przejścia ustala wyłącznie `:app`.
 */
@Composable
fun AppNavigation(
    onExit: () -> Unit = {},
    root: RootViewModel = hiltViewModel(),
) {
    val done by root.onboardingDone.collectAsStateWithLifecycle()
    when (done) {
        null -> Unit
        false -> OnboardingRoute(onFinished = root::finishOnboarding)
        true -> MainFlow(onExit)
    }
}

@Composable
private fun MainFlow(onExit: () -> Unit) {
    // SnapshotStateList nie jest zapisywalny domyślnie: własny Saver (lista Stringów).
    val stack =
        rememberSaveable(
            saver =
                listSaver<SnapshotStateList<String>, String>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) { mutableStateListOf(Routes.HOME) }
    val push: (String) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val openPlayer: (String) -> Unit = { push(Routes.player(it)) }
    val top = stack.last()

    val vodTarget = Routes.vodPlayerTarget(top)
    if (vodTarget != null) {
        VodPlayerRoute(vodTarget.first, vodTarget.second, onBack = pop)
        return
    }
    val playerChannel = Routes.playerChannel(top)
    if (playerChannel != null) {
        PlayerRoute(playerChannel, onBack = pop)
        return
    }

    val labels = Section.entries.associateWith { sectionLabel(it) }
    val entries = railEntries { labels.getValue(it) }
    // Wybór sekcji z szyny zaczyna stos od nowa: Home -> sekcja (BACK z sekcji wraca na szynę, z szyny na Home).
    val select: (String) -> Unit = { route ->
        stack.clear()
        stack.add(Routes.HOME)
        if (route != Routes.HOME) stack.add(route)
    }
    AppShell(
        entries = entries,
        selectedId = Routes.railSection(top)?.route,
        onSelect = select,
        onRailBack = { if (top == Routes.HOME) onExit() else select(Routes.HOME) },
        avatar = {
            Image(
                painterResource(R.drawable.logo_app),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        },
    ) {
        SectionContent(top, push, openPlayer)
    }
}

@Composable
private fun sectionLabel(section: Section): String =
    stringResource(
        when (section) {
            Section.PROFILE -> R.string.nav_profile
            Section.SEARCH -> R.string.nav_search
            Section.HOME -> R.string.nav_home
            Section.LIVE -> R.string.nav_live
            Section.GUIDE -> R.string.nav_guide
            Section.MOVIES -> R.string.nav_movies
            Section.SERIES -> R.string.nav_series
            Section.MYLIST -> R.string.nav_mylist
            Section.SETTINGS -> R.string.nav_settings
        },
    )

@Composable
private fun SectionContent(
    route: String,
    push: (String) -> Unit,
    openPlayer: (String) -> Unit,
) {
    val toRail = pl.ketchupplayer.tv.core.designsystem.LocalFocusRail.current
    when (route) {
        Section.LIVE.route -> LiveTvRoute(openPlayer, { push(SettingsPage.PROVIDERS.route) }, toRail)
        Section.GUIDE.route -> GuideRoute(openPlayer, { push(SettingsPage.PROVIDERS.route) }, toRail)
        Section.SETTINGS.route -> SettingsScreen()
        SettingsPage.PROVIDERS.route -> SettingsScreen(SettingsPage.PROVIDERS)
        SettingsPage.EPG_SOURCES.route -> SettingsScreen(SettingsPage.EPG_SOURCES)
        SettingsPage.PLAYBACK.route -> SettingsScreen(SettingsPage.PLAYBACK)
        SettingsPage.APPEARANCE.route -> SettingsScreen(SettingsPage.APPEARANCE)
        Section.MOVIES.route ->
            VodBrowseRoute(VodMode.MOVIES, { push(Routes.details(it)) }, { push(SettingsPage.PROVIDERS.route) })
        Section.SERIES.route ->
            VodBrowseRoute(VodMode.SERIES, { push(Routes.details(it)) }, { push(SettingsPage.PROVIDERS.route) })
        Section.MYLIST.route ->
            VodBrowseRoute(VodMode.MYLIST, { push(Routes.details(it)) }, { push(SettingsPage.PROVIDERS.route) })
        Section.SEARCH.route ->
            VodBrowseRoute(VodMode.SEARCH, { push(Routes.details(it)) }, { push(SettingsPage.PROVIDERS.route) })
        Section.HOME.route ->
            HomeRoute(
                onOpenChannel = openPlayer,
                onOpenGuide = { push(Section.GUIDE.route) },
                onOpenProviders = { push(SettingsPage.PROVIDERS.route) },
                onOpenDetails = { push(Routes.details(it)) },
                onPlayVod = { push(Routes.vodPlayer(it, false)) },
            )
        else -> {
            val detailsId = Routes.detailsId(route)
            if (detailsId != null) {
                DetailsRoute(
                    detailsId,
                    onPlay = { id, fromStart -> push(Routes.vodPlayer(id, fromStart)) },
                    onOpen = { push(Routes.details(it)) },
                )
            } else {
                ComingSoon(route)
            }
        }
    }
}
