package pl.ketchupplayer.tv

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pl.ketchupplayer.tv.core.data.playback.DataStorePlayerPrefsRepository
import pl.ketchupplayer.tv.core.policy.BufferProfile
import pl.ketchupplayer.tv.player.engine.BufferConfig
import java.io.File

class PlayerBufferModuleTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun prefs() =
        DataStorePlayerPrefsRepository(PreferenceDataStoreFactory.create { File(tmp.root, "b.preferences_pb") })

    /** Profil z Ustawień trafia do silnika jako czasy DefaultLoadControl (połowa max, start 1,5 s, wznowienie 3 s). */
    @Test
    fun `profil z ustawien jest zamieniany na czasy bufora silnika`() =
        runTest {
            val prefs = prefs()
            val source = PlayerBufferModule.bufferSource(prefs)
            assertEquals(BufferConfig(15_000, 30_000, 1_500, 3_000), source.current()) // domyślnie standardowy
            prefs.setLiveBuffer(BufferProfile.STABLE.key)
            assertEquals(BufferConfig(25_000, 50_000, 1_500, 3_000), source.current())
            prefs.setLiveBuffer(BufferProfile.FAST.key)
            assertEquals(BufferConfig(7_500, 15_000, 1_500, 3_000), source.current())
        }
}
