package pl.ketchupplayer.tv.core.data.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.data.groups.HiddenGroups
import pl.ketchupplayer.tv.core.database.KetchupDatabase
import pl.ketchupplayer.tv.core.database.VodStateEntity
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.RequestMethod
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.core.security.InMemorySecretStore

/**
 * Błędy z testów rc4 odtworzone na dwóch syntetycznych listach (adresy `*.example.invalid`, dane dostępu zmyślone):
 * edycja zapisanej listy i wiązanie grup oraz kolejności z listą, z której pochodzą.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistEditAndScopeTest {
    private lateinit var db: KetchupDatabase
    private val secrets = InMemorySecretStore()
    private val requested = mutableListOf<String>()
    private var bodies = mapOf<String, String>()
    private val fetcher =
        TextFetcher { url ->
            requested += url
            bodies.entries.firstOrNull { url.startsWith(it.key) }?.let { Result.success(it.value) }
                ?: Result.failure(java.io.IOException("brak"))
        }

    private fun list(
        host: String,
        vararg channels: Triple<Int, String, String>,
    ) = "#EXTM3U\n" +
        channels.joinToString("") { (n, group, name) ->
            "#EXTINF:-1 tvg-chno=\"$n\" group-title=\"$group\",$name\nhttp://$host/$n.ts\n"
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
        bodies =
            mapOf(
                "http://a.example.invalid/" to
                    list("sa.example.invalid", Triple(1, "Sport", "Kanał A1"), Triple(2, "Sport", "Kanał A2")),
                "http://b.example.invalid/" to
                    list("sb.example.invalid", Triple(1, "Sport", "Kanał B1"), Triple(2, "Info", "Kanał B2")),
            )
    }

    @After
    fun close() = db.close()

    private fun repo() =
        RoomCatalogRepository(
            db.providers(),
            db.channels(),
            db.channelFlags(),
            db.vod(),
            ProviderSecrets(secrets),
            ProviderFactory(
                ProviderSecrets(secrets),
                fetcher,
                pl.ketchupplayer.tv.core.data.network
                    .RoomProviderHeadersRepository(db.providers(), ProviderSecrets(secrets)),
            ),
            Clock { 1_000L },
        )

    private suspend fun twoLists(r: RoomCatalogRepository): Pair<ProviderId, ProviderId> {
        val a =
            (
                r.addM3u(
                    "Lista A",
                    "http://a.example.invalid/get.php?username=jan&password=tajne1",
                ) as AddProviderResult.Added
            ).id
        val b = (r.addM3u("Lista B", "http://b.example.invalid/lista.m3u") as AddProviderResult.Added).id
        return a to b
    }

    // ---- edycja zapisanej listy ----

    @Test
    fun edycjaZmieniaTeSamaListeBezDuplikatuIUtratyDanych() =
        runTest {
            val r = repo()
            val (a, _) = twoLists(r)
            val ch = r.liveChannels().first().first { it.providerId == a }
            r.setFavorite(ch.id, true)
            db.vod().upsertState(VodStateEntity("cos", myList = true))

            // Atrapa sieci nie obsługuje POST: pobranie się nie uda, ale zmiany i dotychczasowe kanały mają zostać.
            val result =
                r.update(
                    a,
                    ProviderEdit(name = "Lista A (dom)", requestMethod = RequestMethod.POST, userAgentId = "vlc"),
                )

            assertTrue("$result", result is AddProviderResult.Added || result is AddProviderResult.SavedWithError)
            assertEquals("ten sam identyfikator", a, (result as? AddProviderResult.SavedWithError)?.id ?: a)
            val providers = r.providers.first()
            assertEquals("bez duplikatu", 2, providers.size)
            val edited = providers.single { it.id == a }
            assertEquals("Lista A (dom)", edited.name)
            assertEquals(RequestMethod.POST, edited.requestMethod)
            assertEquals("vlc", edited.userAgentId)
            assertEquals("ulubione zostają", setOf(ch.id), r.favoriteIds().first())
            assertEquals(true, db.vod().state("cos")?.myList)
            assertEquals("kanały listy zostają", 2, edited.channelCount)
        }

    @Test
    fun pustyAdresWEdycjiZostawiaZapisanyAdresZDanymiDostepu() =
        runTest {
            val r = repo()
            val (a, _) = twoLists(r)
            requested.clear()
            r.update(a, ProviderEdit(name = "Lista A"))
            assertTrue("pobranie poszło na zapisany adres", requested.any { it.contains("password=tajne1") })
            val shown = r.providers.first().single { it.id == a }
            assertFalse("UI dostaje tylko host", shown.urlHost.contains("tajne1"))
            assertFalse(
                db
                    .providers()
                    .get(a.value)!!
                    .url
                    .contains("tajne1"),
            )
        }

    @Test
    fun nowyAdresWEdycjiZastepujeStaryWTajnymMagazynie() =
        runTest {
            val r = repo()
            val (a, _) = twoLists(r)
            bodies =
                bodies + ("http://c.example.invalid/" to list("sc.example.invalid", Triple(7, "Filmy", "Kanał C7")))
            r.update(a, ProviderEdit(name = "Lista A", url = "http://c.example.invalid/nowa.m3u"))
            assertEquals("http://c.example.invalid/nowa.m3u", ProviderSecrets(secrets).url(a))
            assertEquals(
                listOf("Kanał C7"),
                r
                    .liveChannels()
                    .first()
                    .filter { it.providerId == a }
                    .map { it.name },
            )
        }

    @Test
    fun edycjaXtreamZmieniaSamoHasloBezPokazywaniaLoginu() =
        runTest {
            val r = repo()
            r.addXtream("Konto", "http://x.example.invalid:8080", "jan", "stare-haslo")
            val id =
                r.providers
                    .first()
                    .single()
                    .id
            r.update(id, ProviderEdit(name = "Konto", password = "nowe-haslo"))
            assertEquals("jan" to "nowe-haslo", ProviderSecrets(secrets).login(id))
            assertEquals(1, r.providers.first().size)
        }

    @Test
    fun bledneDaneWEdycjiNiczegoNieZmieniaja() =
        runTest {
            val r = repo()
            val (a, _) = twoLists(r)
            val before = db.providers().get(a.value)
            assertEquals(
                AddProviderResult.Invalid(AddProviderError.INVALID_URL),
                r.update(a, ProviderEdit(name = "X", url = "ftp://zle")),
            )
            assertEquals(
                AddProviderResult.Invalid(AddProviderError.INVALID_NAME),
                r.update(a, ProviderEdit(name = " ")),
            )
            assertEquals(
                AddProviderResult.Invalid(AddProviderError.NOT_FOUND),
                r.update(ProviderId("brak"), ProviderEdit("X")),
            )
            assertEquals(before, db.providers().get(a.value))
        }

    // ---- grupy i kolejność związane z listą ----

    @Test
    fun wszystkieListyKazdaListaWSwojejKolejnosciAGrupyZTaSamaNazwaOsobno() =
        runTest {
            val r = repo()
            val (a, b) = twoLists(r)
            val choice = PlaylistChoice(listOf(PlaylistOption(a, "Lista A"), PlaylistOption(b, "Lista B")))
            val shown = PlaylistScope.channels(r.liveChannels().first(), choice, HiddenGroups())
            assertEquals(
                "bez przeplatania numerów 1, 1, 2, 2",
                listOf("Kanał A1", "Kanał A2", "Kanał B1", "Kanał B2"),
                shown.map { it.name },
            )
            assertEquals(
                listOf("Sport · Lista A", "Sport · Lista A", "Sport · Lista B", "Info"),
                shown.map { it.group },
            )
        }

    @Test
    fun wybranaListaPokazujeTylkoSwojeKanalyIGrupyBezDopisku() =
        runTest {
            val r = repo()
            val (a, b) = twoLists(r)
            val choice =
                PlaylistChoice(listOf(PlaylistOption(a, "Lista A"), PlaylistOption(b, "Lista B")), selected = b)
            val shown = PlaylistScope.channels(r.liveChannels().first(), choice, HiddenGroups())
            assertEquals(listOf("Kanał B1", "Kanał B2"), shown.map { it.name })
            assertEquals(listOf("Sport", "Info"), shown.map { it.group })
        }

    @Test
    fun ukrycieGrupyWJednejLiscieNieChowaGrupyODrugiej() =
        runTest {
            val r = repo()
            val (a, b) = twoLists(r)
            val choice = PlaylistChoice(listOf(PlaylistOption(a, "Lista A"), PlaylistOption(b, "Lista B")))
            val hidden = HiddenGroups(setOf(HiddenGroups.key(a.value, "Sport")))
            val shown = PlaylistScope.channels(r.liveChannels().first(), choice, hidden)
            assertEquals(listOf("Kanał B1", "Kanał B2"), shown.map { it.name })
            assertEquals(
                "po ukryciu nazwa nie powtarza się już w widocznych",
                listOf("Sport", "Info"),
                shown.map { it.group },
            )
        }

    @Test
    fun usuniecieJednejListyNieRuszaDanychDrugiej() =
        runTest {
            val r = repo()
            val (a, b) = twoLists(r)
            val bChannel = r.liveChannels().first().first { it.providerId == b }
            r.setFavorite(bChannel.id, true)
            r.remove(a)
            assertEquals(listOf("Kanał B1", "Kanał B2"), r.liveChannels().first().map { it.name })
            assertEquals(setOf(bChannel.id), r.favoriteIds().first())
            assertNotNull(r.channel(bChannel.id))
        }
}
