package pl.ketchupplayer.tv.core.data.interests

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** „Moje zainteresowania” (Przewodnik): słowa kluczowe, po których szukamy audycji w EPG. */
interface InterestsRepository {
    val keywords: Flow<List<String>>

    suspend fun add(keyword: String)

    suspend fun remove(keyword: String)
}

class DataStoreInterestsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : InterestsRepository {
        override val keywords: Flow<List<String>> =
            store.data.map {
                (it[KEY] ?: emptySet()).sortedBy(
                    String::lowercase,
                )
            }

        override suspend fun add(keyword: String) {
            val clean = keyword.trim().take(MAX_LENGTH)
            if (clean.isEmpty()) return
            store.edit { prefs ->
                val current = prefs[KEY] ?: emptySet()
                if (current.none { it.equals(clean, ignoreCase = true) } && current.size < MAX_KEYWORDS) {
                    prefs[KEY] = current + clean
                }
            }
        }

        override suspend fun remove(keyword: String) {
            store.edit { prefs -> prefs[KEY] = (prefs[KEY] ?: emptySet()) - keyword }
        }

        private companion object {
            val KEY = stringSetPreferencesKey("interest_keywords")
            const val MAX_LENGTH = 60
            const val MAX_KEYWORDS = 30
        }
    }
