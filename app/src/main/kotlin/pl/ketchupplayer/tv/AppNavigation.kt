package pl.ketchupplayer.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import pl.ketchupplayer.tv.feature.guide.GuideRoute
import pl.ketchupplayer.tv.feature.livetv.LiveTvRoute
import pl.ketchupplayer.tv.feature.playerui.PlayerRoute
import pl.ketchupplayer.tv.feature.providers.ProvidersRoute
import pl.ketchupplayer.tv.feature.settings.AppearanceRoute
import pl.ketchupplayer.tv.feature.settings.EpgSourcesRoute
import pl.ketchupplayer.tv.feature.settings.PlaybackSettingsRoute

/**
 * Prosta nawigacja ze stosem (kontrakty tras w `core:ui` pojawią się przy większej liczbie ekranów).
 * Feature nie zna innych feature: przejścia ustala wyłącznie `:app`.
 */
@Composable
fun AppNavigation() {
    // SnapshotStateList nie jest zapisywalny domyślnie: własny Saver (lista Stringów) przeżywa obrót i zabicie procesu.
    val stack =
        rememberSaveable(
            saver =
                listSaver<SnapshotStateList<String>, String>(save = {
                    it.toList()
                }, restore = { it.toMutableStateList() }),
        ) {
            mutableStateListOf(Routes.START)
        }
    val push: (String) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val top = stack.last()
    val playerChannel = Routes.playerChannel(top)
    when {
        playerChannel != null -> PlayerRoute(playerChannel, onBack = pop)
        top == Destination.LIVE.key ->
            LiveTvRoute(
                onOpenChannel = { push(Routes.player(it)) },
                onOpenProviders = { push(Destination.PROVIDERS.key) },
                onBack = pop,
            )
        top == Destination.GUIDE.key ->
            GuideRoute(
                onOpenChannel = { push(Routes.player(it)) },
                onOpenProviders = { push(Destination.PROVIDERS.key) },
                onBack = pop,
            )
        top == Destination.PROVIDERS.key -> ProvidersRoute(onBack = pop)
        top == Destination.EPG_SOURCES.key -> EpgSourcesRoute(onBack = pop)
        top == Destination.PLAYBACK.key -> PlaybackSettingsRoute(onBack = pop)
        top == Destination.APPEARANCE.key -> AppearanceRoute(onBack = pop)
        else -> StartScreen(onNavigate = { push(Routes.of(it)) })
    }
}
