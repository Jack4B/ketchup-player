package pl.ketchupplayer.tv.core.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.policy.BkFailoverPolicy
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity
import pl.ketchupplayer.tv.core.policy.FailoverSettings
import javax.inject.Inject

/** Ustawienia automatycznego przełączania źródeł (klucze `bk_failover_*` z KONSPEKT §9). */
interface FailoverSettingsRepository {
    val settings: Flow<FailoverSettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setSensitivity(sensitivity: FailoverSensitivity)

    /** Wartości spoza [BkFailoverPolicy.TV_DELAY_OPTIONS_SECONDS] są zamieniane na domyślną. */
    suspend fun setTvDelaySeconds(seconds: Int)
}

class DataStoreFailoverSettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : FailoverSettingsRepository {
        override val settings: Flow<FailoverSettings> =
            store.data.map { p ->
                FailoverSettings(
                    enabled = p[ENABLED] ?: true,
                    sensitivity =
                        FailoverSensitivity.entries.firstOrNull { it.name == p[SENSITIVITY] }
                            ?: FailoverSensitivity.STANDARD,
                    tvDelaySeconds =
                        BkFailoverPolicy.tvSwitchDelaySeconds(
                            p[TV_DELAY] ?: BkFailoverPolicy.DEFAULT_TV_DELAY_SECONDS,
                        ),
                )
            }

        override suspend fun setEnabled(enabled: Boolean) {
            store.edit { it[ENABLED] = enabled }
        }

        override suspend fun setSensitivity(sensitivity: FailoverSensitivity) {
            store.edit { it[SENSITIVITY] = sensitivity.name }
        }

        override suspend fun setTvDelaySeconds(seconds: Int) {
            store.edit { it[TV_DELAY] = BkFailoverPolicy.tvSwitchDelaySeconds(seconds) }
        }

        private companion object {
            val ENABLED = booleanPreferencesKey("bk_failover_enabled")
            val SENSITIVITY = stringPreferencesKey("bk_failover_sensitivity")
            val TV_DELAY = intPreferencesKey("bk_failover_tv_delay_seconds")
        }
    }
