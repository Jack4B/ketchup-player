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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migracje 7 -> 8 (kolumna `requestMethod`) i 8 -> 9 (kolumna `userAgent`) w `providers`: baza utworzona według
 * zapisanego schematu 7 z istniejącą
 * listą jest otwierana przez Room w wersji 8 (automatyczna migracja + walidacja schematu); dotychczasowe listy dostają
 * GET. Dane wyłącznie fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderMigrationTest {
    private fun schema(version: Int) = File("schemas/pl.ketchupplayer.tv.core.database.KetchupDatabase/$version.json")

    private fun createVersion(
        file: File,
        version: Int,
    ) {
        val database = JSONObject(schema(version).readText()).getJSONObject("database")
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
            "INSERT INTO providers (id, name, type, url, sortOrder) " +
                "VALUES ('old', 'Stara lista', 'M3U', 'http://l.example.invalid/x.m3u', 0)",
        )
        raw.version = version
        raw.close()
    }

    @Test
    fun `lista z wersji 7 dostaje GET po migracji, a metode mozna zmienic`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-7-8.db"
            context.deleteDatabase(name)
            val file = context.getDatabasePath(name)
            file.parentFile?.mkdirs()
            createVersion(file, 7)

            val db = Room.databaseBuilder(context, KetchupDatabase::class.java, name).allowMainThreadQueries().build()
            try {
                val migrated = db.providers().get("old")
                assertNotNull(migrated)
                assertEquals("Stara lista", migrated?.name)
                assertEquals("GET", migrated?.requestMethod)
                db.providers().setRequestMethod("old", "POST")
                assertEquals("POST", db.providers().get("old")?.requestMethod)
                // Nowa lista bez jawnej metody też dostaje GET.
                db.providers().upsert(ProviderEntity("new", "Nowa", "M3U", "http://l.example.invalid/y.m3u"))
                assertEquals("GET", db.providers().get("new")?.requestMethod)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    /** 8 -> 9: dotychczasowe listy mają User-Agent `null` (domyślny okhttp/4.10.0), wybór da się zapisać. */
    @Test
    fun `lista z wersji 8 dostaje domyslny User-Agent po migracji, a profil mozna zmienic`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val name = "migration-8-9.db"
            context.deleteDatabase(name)
            val file = context.getDatabasePath(name)
            file.parentFile?.mkdirs()
            createVersion(file, 8)

            val db = Room.databaseBuilder(context, KetchupDatabase::class.java, name).allowMainThreadQueries().build()
            try {
                val migrated = db.providers().get("old")
                assertNotNull(migrated)
                assertEquals("Stara lista", migrated?.name)
                assertNull(migrated?.userAgent)
                db.providers().setUserAgent("old", "vlc")
                assertEquals("vlc", db.providers().get("old")?.userAgent)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
}
