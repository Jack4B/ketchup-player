package pl.ketchupplayer.tv.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migracja 7 -> 8 (kolumna `requestMethod` w `providers`): baza utworzona według zapisanego schematu 7 z istniejącą
 * listą jest otwierana przez Room w wersji 8 (automatyczna migracja + walidacja schematu); dotychczasowe listy dostają
 * GET. Dane wyłącznie fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderMigrationTest {
    private val schema7 = File("schemas/pl.ketchupplayer.tv.core.database.KetchupDatabase/7.json")

    private fun createVersion7(file: File) {
        val database = JSONObject(schema7.readText()).getJSONObject("database")
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
        raw.version = 7
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
            createVersion7(file)

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
}
