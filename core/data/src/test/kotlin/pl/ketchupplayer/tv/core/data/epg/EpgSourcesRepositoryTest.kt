package pl.ketchupplayer.tv.core.data.epg

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
import java.io.File

class EpgSourcesRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo(name: String = "s"): DataStoreEpgSourcesRepository =
        DataStoreEpgSourcesRepository(PreferenceDataStoreFactory.create { File(tmp.root, "$name.preferences_pb") })

    @Test
    fun `domyslnie ovh i teleman wlaczone`() =
        runTest {
            val list = repo().sources.first()
            assertEquals(listOf(BuiltInEpgSources.OVH_ID, BuiltInEpgSources.TELEMAN_ID), list.map { it.id })
            assertTrue(list[0].enabled)
            assertTrue(list[1].enabled)
        }

    @Test
    fun `flagi wbudowanych zrodel sa trwale`() =
        runTest {
            val r = repo()
            r.setEnabled(BuiltInEpgSources.TELEMAN_ID, false)
            r.setEnabled(BuiltInEpgSources.OVH_ID, false)
            val list = r.sources.first()
            assertFalse(list.first { it.id == BuiltInEpgSources.TELEMAN_ID }.enabled)
            assertFalse(list.first { it.id == BuiltInEpgSources.OVH_ID }.enabled)
        }

    @Test
    fun `dodanie wlasnego zrodla i przelaczanie`() =
        runTest {
            val r = repo()
            assertEquals(AddSourceResult.ADDED, r.addCustom("Moje EPG", "https://moje.pl/epg.xml.gz"))
            val custom = r.sources.first().last()
            assertEquals("Moje EPG", custom.name)
            assertTrue(custom.enabled)
            r.setEnabled(custom.id, false)
            assertFalse(
                r.sources
                    .first()
                    .last()
                    .enabled,
            )
        }

    @Test
    fun `walidacja przy dodawaniu`() =
        runTest {
            val r = repo()
            assertEquals(AddSourceResult.INVALID_NAME, r.addCustom(" ", "https://a.pl/x"))
            assertEquals(AddSourceResult.INVALID_URL, r.addCustom("A", "ftp://a.pl/x"))
            assertEquals(AddSourceResult.DUPLICATE, r.addCustom("Kopia ovh", "https://epg.ovh/pltv.xml.gz"))
            assertEquals(2, r.sources.first().size)
        }

    @Test
    fun `usuwanie dotyczy tylko wlasnych zrodel`() =
        runTest {
            val r = repo()
            r.addCustom("A", "https://a.pl/x")
            val id =
                r.sources
                    .first()
                    .last()
                    .id
            r.removeCustom(BuiltInEpgSources.OVH_ID)
            assertEquals(3, r.sources.first().size)
            r.removeCustom(id)
            assertEquals(2, r.sources.first().size)
        }
}
