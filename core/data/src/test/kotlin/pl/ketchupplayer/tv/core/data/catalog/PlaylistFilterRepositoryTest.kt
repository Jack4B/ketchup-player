package pl.ketchupplayer.tv.core.data.catalog

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.ProviderId
import java.io.File

/** Wybór listy na ekranach: nazwy użytkownika, osobno dla ekranu, usunięta lista wraca do „Wszystkie”. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistFilterRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: KetchupDatabase
    private lateinit var repo: DataStorePlaylistFilterRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    KetchupDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        repo =
            DataStorePlaylistFilterRepository(
                PreferenceDataStoreFactory.create {
                    File(tmp.root, "p.preferences_pb")
                },
                db.providers(),
            )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun wyborJestOsobnyDlaEkranuAUsunietaListaWracaDoWszystkich() =
        runTest {
            assertFalse("jedna lista albo żadna: wybór niepotrzebny", repo.choice(PlaylistScreen.TV).first().available)
            db.providers().upsert(ProviderEntity("a", "Dom", "M3U", "http://a.example.invalid"))
            db.providers().upsert(
                ProviderEntity("b", "Sport u znajomego", "M3U", "http://b.example.invalid", sortOrder = 1),
            )
            val start = repo.choice(PlaylistScreen.TV).first()
            assertTrue(start.available)
            assertEquals(listOf("Dom", "Sport u znajomego"), start.options.map { it.name })
            assertNull(start.selected)

            repo.select(PlaylistScreen.TV, ProviderId("b"))
            assertEquals("Sport u znajomego", repo.choice(PlaylistScreen.TV).first().selectedName)
            assertNull("Filmy mają swój wybór", repo.choice(PlaylistScreen.MOVIES).first().selected)

            db.providers().delete("b")
            val after = repo.choice(PlaylistScreen.TV).first()
            assertNull("usunięta lista: znowu wszystkie", after.selected)
            assertFalse(after.available)
        }
}
