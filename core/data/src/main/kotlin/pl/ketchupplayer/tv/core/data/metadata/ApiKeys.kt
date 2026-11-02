package pl.ketchupplayer.tv.core.data.metadata

import pl.ketchupplayer.tv.core.security.SecretStore
import javax.inject.Inject
import javax.inject.Singleton

/** Klucze API do metadanych (osobiste, darmowe: TMDB i OMDb). */
data class ApiKeys(
    val tmdb: String = "",
    val omdb: String = "",
) {
    val any: Boolean get() = tmdb.isNotBlank() || omdb.isNotBlank()
}

/** Klucze wbudowane w kompilację (z sekretów CI); puste, gdy kompilacja ich nie dostała. Nigdy w repozytorium. */
fun interface DefaultApiKeys {
    fun keys(): ApiKeys
}

/**
 * Klucze użytkownika (Ustawienia → Oceny i opisy) trzymane w [SecretStore]; pusty wpis oznacza klucz wbudowany.
 */
@Singleton
class MetadataKeys
    @Inject
    constructor(
        private val secrets: SecretStore,
        private val defaults: DefaultApiKeys,
    ) {
        suspend fun current(): ApiKeys {
            val d = defaults.keys()
            return ApiKeys(
                tmdb = secrets.get(TMDB).orEmpty().ifBlank { d.tmdb },
                omdb = secrets.get(OMDB).orEmpty().ifBlank { d.omdb },
            )
        }

        /** Wpisane przez użytkownika (bez wbudowanych), do pól w Ustawieniach. */
        suspend fun userEntered(): ApiKeys = ApiKeys(secrets.get(TMDB).orEmpty(), secrets.get(OMDB).orEmpty())

        suspend fun save(keys: ApiKeys) {
            store(TMDB, keys.tmdb.trim())
            store(OMDB, keys.omdb.trim())
        }

        private suspend fun store(
            name: String,
            value: String,
        ) = if (value.isEmpty()) secrets.remove(name) else secrets.put(name, value)

        private companion object {
            const val TMDB = "metadata_tmdb_key"
            const val OMDB = "metadata_omdb_key"
        }
    }
