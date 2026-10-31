package pl.ketchupplayer.tv.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.Base64

/**
 * Sekrety zaszyfrowane [cipher] i zapisane w osobnym DataStore (`secrets`). W pliku nie ma jawnych wartości,
 * a przy uszkodzonym szyfrogramie (np. po utracie klucza) [get] zwraca null zamiast rzucać.
 */
class DataStoreSecretStore(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : SecretStore {
    private fun keyOf(name: String) = stringPreferencesKey("s_$name")

    override suspend fun put(
        key: String,
        value: String,
    ) {
        val encoded = Base64.getEncoder().encodeToString(cipher.encrypt(value.toByteArray(Charsets.UTF_8)))
        store.edit { it[keyOf(key)] = encoded }
    }

    override suspend fun get(key: String): String? {
        val encoded = store.data.first()[keyOf(key)] ?: return null
        return runCatching { String(cipher.decrypt(Base64.getDecoder().decode(encoded)), Charsets.UTF_8) }.getOrNull()
    }

    override suspend fun remove(key: String) {
        store.edit { it.remove(keyOf(key)) }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }
}
