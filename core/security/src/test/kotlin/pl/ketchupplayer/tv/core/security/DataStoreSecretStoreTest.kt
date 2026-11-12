package pl.ketchupplayer.tv.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator

class DataStoreSecretStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun file() = File(tmp.root, "secrets.preferences_pb")

    private val dataStore by lazy { PreferenceDataStoreFactory.create { file() } }

    private fun store(k: javax.crypto.SecretKey = key()) = DataStoreSecretStore(dataStore, AesGcmCipher(k))

    @Test
    fun `zapis i odczyt`() =
        runTest {
            val s = store()
            s.put("provider.1.pass", "tajne-haslo")
            assertEquals("tajne-haslo", s.get("provider.1.pass"))
            assertNull(s.get("brak"))
        }

    @Test
    fun `w pliku nie ma jawnego hasla`() =
        runTest {
            val s = store()
            s.put("p", "SUPER-TAJNE-HASLO-123")
            val bytes = file().readBytes()
            assertTrue(bytes.isNotEmpty())
            assertFalse(String(bytes, Charsets.ISO_8859_1).contains("SUPER-TAJNE-HASLO-123"))
        }

    @Test
    fun `ten sam sekret daje rozne szyfrogramy (losowe IV)`() {
        val c = AesGcmCipher(key())
        val a = c.encrypt("x".toByteArray())
        val b = c.encrypt("x".toByteArray())
        assertFalse(a.contentEquals(b))
        assertEquals("x", String(c.decrypt(a)))
    }

    @Test
    fun `utrata klucza daje null zamiast wyjatku`() =
        runTest {
            store(key()).put("p", "v")
            assertNull(store(key()).get("p"))
        }

    @Test
    fun `remove i clear`() =
        runTest {
            val s = store()
            s.put("a", "1")
            s.put("b", "2")
            s.remove("a")
            assertNull(s.get("a"))
            s.clear()
            assertNull(s.get("b"))
        }
}
