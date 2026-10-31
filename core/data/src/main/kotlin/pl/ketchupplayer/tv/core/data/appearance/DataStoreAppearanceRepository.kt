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
                    theme = ThemeId.entries.firstOrNull { it.name == prefs[THEME] } ?: ThemeId.GRANAT,
                    accent = decode(prefs[ACCENT]),
                )
            }

        override suspend fun setTheme(theme: ThemeId) {
            store.edit { it[THEME] = theme.name }
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
            val THEME = stringPreferencesKey("appearance_theme")
            val ACCENT = stringPreferencesKey("appearance_accent")
        }
    }
