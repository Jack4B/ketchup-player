package pl.ketchupplayer.tv.core.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretStoreTest {
    private val store: SecretStore = InMemorySecretStore()

    @Test
    fun `put i get`() =
        runTest {
            store.put("tmdb_token", "abc")
            assertEquals("abc", store.get("tmdb_token"))
        }

    @Test
    fun `brak klucza daje null`() = runTest { assertNull(store.get("x")) }

    @Test
    fun `remove i clear`() =
        runTest {
            store.put("a", "1")
            store.put("b", "2")
            store.remove("a")
            assertNull(store.get("a"))
            store.clear()
            assertNull(store.get("b"))
        }
}
