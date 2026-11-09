package pl.ketchupplayer.tv.core.data.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import pl.ketchupplayer.tv.core.data.catalog.ProviderSecrets
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import javax.inject.Inject

/**
 * Jednorazowe (i powtarzalne bez skutków ubocznych) przeniesienie wyboru User-Agent z DataStore (`ua_profile_<id>`,
 * `ua_custom_list`, jawny tekst) do nowego modelu: Room `providers.userAgent` i SecretStore.
 *
 * Zasady:
 * - Stary model zapisywał klucz TYLKO przy świadomym wyborze (domyślny profil usuwał klucz), więc obecność klucza to
 *   wybór użytkownika, a jego brak to „bez ustawienia” (lista dostaje nowy domyślny okhttp/4.10.0).
 * - Wybrany gotowy profil (także OTT Navigator) i własny User-Agent przechodzą bez zmian wartości.
 * - Nowszy wybór w Room nigdy nie jest nadpisywany starym; klucz i tak jest wtedy usuwany.
 * - Klucz znika dopiero po przeniesieniu (albo gdy lista już nie istnieje), więc ponowne uruchomienie nic nie psuje.
 */
class LegacyUserAgentMigration
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
        private val providers: ProviderDao,
        private val headers: ProviderHeadersRepository,
        private val secrets: ProviderSecrets,
    ) {
        suspend fun migrate() {
            val prefs = store.data.first()
            val legacy = prefs.asMap().filterKeys { it.name.startsWith(PROFILE_PREFIX) }
            val savedCustoms = prefs[CUSTOMS].orEmpty()
            if (legacy.isEmpty() && savedCustoms.isEmpty()) return
            moveSavedCustoms(savedCustoms)
            for ((key, value) in legacy) {
                val providerId = key.name.removePrefix(PROFILE_PREFIX)
                moveChoice(providerId, value as? String)
                store.edit { it.remove(stringPreferencesKey(key.name)) }
            }
            if (savedCustoms.isNotEmpty()) store.edit { it.remove(CUSTOMS) }
        }

        private suspend fun moveSavedCustoms(text: String) {
            if (text.isEmpty()) return
            val old = text.split('\n').mapNotNull { UserAgentProfiles.validated(it) }
            val current = secrets.customUserAgents()
            secrets.putCustomUserAgents(current + old.filter { it !in current }.distinct())
        }

        private suspend fun moveChoice(
            providerId: String,
            legacyId: String?,
        ) {
            val entity = providers.get(providerId) ?: return // lista już nie istnieje: klucz do usunięcia
            if (entity.userAgent != null || legacyId == null) return // nowszy wybór wygrywa
            val profile = UserAgentProfiles.byId(legacyId)
            if (profile != UserAgentProfiles.AUTO) headers.set(providerId, profile)
        }

        private companion object {
            const val PROFILE_PREFIX = "ua_profile_"
            val CUSTOMS = stringPreferencesKey("ua_custom_list")
        }
    }
