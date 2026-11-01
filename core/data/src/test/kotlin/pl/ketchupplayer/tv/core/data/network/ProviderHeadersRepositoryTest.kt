package pl.ketchupplayer.tv.core.data.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import java.io.File

/** Scenariusz: użytkownik dodaje własny User-Agent, wybiera go dla listy, usuwa; błędne wpisy są odrzucane. */
class ProviderHeadersRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() =
        DataStoreProviderHeadersRepository(
            PreferenceDataStoreFactory.create { File(tmp.root, "h.pb") },
        )

    @Test
    fun `wlasny User-Agent mozna dodac wybrac dla listy i usunac`() =
        runTest {
            val r = repo()
            assertTrue(r.addCustom("  PremierLeagueModPlayer "))
            assertTrue(r.addCustom("Wink/1.31.1"))
            assertTrue(r.addCustom("Wink/1.31.1")) // duplikat nie dubluje
            assertEquals(listOf("PremierLeagueModPlayer", "Wink/1.31.1"), r.customs.first().map { it.label })

            r.set("p1", UserAgentProfiles.custom("Wink/1.31.1"))
            assertEquals("Wink/1.31.1", r.current("p1").value)

            r.removeCustom("Wink/1.31.1")
            assertEquals(listOf("PremierLeagueModPlayer"), r.customs.first().map { it.label })
        }

    @Test
    fun `niepoprawne wpisy sa odrzucane jak w BK Playerze`() =
        runTest {
            val r = repo()
            assertFalse(r.addCustom("   "))
            assertFalse(r.addCustom("zażółć"))
            assertFalse(r.addCustom("a".repeat(513)))
            assertFalse(r.addCustom("linia1\nlinia2"))
            assertEquals(emptyList<String>(), r.customs.first())
        }
}
