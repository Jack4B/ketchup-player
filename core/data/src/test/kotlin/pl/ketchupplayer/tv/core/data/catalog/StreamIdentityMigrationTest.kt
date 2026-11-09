package pl.ketchupplayer.tv.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.ChannelFlagEntity
import pl.ketchupplayer.tv.core.database.ChannelRef
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.VodStateEntity
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.network.NetworkClients
import pl.ketchupplayer.tv.core.network.OkHttpTextFetcher
import pl.ketchupplayer.tv.core.security.InMemorySecretStore

/** Funkcje czyste: klucz strumienia z adresu M3U i z odwołania API, plan przenoszenia id. */
class StreamIdentityTest {
    @Test
    fun `klucz z adresu M3U i z odwolania API jest ten sam`() {
        val live = StreamKey("live", "101")
        assertEquals(live, StreamIdentity.of("http://h:8080/live/user123/pass456/101.ts"))
        assertEquals(live, StreamIdentity.of("http://h:8080/user123/pass456/101"))
        assertEquals(live, StreamIdentity.of("https://h/pre/live/user123/pass456/101.m3u8".replace("/pre", "")))
        assertEquals(live, StreamIdentity.of("xtream:live:101.ts"))
        assertEquals(StreamKey("movie", "7"), StreamIdentity.of("http://h/movie/user123/pass456/7.mp4"))
        assertEquals(StreamKey("movie", "7"), StreamIdentity.of("xtream:movie:7.mkv"))
    }

    @Test
    fun `adresy bez numeru strumienia i odwolania seriali nie maja klucza`() {
        assertNull(StreamIdentity.of("http://h/stream/abc"))
        assertNull(StreamIdentity.of("http://h/live/u/p/abc.ts"))
        assertNull(StreamIdentity.of("xtream:series:9"))
        assertNull(StreamIdentity.of(null))
        assertNull(StreamIdentity.of(""))
    }

    @Test
    fun `plan przenosi tylko pary jednoznaczne`() {
        val old =
            listOf(
                ChannelRef("old1", "http://h/live/u/p/1.ts"),
                ChannelRef("old2", "http://h/live/u/p/2.ts"),
                ChannelRef("old2b", "http://h/live/u/p/2.ts"), // duplikat po stronie starej listy
                ChannelRef("old3", "http://h/live/u/p/3.ts"),
                ChannelRef("old4", "http://h/stream/abc"), // bez numeru
                ChannelRef("same", "http://h/live/u/p/5.ts"),
            )
        val new =
            listOf(
                ChannelRef("new1", "xtream:live:1.ts"),
                ChannelRef("new2", "xtream:live:2.ts"),
                ChannelRef("new3", "xtream:live:3.ts"),
                ChannelRef("new3b", "xtream:live:3.ts"), // duplikat po stronie nowej listy
                ChannelRef("new4", "xtream:live:4.ts"),
                ChannelRef("same", "xtream:live:5.ts"), // id się nie zmienia
            )
        assertEquals(mapOf("old1" to "new1"), StreamIdentity.plan(old, new))
    }
}

/**
 * Przejście listy M3U na API i z powrotem: ulubione, ukryte i postęp filmów idą za numerem strumienia (nie za nazwą,
 * API nazywa kanały inaczej), niejednoznaczne i nieudane przypadki niczego nie zmieniają ani nie kasują.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamIdentityMigrationTest {
    private lateinit var db: KetchupDatabase
    private lateinit var server: MockWebServer
    private val secrets = InMemorySecretStore()
    private var apiStatus = 404
    private var duplicate = false

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    KetchupDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        server = MockWebServer().also { it.start() }
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/get.php") -> MockResponse().setBody(playlist())
                        apiStatus != 200 -> MockResponse().setResponseCode(apiStatus)
                        "action=get_live_categories" in path -> MockResponse().setBody(CATS)
                        "action=get_live_streams" in path -> MockResponse().setBody(API_LIVE)
                        "action=get_vod_streams" in path -> MockResponse().setBody(API_VOD)
                        "action=" in path -> MockResponse().setBody("[]")
                        else -> MockResponse().setBody(AUTH)
                    }
                }
            }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun playlist(): String {
        val u = "http://127.0.0.1:${server.port}"
        val copy = "#EXTINF:-1 group-title=\"Info\",TVN 24 kopia\n$u/live/user123/pass456/101.ts\n"
        val extra = if (duplicate) copy else ""
        return "#EXTM3U\n" +
            "#EXTINF:-1 group-title=\"Info\",TVN 24\n$u/live/user123/pass456/101.ts\n" +
            "#EXTINF:-1 group-title=\"Info\",INFO24\n$u/live/user123/pass456/102.ts\n" +
            "#EXTINF:-1 group-title=\"Info\",Bez numeru\nhttp://h.example.invalid/stream/abc\n" +
            "#EXTINF:-1 group-title=\"Filmy\",Film (2019)\n$u/movie/user123/pass456/7.mp4\n" +
            "#EXTINF:-1 group-title=\"Seriale\",Serial S01E01\n$u/series/user123/pass456/55.mkv\n" + extra
    }

    private fun repo(): RoomCatalogRepository {
        val s = ProviderSecrets(secrets)
        return RoomCatalogRepository(
            db.providers(),
            db.channels(),
            db.channelFlags(),
            db.vod(),
            s,
            ProviderFactory(s, OkHttpTextFetcher(NetworkClients.stream())),
            Clock { 1_000L },
        )
    }

    private val link get() = server.url("/get.php?username=user123&password=pass456&type=m3u_plus").toString()

    private suspend fun idOf(
        repo: RoomCatalogRepository,
        name: String,
    ) = repo
        .liveChannels()
        .first()
        .first { it.name == name }
        .id

    private suspend fun flag(id: String) = db.channelFlags().get(id)

    private fun seriesId(): String =
        db.openHelper.readableDatabase.query("SELECT id FROM vod_items WHERE kind = 'SERIES'").use {
            it.moveToFirst()
            it.getString(0)
        }

    @Test
    fun `ulubione i ukryte przechodza z M3U na API i wracaja po numerze strumienia`() =
        runTest {
            val repo = repo()
            val added = repo.addM3u("Panel", link) as AddProviderResult.Added
            val p = added.id.value
            val tvnM3u = idOf(repo, "TVN 24")
            val infoM3u = idOf(repo, "INFO24")
            db.channelFlags().upsert(ChannelFlagEntity(tvnM3u, favorite = true, lastWatchedMs = 5_000))
            db.channelFlags().upsert(ChannelFlagEntity(infoM3u, hidden = true))

            apiStatus = 200 // API zaczyna działać: kolejny import idzie przez player_api.php
            assertTrue(repo.refresh(added.id).isSuccess)
            val names = repo.liveChannels().first().map { it.name }
            assertTrue(names.toString(), "TVN 24 HD" in names && "TVN 24" !in names) // API ma inne nazwy
            assertEquals(true, flag("$p:l101")?.favorite)
            assertEquals(5_000L, flag("$p:l101")?.lastWatchedMs)
            assertEquals(true, flag("$p:l102")?.hidden)
            assertNull("stare id nie zostaje z osieroconym wpisem", flag(tvnM3u))
            assertNull(flag(infoM3u))

            apiStatus = 404 // powrót do M3U (tryb zapasowy)
            assertTrue(repo.refresh(added.id).isSuccess)
            assertEquals(true, flag(tvnM3u)?.favorite)
            assertEquals(true, flag(infoM3u)?.hidden)
            assertNull(flag("$p:l101"))
        }

    @Test
    fun `nieudany import niczego nie zmienia`() =
        runTest {
            val repo = repo()
            val added = repo.addM3u("Panel", link) as AddProviderResult.Added
            val tvnM3u = idOf(repo, "TVN 24")
            db.channelFlags().upsert(ChannelFlagEntity(tvnM3u, favorite = true))
            val before =
                repo
                    .liveChannels()
                    .first()
                    .map { it.id }
                    .toSet()

            apiStatus = 401 // odmowa dostępu: import kończy się błędem, bez trybu zapasowego
            assertTrue(repo.refresh(added.id).isFailure)
            assertEquals(
                before,
                repo
                    .liveChannels()
                    .first()
                    .map { it.id }
                    .toSet(),
            )
            assertEquals(true, flag(tvnM3u)?.favorite)
        }

    @Test
    fun `duplikat numeru strumienia jest niejednoznaczny, znaczniki zostaja i nic nie jest kasowane`() =
        runTest {
            duplicate = true
            val repo = repo()
            val added = repo.addM3u("Panel", link) as AddProviderResult.Added
            val ids =
                repo
                    .liveChannels()
                    .first()
                    .filter { it.name.startsWith("TVN 24") }
                    .map { it.id }
            assertEquals(2, ids.size)
            ids.forEach { db.channelFlags().upsert(ChannelFlagEntity(it, favorite = true)) }

            apiStatus = 200
            assertTrue(repo.refresh(added.id).isSuccess)
            ids.forEach { assertEquals("zostaje pod starym id", true, flag(it)?.favorite) }
            assertNull(flag("${added.id.value}:l101"))
        }

    @Test
    fun `film z postepem przechodzi na API, serial zostaje pod starym id`() =
        runTest {
            val repo = repo()
            val added = repo.addM3u("Panel", link) as AddProviderResult.Added
            val p = added.id.value
            val movieM3u =
                db
                    .vod()
                    .movieRefs(p)
                    .single()
                    .id
            val series = seriesId()
            db.vod().putState(
                VodStateEntity(movieM3u, myList = true, positionMs = 90_000, durationMs = 600_000, updatedMs = 7),
            )
            db.vod().putState(VodStateEntity(series, myList = true))

            apiStatus = 200
            assertTrue(repo.refresh(added.id).isSuccess)
            val moved = db.vod().stateOf("$p:m7")
            assertNotNull(moved)
            assertEquals(true, moved?.myList)
            assertEquals(90_000L, moved?.positionMs)
            assertNull(db.vod().stateOf(movieM3u))
            assertEquals(
                "serial nie ma bezpiecznego odpowiednika: dane nietknięte",
                true,
                db.vod().stateOf(series)?.myList,
            )
        }

    @Test
    fun `nazwa sama nie wystarcza do przeniesienia`() =
        runTest {
            val repo = repo()
            val added = repo.addM3u("Panel", link) as AddProviderResult.Added
            val bezNumeru = idOf(repo, "Bez numeru")
            db.channelFlags().upsert(ChannelFlagEntity(bezNumeru, favorite = true))
            apiStatus = 200
            repo.refresh(added.id)
            assertEquals(true, flag(bezNumeru)?.favorite)
            assertFalse(repo.liveChannels().first().any { it.id == bezNumeru })
            assertEquals(ProviderId(added.id.value), added.id)
        }

    private companion object {
        const val AUTH = """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000"},"server_info":{}}"""
        const val CATS = """[{"category_id":"1","category_name":"PL | News"}]"""
        const val API_LIVE =
            """[{"num":1,"name":"TVN 24 HD","stream_id":101,"category_id":"1"},""" +
                """{"num":2,"name":"INFO24","stream_id":102,"category_id":"1"},""" +
                """{"num":3,"name":"Nowy","stream_id":103,"category_id":"1"}]"""
        const val API_VOD = """[{"stream_id":"7","name":"Film (2019)","container_extension":"mp4","year":"2019"}]"""
    }
}
