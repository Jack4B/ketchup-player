package pl.ketchupplayer.tv.core.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.data.catalog.ProviderSecrets
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.network.UserAgentProfile
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import javax.inject.Inject

/** Nagłówek User-Agent wybrany dla listy: gotowy profil albo własny. Domyślny (`AUTO`) to okhttp/4.10.0. */
interface ProviderHeadersRepository {
    fun profile(providerId: String): Flow<UserAgentProfile>

    suspend fun current(providerId: String): UserAgentProfile

    suspend fun set(
        providerId: String,
        profile: UserAgentProfile,
    )

    /** Własne User-Agenty użytkownika (kolejność dodania); wartości są w `SecretStore`. */
    val customs: Flow<List<UserAgentProfile>>

    /** Dodaje wpis po walidacji (drukowalne ASCII, do 512 znaków); false = odrzucony. */
    suspend fun addCustom(value: String): Boolean

    suspend fun removeCustom(value: String)
}

/**
 * Wybór profilu trzyma Room (`providers.userAgent`: id profilu albo `custom`); wartość własnego User-Agenta, jako dane
 * dostępu, tylko `SecretStore` ([ProviderSecrets]).
 */
class RoomProviderHeadersRepository
    @Inject
    constructor(
        private val providers: ProviderDao,
        private val secrets: ProviderSecrets,
    ) : ProviderHeadersRepository {
        private val customChanges = kotlinx.coroutines.flow.MutableStateFlow(0)

        override fun profile(providerId: String): Flow<UserAgentProfile> =
            providers.observeUserAgent(providerId).map { resolve(providerId, it) }

        override suspend fun current(providerId: String): UserAgentProfile = profile(providerId).first()

        override suspend fun set(
            providerId: String,
            profile: UserAgentProfile,
        ) {
            val id = ProviderId(providerId)
            when {
                profile == UserAgentProfiles.AUTO -> {
                    providers.setUserAgent(providerId, null)
                    secrets.removeUserAgent(id)
                }
                profile.id.startsWith(CUSTOM) -> {
                    val value = UserAgentProfiles.validated(profile.value.orEmpty()) ?: return
                    secrets.putUserAgent(id, value)
                    providers.setUserAgent(providerId, CUSTOM_MARK)
                }
                else -> {
                    providers.setUserAgent(providerId, profile.id)
                    secrets.removeUserAgent(id)
                }
            }
        }

        override val customs: Flow<List<UserAgentProfile>> =
            customChanges.map { secrets.customUserAgents().map(UserAgentProfiles::custom) }

        override suspend fun addCustom(value: String): Boolean {
            val clean = UserAgentProfiles.validated(value) ?: return false
            val list = secrets.customUserAgents()
            if (clean !in list) secrets.putCustomUserAgents(list + clean)
            customChanges.value++
            return true
        }

        override suspend fun removeCustom(value: String) {
            secrets.putCustomUserAgents(secrets.customUserAgents().filter { it != value })
            customChanges.value++
        }

        private suspend fun resolve(
            providerId: String,
            stored: String?,
        ): UserAgentProfile =
            when {
                stored == null -> UserAgentProfiles.AUTO
                stored == CUSTOM_MARK ->
                    secrets.userAgent(ProviderId(providerId))?.let(UserAgentProfiles::custom)
                        ?: UserAgentProfiles.AUTO
                else -> UserAgentProfiles.byId(stored)
            }

        private companion object {
            const val CUSTOM = "custom"
            const val CUSTOM_MARK = "custom"
        }
    }
