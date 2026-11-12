package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.security.SecretStore
import javax.inject.Inject

/** Dane dostawcy, które nie mogą trafić do bazy: pełny adres listy, login/hasło, adres EPG z danymi logowania. */
@Suppress("TooManyFunctions") // jeden punkt dostępu do sekretów dostawcy
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

        /** Własny User-Agent listy: traktowany jak dane dostępu (nie trafia do bazy, logów ani eksportu). */
        suspend fun putUserAgent(
            id: ProviderId,
            value: String,
        ) = store.put(key(id, UA), value)

        suspend fun userAgent(id: ProviderId): String? = store.get(key(id, UA))

        suspend fun removeUserAgent(id: ProviderId) = store.remove(key(id, UA))

        /** Zapisane własne User-Agenty użytkownika (jeden wpis na linię), do wyboru dla każdej listy. */
        suspend fun customUserAgents(): List<String> =
            store
                .get(CUSTOMS)
                .orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }

        suspend fun putCustomUserAgents(values: List<String>) = store.put(CUSTOMS, values.joinToString("\n"))

        suspend fun clear(id: ProviderId) = listOf(URL, USER, PASS, EPG, UA).forEach { store.remove(key(id, it)) }

        private fun key(
            id: ProviderId,
            part: String,
        ) = "${ref(id)}.$part"

        private companion object {
            const val URL = "url"
            const val USER = "user"
            const val PASS = "pass"
            const val EPG = "epg"
            const val UA = "ua"
            const val CUSTOMS = "ua.customs"
        }
    }
