package pl.ketchupplayer.tv.core.security

/**
 * Jedyny punkt dostępu do sekretów (hasła dostawców, token TMDB, PIN-hash). §8.2.
 * Implementacja produkcyjna (Tink + Android Keystore) powstaje w fazie D; do tego czasu [InMemorySecretStore].
 */
interface SecretStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
    suspend fun clear()
}

/** Implementacja w pamięci — do testów i faz przed integracją Keystore. Nie persystuje nic. */
class InMemorySecretStore : SecretStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
    override suspend fun put(key: String, value: String) {
        map[key] = value
    }
    override suspend fun get(key: String): String? = map[key]
    override suspend fun remove(key: String) {
        map.remove(key)
    }
    override suspend fun clear() = map.clear()
}
