package pl.ketchupplayer.tv.core.data.personalization

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.policy.personalization.PersonalizationParams
import javax.inject.Inject

/**
 * Zgoda i ustawienia personalizacji. Domyślnie wszystko wyłączone: bez jawnego włączenia aplikacja nie zapisuje
 * żadnej sesji oglądania. [incognito] („Nie ucz się z tej sesji”) wstrzymuje naukę bez kasowania dotychczasowej.
 */
data class PersonalizationPrefs(
    val enabled: Boolean = false,
    val incognito: Boolean = false,
    val retentionDays: Int = PersonalizationParams.DEFAULT.defaultRetentionDays,
) {
    /** Czy wolno teraz zapisywać i oceniać oglądanie. */
    val learning: Boolean get() = enabled && !incognito
}

interface PersonalizationSettings {
    val prefs: Flow<PersonalizationPrefs>

    suspend fun current(): PersonalizationPrefs = prefs.first()

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setIncognito(incognito: Boolean)

    suspend fun setRetentionDays(days: Int)
}

class DataStorePersonalizationSettings
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : PersonalizationSettings {
        override val prefs: Flow<PersonalizationPrefs> =
            store.data.map {
                PersonalizationPrefs(
                    enabled = it[ENABLED] ?: false,
                    incognito = it[INCOGNITO] ?: false,
                    retentionDays = (it[RETENTION] ?: DEFAULT_RETENTION).coerceIn(MIN_RETENTION, MAX_RETENTION),
                )
            }

        override suspend fun setEnabled(enabled: Boolean) {
            store.edit {
                it[ENABLED] = enabled
                // Wyłączenie zgody kończy też tryb „nie ucz się”: po ponownym włączeniu stan jest jednoznaczny.
                if (!enabled) it[INCOGNITO] = false
            }
        }

        override suspend fun setIncognito(incognito: Boolean) {
            store.edit { it[INCOGNITO] = incognito }
        }

        override suspend fun setRetentionDays(days: Int) {
            store.edit { it[RETENTION] = days.coerceIn(MIN_RETENTION, MAX_RETENTION) }
        }

        companion object {
            const val MIN_RETENTION = 30
            const val MAX_RETENTION = 365
            val RETENTION_CHOICES = listOf(30, 90, 180, 365)
            private val DEFAULT_RETENTION = PersonalizationParams.DEFAULT.defaultRetentionDays
            private val ENABLED = booleanPreferencesKey("personalization_enabled")
            private val INCOGNITO = booleanPreferencesKey("personalization_incognito")
            private val RETENTION = intPreferencesKey("personalization_retention_days")
        }
    }
