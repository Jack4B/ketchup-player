package pl.ketchupplayer.tv.core.data.playback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.ketchupplayer.tv.core.policy.BufferProfile
import java.io.File

class PlayerPrefsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() =
        DataStorePlayerPrefsRepository(
            PreferenceDataStoreFactory.create {
                File(tmp.root, "p.preferences_pb")
            },
        )

    @Test
    fun `bufor domyslnie standardowy a wybor jest trwaly`() =
        runTest {
            val r = repo()
            assertEquals(BufferProfile.STANDARD, BufferProfile.fromKey(r.liveBuffer.first()))
            r.setLiveBuffer(BufferProfile.STABLE.key)
            assertEquals(BufferProfile.STABLE, BufferProfile.fromKey(r.liveBuffer.first()))
        }

    /** Zapis ze starej wersji lub uszkodzony klucz nie psuje odtwarzania: wraca profil standardowy. */
    @Test
    fun `nieznany zapisany klucz daje profil standardowy`() =
        runTest {
            val r = repo()
            r.setLiveBuffer("cos-dziwnego")
            assertEquals(BufferProfile.STANDARD, BufferProfile.fromKey(r.liveBuffer.first()))
        }
}
