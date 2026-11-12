package pl.ketchupplayer.tv.core.data.network

import android.content.Context
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.data.catalog.ProviderSecrets
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import pl.ketchupplayer.tv.core.security.InMemorySecretStore

/**
 * Scenariusz: User-Agent listy w Room (`providers.userAgent`), własny jako dane dostępu w `SecretStore`;
 * domyślny to okhttp/4.10.0. Dane wyłącznie fikcyjne.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderHeadersRepositoryTest {
    private lateinit var db: KetchupDatabase
    private val store = InMemorySecretStore()
    private val secrets = ProviderSecrets(store)

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

    private suspend fun repo(): RoomProviderHeadersRepository {
        db.providers().upsert(ProviderEntity("p1", "Lista", "M3U", "http://l.example.invalid"))
        return RoomProviderHeadersRepository(db.providers(), secrets)
    }

    @Test
    fun `lista bez wyboru ma domyslny User-Agent okhttp`() =
        runTest {
            val r = repo()
            assertEquals(UserAgentProfiles.AUTO, r.current("p1"))
            assertNull(r.current("p1").value) // null = klient użyje okhttp/4.10.0
            assertNull(db.providers().get("p1")?.userAgent)
        }

    @Test
    fun `gotowy profil trafia do Room jako id`() =
        runTest {
            val r = repo()
            r.set("p1", UserAgentProfiles.VLC)
            assertEquals("vlc", db.providers().get("p1")?.userAgent)
            assertEquals("VLC/3.0.20 LibVLC/3.0.20", r.current("p1").value)
            r.set("p1", UserAgentProfiles.AUTO)
            assertNull(db.providers().get("p1")?.userAgent)
        }

    @Test
    fun `wlasny User-Agent mozna dodac wybrac dla listy i usunac`() =
        runTest {
            val r = repo()
            assertTrue(r.addCustom("  PremierLeagueModPlayer "))
            assertTrue(r.addCustom("Wink/1.31.1"))
            assertTrue(r.addCustom("Wink/1.31.1")) // duplikat nie dubluje
            assertEquals(listOf("PremierLeagueModPlayer", "Wink/1.31.1"), r.customs.first().map { it.label })

            r.set("p1", UserAgentProfiles.custom("Wink/1.31.1"))
            assertEquals("Wink/1.31.1", r.current("p1").value)

            r.removeCustom("Wink/1.31.1")
            assertEquals(listOf("PremierLeagueModPlayer"), r.customs.first().map { it.label })
        }

    /** Własny User-Agent jest jak dane dostępu: w bazie tylko znacznik, wartość w SecretStore, nie w toString. */
    @Test
    fun `wlasny User-Agent nie trafia do bazy ani do toString`() =
        runTest {
            val r = repo()
            val secret = "MojaAplikacja/9.9 token-abc123"
            r.set("p1", UserAgentProfiles.custom(secret))
            assertEquals("custom", db.providers().get("p1")?.userAgent)
            assertFalse(
                db
                    .providers()
                    .get("p1")
                    .toString()
                    .contains("abc123"),
            )
            assertEquals(secret, secrets.userAgent(ProviderId("p1")))
            assertEquals(secret, r.current("p1").value)
            assertFalse(r.current("p1").toString().contains("abc123"))
            assertFalse(UserAgentProfiles.custom(secret).toString().contains("abc123"))
            // Zmiana na gotowy profil usuwa własną wartość z SecretStore.
            r.set("p1", UserAgentProfiles.TIVIMATE)
            assertNull(secrets.userAgent(ProviderId("p1")))
        }

    @Test
    fun `niepoprawne wpisy sa odrzucane jak w BK Playerze`() =
        runTest {
            val r = repo()
            assertFalse(r.addCustom("   "))
            assertFalse(r.addCustom("zażółć"))
            assertFalse(r.addCustom("a".repeat(513)))
            assertFalse(r.addCustom("linia1\nlinia2"))
            assertEquals(emptyList<String>(), r.customs.first())
        }
}
