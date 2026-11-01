package pl.ketchupplayer.tv.core.data.groups

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.database.ChannelEntity
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.ProviderEntity
import java.io.File

/** Scenariusz: lista z wieloma krajami; użytkownik zostawia tylko polskie grupy, wybór przeżywa odczyt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: KetchupDatabase
    private lateinit var repo: DataStoreGroupsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, KetchupDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo =
            DataStoreGroupsRepository(
                PreferenceDataStoreFactory.create { File(tmp.root, "g.preferences_pb") },
                db.channels(),
                db.vod(),
            )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.providers().upsert(ProviderEntity("p", "Lista", "M3U", "http://x.invalid/l.m3u"))
        db.channels().insertAll(
            listOf(
                channel("1", "TVN", "PL | Info"),
                channel("2", "TVP", "PL | Info"),
                channel("3", "BBC", "UK | News"),
            ),
        )
    }

    private fun channel(
        id: String,
        name: String,
        group: String,
    ) = ChannelEntity(
        id = id,
        providerId = "p",
        type = "LIVE",
        name = name,
        normName = name.lowercase(),
        number = null,
        epgId = null,
        logo = null,
        groupName = group,
        streamUrl = "http://x.invalid/$id",
        userAgent = null,
    )

    @Test
    fun `grupy maja liczby, ukrycie i pokazanie wszystkich dzialaja na calym rodzaju`() =
        runTest {
            seed()
            assertEquals(
                listOf("PL | Info" to 2, "UK | News" to 1),
                repo.options(GroupKind.TV).first().map { it.name to it.count },
            )
            repo.setVisible(GroupKind.TV, "UK | News", false)
            assertEquals(setOf("UK | News"), repo.hidden(GroupKind.TV).first())
            assertEquals(emptySet<String>(), repo.hidden(GroupKind.MOVIES).first())
            repo.setAllVisible(GroupKind.TV, false)
            assertEquals(2, repo.hidden(GroupKind.TV).first().size)
            repo.setAllVisible(GroupKind.TV, true)
            assertEquals(emptySet<String>(), repo.hidden(GroupKind.TV).first())
        }
}
