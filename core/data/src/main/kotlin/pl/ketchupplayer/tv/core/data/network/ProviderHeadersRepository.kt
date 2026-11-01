package pl.ketchupplayer.tv.core.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.network.UserAgentProfile
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import javax.inject.Inject

/** Nagłówek User-Agent wybrany dla listy: ręcznie albo zapamiętany po udanej automatycznej próbie. */
interface ProviderHeadersRepository {
    fun profile(providerId: String): Flow<UserAgentProfile>

    suspend fun current(providerId: String): UserAgentProfile

    suspend fun set(
        providerId: String,
        profile: UserAgentProfile,
    )
}

class DataStoreProviderHeadersRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : ProviderHeadersRepository {
        override fun profile(providerId: String): Flow<UserAgentProfile> =
            store.data.map { UserAgentProfiles.byId(it[key(providerId)]) }

        override suspend fun current(providerId: String): UserAgentProfile = profile(providerId).first()

        override suspend fun set(
            providerId: String,
            profile: UserAgentProfile,
        ) {
            store.edit {
                if (profile == UserAgentProfiles.AUTO) it.remove(key(providerId)) else it[key(providerId)] = profile.id
            }
        }

        private fun key(providerId: String) = stringPreferencesKey("ua_profile_$providerId")
    }
