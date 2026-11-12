package pl.ketchupplayer.tv.core.data.groups

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private lateinit var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create { File(tmp.root, "g.preferences_pb") }
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, KetchupDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo =
            DataStoreGroupsRepository(
                store,
                db.channels(),
                db.vod(),
                db.providers(),
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
            repo.setVisible(GroupKind.TV, "p", "UK | News", false)
            assertTrue(repo.hidden(GroupKind.TV).first().hides("p", "UK | News"))
            assertTrue(repo.hidden(GroupKind.MOVIES).first().isEmpty)
            repo.setAllVisible(GroupKind.TV, false)
            assertEquals(2, repo.hidden(GroupKind.TV).first().size)
            repo.setAllVisible(GroupKind.TV, true)
            assertTrue(repo.hidden(GroupKind.TV).first().isEmpty)
        }

    private fun movie(
        id: String,
        provider: String,
        year: Int?,
    ) = pl.ketchupplayer.tv.core.database.VodItemEntity(
        id,
        provider,
        "MOVIE",
        "Matrix",
        "matrix",
        "ref",
        null,
        null,
        year,
        null,
        null,
        null,
        null,
        null,
        null,
    )

    /** rc4: ta sama nazwa grupy w dwóch listach to dwie pozycje; ukrycie w jednej liście nie chowa drugiej. */
    @Test
    fun `grupa o tej samej nazwie w dwoch listach jest osobna dla kazdej listy`() =
        runTest {
            seed()
            db.providers().upsert(ProviderEntity("q", "Druga", "M3U", "http://y.invalid/l.m3u", sortOrder = 1))
            db.channels().insertAll(listOf(channel("9", "Sport A", "UK | News").copy(id = "q9", providerId = "q")))
            val options = repo.options(GroupKind.TV).first()
            val news = options.filter { it.name == "UK | News" }
            assertEquals(listOf("p" to 1, "q" to 1), news.map { it.providerId to it.count })
            assertEquals(
                "powtarzająca się nazwa ma podpis listy",
                listOf("Lista", "Druga"),
                news.map { it.providerName },
            )
            assertEquals(null, options.single { it.name == "PL | Info" }.providerName)

            repo.setVisible(GroupKind.TV, "q", "UK | News", false)
            val hidden = repo.hidden(GroupKind.TV).first()
            assertTrue(hidden.hides("q", "UK | News"))
            assertFalse("ta sama nazwa w pierwszej liście zostaje widoczna", hidden.hides("p", "UK | News"))
        }

    /** Wybory sprzed aktualizacji (sama nazwa) nadal ukrywają grupę; pokazanie w jednej liście nie odkrywa drugiej. */
    @Test
    fun `stary wpis ukrycia po nazwie dziala dalej i rozdziela sie przy pokazaniu w jednej liscie`() =
        runTest {
            seed()
            db.providers().upsert(ProviderEntity("q", "Druga", "M3U", "http://y.invalid/l.m3u", sortOrder = 1))
            db.channels().insertAll(listOf(channel("9", "Sport A", "UK | News").copy(id = "q9", providerId = "q")))
            store.edit { it[stringSetPreferencesKey("hidden_groups_tv")] = setOf("UK | News") }
            val legacy = repo.hidden(GroupKind.TV).first()
            assertTrue(legacy.hides("p", "UK | News") && legacy.hides("q", "UK | News"))
            repo.setVisible(GroupKind.TV, "p", "UK | News", true)
            val split = repo.hidden(GroupKind.TV).first()
            assertFalse(split.hides("p", "UK | News"))
            assertTrue("w drugiej liście grupa zostaje ukryta", split.hides("q", "UK | News"))
        }

    /** Ten sam film w dwóch listach = 2 źródła; inny rok albo ta sama lista dwa razy nie zwiększają licznika. */
    @Test
    fun `liczba zrodel filmu liczy rozne listy z tym samym tytulem i rokiem`() =
        runTest {
            db.providers().upsert(ProviderEntity("p1", "A", "M3U", "http://a.invalid/l.m3u"))
            db.providers().upsert(ProviderEntity("p2", "B", "M3U", "http://b.invalid/l.m3u"))
            db.vod().insertAll(
                listOf(movie("1", "p1", 1999), movie("2", "p2", 1999), movie("3", "p2", 1999), movie("4", "p1", 2021)),
            )
            assertEquals(2, db.vod().sourceCount("MOVIE", "matrix", 1999))
            assertEquals(1, db.vod().sourceCount("MOVIE", "matrix", 2021))
        }
}
