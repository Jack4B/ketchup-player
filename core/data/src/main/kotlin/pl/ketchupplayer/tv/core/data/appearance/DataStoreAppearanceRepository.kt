package pl.ketchupplayer.tv.core.data.appearance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.designsystem.AccentChoice
import pl.ketchupplayer.tv.core.designsystem.AccentId
import pl.ketchupplayer.tv.core.designsystem.HexColor
import pl.ketchupplayer.tv.core.designsystem.Palettes
import pl.ketchupplayer.tv.core.designsystem.ThemeId
import javax.inject.Inject

/** Motyw i akcent w DataStore. Akcent: `preset:NAZWA` albo `custom:#RRGGBB`; brak/nieznany = domyślny motywu. */
class DataStoreAppearanceRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : AppearanceRepository {
        override val appearance: Flow<Appearance> =
            store.data.map { prefs ->
                Appearance(
                    theme = themeFromStored(prefs[THEME]),
                    accent = decode(prefs[ACCENT]),
                    glassBlur = prefs[BLUR] ?: false,
                    glassBlurDecided = prefs[BLUR_DECIDED] ?: false,
                    customHue = prefs[HUE] ?: Palettes.DEFAULT_HUE,
                )
            }

        override suspend fun setTheme(theme: ThemeId) {
            store.edit { it[THEME] = theme.name }
        }

        override suspend fun setCustomHue(hue: Int) {
            store.edit { it[HUE] = hue.coerceIn(0, MAX_HUE) }
        }

        override suspend fun setGlassBlur(enabled: Boolean) {
            store.edit {
                it[BLUR] = enabled
                it[BLUR_DECIDED] = true
            }
        }

        override suspend fun setAccent(accent: AccentChoice?) {
            store.edit { prefs ->
                when (accent) {
                    null -> prefs.remove(ACCENT)
                    is AccentChoice.Preset -> prefs[ACCENT] = "preset:${accent.id.name}"
                    is AccentChoice.Custom -> prefs[ACCENT] = "custom:${HexColor.format(accent.argb)}"
                }
            }
        }

        private fun decode(raw: String?): AccentChoice? =
            when {
                raw == null -> null
                raw.startsWith("preset:") ->
                    AccentId.entries.firstOrNull { it.name == raw.removePrefix("preset:") }?.let(AccentChoice::Preset)
                raw.startsWith("custom:") -> HexColor.parse(raw.removePrefix("custom:"))?.let(AccentChoice::Custom)
                else -> null
            }

        private companion object {
            const val MAX_HUE = 360
            val HUE =
                androidx.datastore.preferences.core
                    .intPreferencesKey("appearance_custom_hue")
            val THEME = stringPreferencesKey("appearance_theme")
            val ACCENT = stringPreferencesKey("appearance_accent")
            val BLUR_DECIDED =
                androidx.datastore.preferences.core
                    .booleanPreferencesKey("appearance_glass_blur_decided")
            val BLUR =
                androidx.datastore.preferences.core
                    .booleanPreferencesKey("appearance_glass_blur")
        }
    }

/** Stare nazwy motywów z wersji 0.x (Granat, Bordo) trafiają na ich następców; nieznane na domyślny Morski. */
internal fun themeFromStored(name: String?): ThemeId =
    when (name) {
        "GRANAT" -> ThemeId.MORSKI
        "BORDO" -> ThemeId.GRAFIT
        else -> ThemeId.entries.firstOrNull { it.name == name } ?: ThemeId.MORSKI
    }
