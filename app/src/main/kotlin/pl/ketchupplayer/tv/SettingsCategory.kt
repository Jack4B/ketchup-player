package pl.ketchupplayer.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.LocalBreadcrumb
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.panel
import pl.ketchupplayer.tv.feature.providers.ProvidersRoute
import pl.ketchupplayer.tv.feature.settings.AppearanceRoute
import pl.ketchupplayer.tv.feature.settings.EpgSourcesRoute
import pl.ketchupplayer.tv.feature.settings.GroupsSettingsRoute
import pl.ketchupplayer.tv.feature.settings.NetworkSettingsRoute
import pl.ketchupplayer.tv.feature.settings.PlaybackSettingsRoute

/** Kategorie ustawień (makieta v3 09). Te bez własnych opcji pokazują krótką informację. */
enum class SettingsCategory(
    val label: Int,
    val page: SettingsPage? = null,
) {
    PROVIDERS(R.string.settings_cat_providers, SettingsPage.PROVIDERS),
    GROUPS(R.string.settings_cat_groups, SettingsPage.GROUPS),
    PLAYBACK(R.string.settings_cat_playback, SettingsPage.PLAYBACK),
    TV(R.string.settings_cat_tv, SettingsPage.EPG_SOURCES),
    VOD(R.string.settings_cat_vod),
    RECORDING(R.string.settings_cat_recording),
    NETWORK(R.string.settings_cat_network, SettingsPage.NETWORK),
    PROFILES(R.string.settings_cat_profiles),
    APPEARANCE(R.string.settings_cat_appearance, SettingsPage.APPEARANCE),
    REMOTE(R.string.settings_cat_remote),
    SERVICES(R.string.settings_cat_services),
    DATA(R.string.settings_cat_data),
    ABOUT(R.string.settings_cat_about),
}

/**
 * Ustawienia dwupoziomowe: po lewej kategorie, po prawej opcje zaznaczonej (fokusowanej) kategorii.
 * PRAWO/OK z kategorii wchodzi w opcje, BACK z opcji wraca do kategorii, z kategorii na szynę.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(initial: SettingsPage? = null) {
    val categories = SettingsCategory.entries
    var selected by rememberSaveable { mutableIntStateOf(initialCategoryIndex(initial)) }
    val requesters = remember { categories.map { FocusRequester() } }
    LaunchedEffect(Unit) {
        repeat(FOCUS_RETRIES) {
            withFrameNanos { }
            if (runCatching { requesters[selected].requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    val c = KetchupTheme.colors
    ScreenScaffold(scrollable = false) {
        ScreenTitle(stringResource(R.string.nav_settings))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(
                Modifier
                    .width(CATEGORIES_WIDTH)
                    .fillMaxHeight()
                    .panel()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(categories, key = { _, cat -> cat.name }) { i, cat ->
                    FocusItem(
                        onClick = { selected = i },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) selected = i },
                        selected = i == selected,
                        focusRequester = requesters[i],
                        container = if (i == selected) c.surface3 else Color.Transparent,
                        outlined = false,
                    ) {
                        Text(
                            stringResource(cat.label),
                            fontSize = 15.sp,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                val toCategories: () -> Unit = { runCatching { requesters[selected].requestFocus() } }
                CompositionLocalProvider(LocalBreadcrumb provides stringResource(R.string.nav_settings)) {
                    // key: przełączenie kategorii czyści stan poprzedniej (formularze, edytory).
                    androidx.compose.runtime.key(selected) { CategoryContent(categories[selected], toCategories) }
                }
            }
        }
    }
}

/** Kategoria startowa: odpowiadająca stronie, a bez niej pierwsza („Listy i dostawcy”). */
internal fun initialCategoryIndex(initial: SettingsPage?): Int =
    if (initial == null) 0 else SettingsCategory.entries.indexOfFirst { it.page == initial }.coerceAtLeast(0)

private val CATEGORIES_WIDTH = 230.dp
private const val FOCUS_RETRIES = 20

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryContent(
    category: SettingsCategory,
    onBack: () -> Unit,
) {
    when (category) {
        SettingsCategory.PROVIDERS -> ProvidersRoute(onBack = onBack)
        SettingsCategory.GROUPS -> GroupsSettingsRoute(onBack = onBack)
        SettingsCategory.NETWORK -> NetworkSettingsRoute(onBack = onBack)
        SettingsCategory.PLAYBACK -> PlaybackSettingsRoute(onBack = onBack)
        SettingsCategory.TV -> EpgSourcesRoute(onBack = onBack)
        SettingsCategory.APPEARANCE -> AppearanceRoute(onBack = onBack)
        SettingsCategory.ABOUT -> About()
        else -> Soon(category)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Soon(category: SettingsCategory) {
    val c = KetchupTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(category.label), fontSize = 22.sp, color = c.textPrimary)
        Text(stringResource(R.string.coming_soon), fontSize = 15.sp, color = c.textSecondary)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun About() {
    val c = KetchupTheme.colors
    val context = LocalContext.current
    val version =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_about_name), fontSize = 22.sp, color = c.textPrimary)
        Text(stringResource(R.string.settings_about_version, version), fontSize = 15.sp, color = c.textSecondary)
        Text(stringResource(R.string.settings_about_text), fontSize = 15.sp, color = c.textPrimary)
    }
}

/** Sekcje, których ekrany powstają w kolejnych etapach (profile). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ComingSoon(route: String) {
    ScreenScaffold {
        ScreenTitle(route)
        Text(stringResource(R.string.coming_soon), color = KetchupTheme.colors.textSecondary)
    }
}
