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

    /** Minimalny odstęp między kolejnymi naciśnięciami tej samej strzałki w ms (0 = wyłączony); domyślnie 250. */
    val remoteDebounceMs: Flow<Int>

    suspend fun setRemoteDebounceMs(value: Int)

    /** Odświeżanie EPG (i starych list) przy starcie aplikacji; domyślnie włączone. */
    val autoRefresh: Flow<Boolean>

    suspend fun setAutoRefresh(enabled: Boolean)

    /** Żywy podgląd (wyciszony) zaznaczonego kanału w Na żywo; domyślnie włączony. */
    val livePreview: Flow<Boolean>

    suspend fun setLivePreview(enabled: Boolean)
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

        override val remoteDebounceMs: Flow<Int> = store.data.map { it[DEBOUNCE] ?: DEFAULT_DEBOUNCE_MS }

        override suspend fun setRemoteDebounceMs(value: Int) {
            store.edit { it[DEBOUNCE] = value }
        }

        override val autoRefresh: Flow<Boolean> = store.data.map { it[AUTO_REFRESH] ?: true }

        override suspend fun setAutoRefresh(enabled: Boolean) {
            store.edit { it[AUTO_REFRESH] = enabled }
        }

        override val livePreview: Flow<Boolean> = store.data.map { it[LIVE_PREVIEW] ?: true }

        override suspend fun setLivePreview(enabled: Boolean) {
            store.edit { it[LIVE_PREVIEW] = enabled }
        }

        private companion object {
            val LIVE_PREVIEW =
                androidx.datastore.preferences.core
                    .booleanPreferencesKey("live_preview")
            val AUTO_REFRESH =
                androidx.datastore.preferences.core
                    .booleanPreferencesKey("auto_refresh_on_start")
            const val DEFAULT_DEBOUNCE_MS = 250
            val DEBOUNCE =
                androidx.datastore.preferences.core
                    .intPreferencesKey("remote_debounce_ms")
            val CLOCK = booleanPreferencesKey("player_clock_enabled")
        }
    }
