package pl.ketchupplayer.tv.core.data.network

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.data.catalog.ProviderSecrets
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.network.NetworkClients
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import pl.ketchupplayer.tv.core.security.InMemorySecretStore
import java.io.File

/** Aktualizacja istniejącej instalacji: wybór User-Agent z DataStore przechodzi do Room i SecretStore bez utraty. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyUserAgentMigrationTest {
    private lateinit var db: KetchupDatabase
    private val store = InMemorySecretStore()
    private val secrets = ProviderSecrets(store)
    private val prefs =
        PreferenceDataStoreFactory.create { File.createTempFile("legacy", ".preferences_pb").also { it.delete() } }

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    KetchupDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = db.close()

    private val headers get() = RoomProviderHeadersRepository(db.providers(), secrets)

    private fun migration() = LegacyUserAgentMigration(prefs, db.providers(), headers, secrets)

    private suspend fun seedInstall() {
        listOf("p1", "p2", "p3").forEach { db.providers().upsert(ProviderEntity(it, it, "M3U", "http://h.invalid")) }
        prefs.edit {
            it[stringPreferencesKey("ua_profile_p1")] = "ott" // świadomy wybór OTT Navigator
            it[stringPreferencesKey("ua_profile_p2")] = "custom:MojAgent/2 token-xyz"
            it[stringPreferencesKey("ua_profile_ghost")] = "vlc" // lista usunięta
            it[stringPreferencesKey("ua_custom_list")] = "Zapisany/1\nMojAgent/2 token-xyz"
        }
    }

    @Test
    fun `wybor i wlasny User-Agent przechodza bez resetu do okhttp, brak ustawienia zostaje domyslny`() =
        runTest {
            seedInstall()
            migration().migrate()

            assertEquals("ott", db.providers().get("p1")?.userAgent)
            assertEquals(NetworkClients.OTT_USER_AGENT, headers.current("p1").value)
            assertEquals("custom", db.providers().get("p2")?.userAgent)
            assertEquals("MojAgent/2 token-xyz", headers.current("p2").value)
            assertFalse(
                db
                    .providers()
                    .get("p2")
                    .toString()
                    .contains("token-xyz"),
            )
            assertNull("brak klucza = brak ustawienia", db.providers().get("p3")?.userAgent)
            assertNull(headers.current("p3").value) // domyślny okhttp/4.10.0 (null = klient użyje domyślnego)
            assertEquals(listOf("Zapisany/1", "MojAgent/2 token-xyz"), secrets.customUserAgents())
        }

    @Test
    fun `stare klucze znikaja, a ponowne uruchomienie nic nie zmienia`() =
        runTest {
            seedInstall()
            migration().migrate()
            val names =
                prefs.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }
            assertFalse(names.toString(), names.any { it.startsWith("ua_profile_") || it == "ua_custom_list" })

            migration().migrate()
            migration().migrate()
            assertEquals("ott", db.providers().get("p1")?.userAgent)
            assertEquals("MojAgent/2 token-xyz", headers.current("p2").value)
            assertEquals(listOf("Zapisany/1", "MojAgent/2 token-xyz"), secrets.customUserAgents())
        }

    @Test
    fun `nowszy wybor w Room nie jest nadpisywany starym kluczem`() =
        runTest {
            seedInstall()
            headers.set("p1", UserAgentProfiles.TIVIMATE)
            migration().migrate()
            assertEquals("tivimate", db.providers().get("p1")?.userAgent)
            val names =
                prefs.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }
            assertFalse(names.contains("ua_profile_p1"))
        }

    @Test
    fun `pusty DataStore nic nie robi`() =
        runTest {
            db.providers().upsert(ProviderEntity("p1", "p1", "M3U", "http://h.invalid"))
            migration().migrate()
            assertNull(db.providers().get("p1")?.userAgent)
            assertEquals(emptyList<String>(), secrets.customUserAgents())
            assertEquals(ProviderId("p1"), ProviderId("p1"))
        }
}
