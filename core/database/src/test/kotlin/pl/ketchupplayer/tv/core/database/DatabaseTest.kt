package pl.ketchupplayer.tv.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: KetchupDatabase

    @Before
    fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KetchupDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private fun provider(id: String = "p1") = ProviderEntity(id, "Lista", "M3U", "http://l.example.invalid/x.m3u")

    private fun channel(
        id: String,
        name: String,
        number: Int?,
        group: String? = "G",
        providerId: String = "p1",
    ) = ChannelEntity(
        id,
        providerId,
        name,
        name.lowercase(),
        number,
        null,
        null,
        group,
        "LIVE",
        "http://h.example.invalid/$id",
        null,
    )

    @Test
    fun `replaceAll podmienia liste dostawcy atomowo`() =
        runTest {
            db.providers().upsert(provider())
            db.channels().replaceAll("p1", listOf(channel("a", "A", 2), channel("b", "B", 1)))
            assertEquals(
                listOf("B", "A"),
                db
                    .channels()
                    .observeByType("LIVE")
                    .first()
                    .map { it.name },
            )
            db.channels().replaceAll("p1", listOf(channel("c", "C", 5)))
            assertEquals(
                listOf("C"),
                db
                    .channels()
                    .observeByType("LIVE")
                    .first()
                    .map { it.name },
            )
        }

    @Test
    fun `usuniecie dostawcy kasuje jego kanaly`() =
        runTest {
            db.providers().upsert(provider())
            db.channels().replaceAll("p1", listOf(channel("a", "A", 1)))
            db.providers().delete("p1")
            assertEquals(0, db.channels().count("p1"))
        }

    @Test
    fun `kanaly bez numeru sa na koncu, grupy sa unikalne`() =
        runTest {
            db.providers().upsert(provider())
            db.channels().replaceAll(
                "p1",
                listOf(channel("a", "Zeta", null, "X"), channel("b", "Beta", 7, "X"), channel("c", "Alfa", 3, "Y")),
            )
            assertEquals(
                listOf("Alfa", "Beta", "Zeta"),
                db
                    .channels()
                    .observeByType("LIVE")
                    .first()
                    .map { it.name },
            )
            assertEquals(listOf("X", "Y"), db.channels().observeGroups("LIVE").first())
        }

    @Test
    fun `flagi przezywaja podmiane listy`() =
        runTest {
            db.providers().upsert(provider())
            db.channels().replaceAll("p1", listOf(channel("a", "A", 1)))
            db.channelFlags().upsert(ChannelFlagEntity("a", favorite = true))
            db.channels().replaceAll("p1", listOf(channel("a", "A", 1)))
            assertEquals(
                true,
                db
                    .channelFlags()
                    .observeAll()
                    .first()
                    .single()
                    .favorite,
            )
        }

    @Test
    fun `markRefreshed zapisuje czas i zachowuje adres EPG gdy brak nowego`() =
        runTest {
            db.providers().upsert(provider().copy(epgUrl = "https://e.example.invalid/x"))
            db.providers().markRefreshed("p1", 123L, null, null)
            val p = db.providers().get("p1")!!
            assertEquals(123L, p.lastRefreshMs)
            assertEquals("https://e.example.invalid/x", p.epgUrl)
            assertNull(p.lastError)
        }
}
