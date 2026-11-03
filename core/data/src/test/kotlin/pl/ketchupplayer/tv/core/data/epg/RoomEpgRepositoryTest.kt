package pl.ketchupplayer.tv.core.data.epg

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.catalog.ProviderSecrets
import pl.ketchupplayer.tv.core.database.ChannelEntity
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.EpgSourceKind
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.core.network.StreamFetcher
import pl.ketchupplayer.tv.core.security.InMemorySecretStore
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private class FakeStreams(
    val bodies: Map<String, String> = emptyMap(),
) : StreamFetcher {
    val opened = mutableListOf<String>()

    override suspend fun <T> open(
        url: String,
        block: (InputStream) -> T,
    ): Result<T> {
        opened += url
        val body = bodies[url] ?: return Result.failure(NetworkException.Http(404))
        return Result.success(block(body.byteInputStream()))
    }
}

private class FakeSources(
    initial: List<EpgSource>,
) : EpgSourcesRepository {
    override val sources = MutableStateFlow(initial)

    override suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) = Unit

    override suspend fun addCustom(
        name: String,
        url: String,
        providerId: ProviderId?,
    ) = AddSourceResult.ADDED

    override suspend fun removeCustom(id: String) = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomEpgRepositoryTest {
    private lateinit var db: KetchupDatabase
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val ovh = BuiltInEpgSources.OVH_URL
    private val secrets = ProviderSecrets(InMemorySecretStore())
    private val prefs by lazy {
        PreferenceDataStoreFactory.create { File.createTempFile("epg", ".preferences_pb").also { it.delete() } }
    }

    @Before
    fun open() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    KetchupDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun close() = db.close()

    private fun fmt(ms: Long): String =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").format(Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC))

    private fun xml(vararg progs: Triple<String, Long, Long>): String =
        "<tv><channel id=\"jeden.pl\"><display-name>JEDEN HD</display-name></channel>" +
            "<channel id=\"obcy\"><display-name>Obcy</display-name></channel>" +
            progs.joinToString("") { (ch, s, e) ->
                "<programme start=\"${fmt(s)}\" stop=\"${fmt(e)}\" channel=\"$ch\"><title>T-$s</title></programme>"
            } + "</tv>"

    private suspend fun seedChannel() {
        db.providers().upsert(ProviderEntity("p", "P", ProviderType.M3U.name, "http://h.example.invalid"))
        db.channels().replaceAll(
            "p",
            listOf(ChannelEntity("p:1", "p", "JEDEN HD", "jeden hd", 1, "jeden.pl", null, null, "LIVE", "u", null)),
        )
    }

    private fun repo(
        streams: StreamFetcher,
        sources: List<EpgSource> = listOf(BuiltInEpgSources.ovh()),
    ) = RoomEpgRepository(
        db.epg(),
        db.channels(),
        EpgUrlProvider(FakeSources(sources), db.providers(), secrets),
        streams,
        Clock { now },
        prefs,
    )

    @Test
    fun `import dopasowuje kanal i pokazuje audycje trwajaca teraz`() =
        runTest {
            seedChannel()
            val body =
                xml(
                    Triple("jeden.pl", now - hour, now + hour),
                    Triple("jeden.pl", now + hour, now + 2 * hour),
                    Triple("obcy", now - hour, now + hour),
                )
            val r = repo(FakeStreams(mapOf(ovh to body)))
            val report = r.refresh().getOrThrow()
            assertEquals(2, report.programmes)
            assertEquals(1, report.matchedChannels)
            assertEquals(
                "T-${now - hour}",
                r
                    .onAir(now)
                    .first()
                    .getValue("p:1")
                    .title,
            )
            assertEquals(2, r.range("p:1", now - 2 * hour, now + 3 * hour).size)
        }

    @Test
    fun `audycje spoza horyzontu sa odrzucane`() =
        runTest {
            seedChannel()
            val day = 24 * hour
            val body =
                xml(
                    Triple("jeden.pl", now - 10 * day, now - 9 * day),
                    Triple(
                        "jeden.pl",
                        now + 10 * day,
                        now + 11 * day,
                    ),
                )
            assertEquals(0, repo(FakeStreams(mapOf(ovh to body))).refresh().getOrThrow().programmes)
        }

    @Test
    fun `blad wszystkich zrodel zostawia stary przewodnik`() =
        runTest {
            seedChannel()
            repo(FakeStreams(mapOf(ovh to xml(Triple("jeden.pl", now - hour, now + hour))))).refresh().getOrThrow()
            assertTrue(repo(FakeStreams()).refresh().isFailure)
            assertEquals(1, db.epg().count())
        }

    @Test
    fun `pomija wylaczone zrodla i Teleman, uzywa wlasnych i adresow dostawcow`() =
        runTest {
            seedChannel()
            val providerEpg = "http://prov.example.invalid/xmltv.php?username=u&password=p"
            secrets.putEpg(ProviderId("p"), providerEpg)
            val custom = EpgSource("c", "Moje", EpgSourceKind.CUSTOM_XMLTV, "https://moje.example.invalid/e.xml", true)
            val sources =
                listOf(BuiltInEpgSources.ovh(enabled = false), BuiltInEpgSources.teleman(enabled = true), custom)
            val fake = FakeStreams()
            repo(fake, sources).refresh()
            assertEquals(setOf("https://moje.example.invalid/e.xml", providerEpg), fake.opened.toSet())
        }

    @Test
    fun `brak wlaczonych zrodel to blad`() =
        runTest {
            val r = repo(FakeStreams(), listOf(BuiltInEpgSources.ovh(enabled = false)))
            assertTrue(r.refresh().isFailure)
        }

    @Test
    fun `refreshIfStale odswieza tylko gdy dane sa stare`() =
        runTest {
            seedChannel()
            val fake = FakeStreams(mapOf(ovh to xml(Triple("jeden.pl", now - hour, now + hour))))
            val r = repo(fake)
            assertNotNull(r.refreshIfStale(6 * hour))
            assertNull(r.refreshIfStale(6 * hour))
            assertEquals(1, fake.opened.size)
        }
}
