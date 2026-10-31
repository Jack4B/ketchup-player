package pl.ketchupplayer.tv.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Szyfrowanie sekretów w spoczynku. Format szyfrogramu: `IV (12 B) || ciphertext+tag`. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(data: ByteArray): ByteArray
}

/** AES-256-GCM z podanym kluczem (w aplikacji klucz żyje w Android Keystore i nie opuszcza sprzętu/TEE). */
class AesGcmCipher(
    private val key: SecretKey,
    private val random: SecureRandom = SecureRandom(),
) : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher =
            Cipher
                .getInstance(
                    TRANSFORMATION,
                ).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_BYTES) { "Zbyt krótki szyfrogram" }
        val iv = data.copyOfRange(0, IV_BYTES)
        val cipher =
            Cipher
                .getInstance(
                    TRANSFORMATION,
                ).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
