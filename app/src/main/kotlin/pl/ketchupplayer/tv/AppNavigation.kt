package pl.ketchupplayer.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.ketchupplayer.tv.core.designsystem.AppShell
import pl.ketchupplayer.tv.core.designsystem.KetchupLogoApp
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.glassPanel
import pl.ketchupplayer.tv.feature.details.DetailsRoute
import pl.ketchupplayer.tv.feature.guide.GuideRoute
import pl.ketchupplayer.tv.feature.home.HomeRoute
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
    SplashOverlay(root.splashMs) {
        when (done) {
            null -> Unit
            false -> OnboardingRoute(onFinished = root::finishOnboarding)
            true -> MainFlow(onExit, root)
        }
    }
}

@Composable
private fun MainFlow(
    onExit: () -> Unit,
    root: RootViewModel,
) {
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
    val newEpisodes by root.newEpisodes.collectAsStateWithLifecycle()
    val entries = railEntries(newEpisodes) { labels.getValue(it) }
    // Wybór sekcji z szyny zaczyna stos od nowa: Home -> sekcja (BACK z sekcji wraca na szynę, z szyny na Home).
    val settingsOpen = Routes.isSettings(top)
    // Ustawienia to panel z prawej nad bieżącą sekcją: wybór z szyny dokłada go na stos, sekcja pod spodem zostaje,
    // a zamknięcie (BACK) zdejmuje go ze stosu.
    var railFocusTick by remember { mutableIntStateOf(0) }
    val closeSettings: () -> Unit = {
        while (stack.size > 1 && Routes.isSettings(stack.last())) stack.removeAt(stack.lastIndex)
        railFocusTick++ // po zamknięciu panelu fokus wraca na pozycję szyny sekcji pod spodem
    }
    val select: (String) -> Unit = { route ->
        if (route == Section.SETTINGS.route) {
            if (!settingsOpen) stack.add(route)
        } else {
            stack.clear()
            stack.add(Routes.HOME)
            if (route != Routes.HOME) stack.add(route)
        }
    }
    val base = if (settingsOpen) stack.lastOrNull { !Routes.isSettings(it) } ?: Routes.HOME else top
    AppShell(
        entries = entries,
        selectedId = Routes.railSection(top)?.route,
        onSelect = select,
        onRailBack = {
            when {
                settingsOpen -> closeSettings()
                top == Routes.HOME -> onExit()
                else -> select(Routes.HOME)
            }
        },
        avatar = {
            KetchupLogoApp(Modifier.fillMaxSize())
        },
    ) {
        val toRailNow = pl.ketchupplayer.tv.core.designsystem.LocalFocusRail.current
        LaunchedEffect(railFocusTick) {
            if (railFocusTick > 0) {
                withFrameNanos { }
                toRailNow()
            }
        }
        // Pod panelem Ustawień sekcja jest tylko tłem: bez fokusu i semantyki (D-pad nie wchodzi pod spód).
        Box(
            if (settingsOpen) {
                Modifier.alpha(UNDER_PANEL_ALPHA).focusProperties { canFocus = false }.clearAndSetSemantics { }
            } else {
                Modifier
            },
        ) { SectionContent(base, push, openPlayer) }
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(tween(SETTINGS_PANEL_MS)) { it } + fadeIn(tween(SETTINGS_PANEL_MS)),
            exit = slideOutHorizontally(tween(SETTINGS_PANEL_MS)) { it } + fadeOut(tween(SETTINGS_PANEL_MS)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SettingsPanel(initialPage(top), closeSettings)
        }
    }
}

private const val SETTINGS_PANEL_MS = 250

private fun initialPage(route: String): SettingsPage? = SettingsPage.entries.firstOrNull { it.route == route }

/** Panel Ustawień z prawej: ciemna szklana tafla na ok. 3/4 szerokości treści; BACK z listy kategorii zamyka. */
@Composable
private fun SettingsPanel(
    page: SettingsPage?,
    onClose: () -> Unit,
) {
    val c = KetchupTheme.colors
    Box(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(SETTINGS_PANEL_WIDTH)
            .background(c.background.copy(alpha = SETTINGS_PANEL_ALPHA), RoundedCornerShape(c.cornerRadius))
            .glassPanel(),
    ) {
        Box(Modifier.padding(horizontal = SETTINGS_PANEL_PADDING)) { SettingsScreen(page, onClose) }
    }
}

private val SETTINGS_PANEL_PADDING = 20.dp

private const val SETTINGS_PANEL_WIDTH = 0.74f
private const val SETTINGS_PANEL_ALPHA = 0.97f
private const val UNDER_PANEL_ALPHA = 0.4f

@Composable
private fun sectionLabel(section: Section): String =
    stringResource(
        when (section) {
            Section.PROFILE -> R.string.nav_profile
            Section.SEARCH -> R.string.nav_search
            Section.HOME -> R.string.nav_home
            Section.LIVE -> R.string.nav_live
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
        // „Na żywo” to widok EPG z grupami, podglądem audycji, Radarem i zainteresowaniami (dawny osobny Przewodnik).
        Section.LIVE.route -> GuideRoute(openPlayer, { push(SettingsPage.PROVIDERS.route) }, toRail)
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
                onOpenGuide = { push(Section.LIVE.route) },
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
