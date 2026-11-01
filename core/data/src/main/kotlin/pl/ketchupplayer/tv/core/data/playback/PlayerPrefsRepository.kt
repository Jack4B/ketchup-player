package pl.ketchupplayer.tv.core.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Preferencje odtwarzacza (poza failoverem): zegar w rogu ekranu podczas oglądania. */
interface PlayerPrefsRepository {
    /** Zegar w prawym górnym rogu odtwarzacza, domyślnie włączony. */
    val clockEnabled: Flow<Boolean>

    suspend fun setClockEnabled(enabled: Boolean)
}

class DataStorePlayerPrefsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : PlayerPrefsRepository {
        override val clockEnabled: Flow<Boolean> = store.data.map { it[CLOCK] ?: true }

        override suspend fun setClockEnabled(enabled: Boolean) {
            store.edit { it[CLOCK] = enabled }
        }

        private companion object {
            val CLOCK = booleanPreferencesKey("player_clock_enabled")
        }
    }
