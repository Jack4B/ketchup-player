package pl.ketchupplayer.tv.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migracja 9 -> 10 (tabele personalizacji i nowe kolumny `vod_meta`): baza utworzona według zapisanego schematu 9,
 * z listą, filmem, jego oceną, Moją listą i postępem, jest otwierana przez Room w wersji 10. Dotychczasowe dane
 * zostają, nowe tabele są puste i działają. Dane wyłącznie fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonalizationMigrationTest {
    private fun createVersion9(file: File) {
        val schema = File("schemas/pl.ketchupplayer.tv.core.database.KetchupDatabase/9.json")
        val database = JSONObject(schema.readText()).getJSONObject("database")
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = database.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
        raw.execSQL(
            "INSERT INTO providers (id, name, type, url, sortOrder, requestMethod) " +
                "VALUES ('old', 'Stara lista', 'M3U', 'http://l.example.invalid/x.m3u', 0, 'GET')",
        )
        raw.execSQL(
            "INSERT INTO vod_items (id, providerId, kind, title, normTitle, ref, genre) " +
                "VALUES ('old:m1', 'old', 'MOVIE', 'Stary film', 'stary film', 'r1', 'Dramat')",
        )
        raw.execSQL(
            "INSERT INTO vod_meta (itemId, tmdbRating, fetchedMs, found) VALUES ('old:m1', 7.5, 1000, 1)",
        )
        raw.execSQL(
            "INSERT INTO vod_state (itemId, myList, positionMs, durationMs) VALUES ('old:m1', 1, 60000, 6000000)",
        )
        raw.version = 9
        raw.close()
    }

    @Test
    fun `baza z wersji 9 zachowuje dane i dostaje puste tabele personalizacji`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-9-10.db"
            context.deleteDatabase(name)
            val file = context.getDatabasePath(name)
            file.parentFile?.mkdirs()
            createVersion9(file)

            val db = Room.databaseBuilder(context, KetchupDatabase::class.java, name).allowMainThreadQueries().build()
            try {
                assertEquals("Stara lista", db.providers().get("old")?.name)
                assertEquals("Stary film", db.vod().get("old:m1")?.title)
                val meta = db.vod().meta("old:m1")
                assertEquals(7.5, meta?.tmdbRating ?: 0.0, 0.0)
                assertNull("nowe kolumny są puste dla starych wierszy", meta?.director)
                assertNull(meta?.tmdbMatch)
                val state = db.vod().state("old:m1")
                assertEquals(true, state?.myList)
                assertEquals(60_000L, state?.positionMs)

                val p = db.personalization()
                assertEquals(0, p.sessionCount())
                assertTrue(p.features("household").isEmpty())
                p.putFeedback(ExplicitFeedbackEntity("household", "VOD", "old:m1", "LIKE", 5))
                assertEquals(1, p.feedback("household").size)
                p.putMeta(PersonalizationMetaEntity("household", "k", "v", 5))
                assertEquals("v", p.meta("household", "k"))
                val source = p.featureSource("old:m1")
                assertNotNull(source)
                assertEquals("Dramat", source?.genre)
                assertTrue(db.sport().teams().isEmpty())
                assertTrue(p.excludedVodItems("household").contains("old:m1"))
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    @Test
    fun `sesje maja unikalny klucz deduplikacji a czyszczenie nie rusza katalogu`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, KetchupDatabase::class.java).allowMainThreadQueries().build()
            try {
                val p = db.personalization()
                db.providers().upsert(ProviderEntity("p", "Lista", "M3U", "http://l.example.invalid/y.m3u"))

                fun session(id: String) =
                    WatchSessionEntity(
                        id = id,
                        profileId = "household",
                        providerId = "p",
                        itemId = "f",
                        contentKind = "MOVIE",
                        seriesId = null,
                        programmeStartMs = null,
                        programmeEndMs = null,
                        programmeTitleNorm = null,
                        startedAtMs = 1,
                        lastEventAtMs = 2,
                        endedAtMs = null,
                        status = "OPEN",
                        activeWatchMs = 0,
                        wallWatchMs = 0,
                        pauseMs = 0,
                        bufferMs = 0,
                        seekCount = 0,
                        rebufferCount = 0,
                        maxPositionMs = 0,
                        durationMs = null,
                        startReason = "USER",
                        endReason = null,
                        evidenceClass = "NEUTRAL",
                        sourceVersion = 1,
                        dedupKey = "klucz",
                    )
                p.putSession(session("a"))
                p.putSession(session("b"))
                assertEquals("ten sam klucz = jeden wiersz", 1, p.sessionCount())
                assertEquals("b", p.sessionByKey("household", "klucz")?.id)
                assertEquals(1, p.openSessions().size)
                p.clearLearned()
                assertEquals(0, p.sessionCount())
                assertNotNull(db.providers().get("p"))
            } finally {
                db.close()
            }
        }
}
