package pl.ketchupplayer.tv.core.data.playback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity
import java.io.File

class FailoverSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun repo() =
        DataStoreFailoverSettingsRepository(
            PreferenceDataStoreFactory.create {
                File(tmp.root, "f.preferences_pb")
            },
        )

    @Test
    fun `domyslnie wlaczony, standard, 25 s`() =
        runTest {
            val s = repo().settings.first()
            assertEquals(true, s.enabled)
            assertEquals(FailoverSensitivity.STANDARD, s.sensitivity)
            assertEquals(25, s.tvDelaySeconds)
        }

    @Test
    fun `zapisuje przelacznik, czulosc i opoznienie`() =
        runTest {
            val r = repo()
            r.setEnabled(false)
            r.setSensitivity(FailoverSensitivity.FAST)
            r.setTvDelaySeconds(10)
            val s = r.settings.first()
            assertFalse(s.enabled)
            assertEquals(FailoverSensitivity.FAST, s.sensitivity)
            assertEquals(10, s.tvDelaySeconds)
        }

    @Test
    fun `opoznienie spoza zbioru wraca do domyslnego`() =
        runTest {
            val r = repo()
            r.setTvDelaySeconds(7)
            assertEquals(25, r.settings.first().tvDelaySeconds)
        }
}
