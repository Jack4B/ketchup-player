package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.security.SecretStore
import javax.inject.Inject

/** Dane dostawcy, które nie mogą trafić do bazy: pełny adres listy, login/hasło, adres EPG z danymi logowania. */
class ProviderSecrets
    @Inject
    constructor(
        private val store: SecretStore,
    ) {
        fun ref(id: ProviderId) = "provider.${id.value}"

        suspend fun putUrl(
            id: ProviderId,
            url: String,
        ) = store.put(key(id, URL), url)

        suspend fun url(id: ProviderId): String? = store.get(key(id, URL))

        suspend fun putLogin(
            id: ProviderId,
            user: String,
            pass: String,
        ) {
            store.put(key(id, USER), user)
            store.put(key(id, PASS), pass)
        }

        suspend fun login(id: ProviderId): Pair<String, String>? {
            val user = store.get(key(id, USER)) ?: return null
            val pass = store.get(key(id, PASS)) ?: return null
            return user to pass
        }

        suspend fun putEpg(
            id: ProviderId,
            url: String,
        ) = store.put(key(id, EPG), url)

        suspend fun epg(id: ProviderId): String? = store.get(key(id, EPG))

        suspend fun clear(id: ProviderId) = listOf(URL, USER, PASS, EPG).forEach { store.remove(key(id, it)) }

        private fun key(
            id: ProviderId,
            part: String,
        ) = "${ref(id)}.$part"

        private companion object {
            const val URL = "url"
            const val USER = "user"
            const val PASS = "pass"
            const val EPG = "epg"
        }
    }
