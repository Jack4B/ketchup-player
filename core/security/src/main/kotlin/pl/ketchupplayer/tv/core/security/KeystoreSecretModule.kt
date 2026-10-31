package pl.ketchupplayer.tv.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Singleton

private val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

/** Klucz AES-256 w Android Keystore (niewyciągalny); tworzony przy pierwszym użyciu. */
internal object KeystoreKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "ketchup_secrets_v1"
    private const val KEY_BITS = 256

    fun getOrCreate(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return gen.generateKey()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object KeystoreSecretModule {
    @Provides
    @Singleton
    fun secretStore(
        @ApplicationContext context: Context,
    ): SecretStore = DataStoreSecretStore(context.secretsDataStore, AesGcmCipher(KeystoreKey.getOrCreate()))
}
