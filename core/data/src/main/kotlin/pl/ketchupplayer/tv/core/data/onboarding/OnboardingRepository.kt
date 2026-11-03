package pl.ketchupplayer.tv.core.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Czy pierwsze uruchomienie (kreator „Dodaj pierwszą listę”) zostało zakończone lub pominięte. */
interface OnboardingRepository {
    val done: Flow<Boolean>

    suspend fun setDone(value: Boolean = true)
}

class DataStoreOnboardingRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : OnboardingRepository {
        override val done: Flow<Boolean> = store.data.map { it[DONE] == true }

        override suspend fun setDone(value: Boolean) {
            store.edit { it[DONE] = value }
        }

        private companion object {
            val DONE = booleanPreferencesKey("onboarding_done")
        }
    }
