package pl.ketchupplayer.tv.provider.m3u

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.provider.api.ProviderError

class M3uProviderTest {
    private val list =
        "#EXTM3U url-tvg=\"https://epg.example.invalid/x.xml\"\n#EXTINF:-1,A\nhttp://h.example.invalid/a\n"

    @Test
    fun `laduje kanaly i zapamietuje adres EPG`() =
        runTest {
            val p = M3uProvider(ProviderId("p"), "http://l.example.invalid/x.m3u") { Result.success(list) }
            assertEquals(listOf("A"), p.loadChannels().getOrThrow().map { it.name })
            assertEquals("https://epg.example.invalid/x.xml", p.epgUrl())
        }

    @Test
    fun `401 to Unauthorized, 500 to Network`() =
        runTest {
            val p401 = M3uProvider(ProviderId("p"), "u") { Result.failure(NetworkException.Http(401)) }
            assertTrue(p401.loadChannels().exceptionOrNull() is ProviderError.Unauthorized)
            val p500 = M3uProvider(ProviderId("p"), "u") { Result.failure(NetworkException.Http(500)) }
            assertTrue(p500.loadChannels().exceptionOrNull() is ProviderError.Network)
        }

    @Test
    fun `pusta lista to BadResponse`() =
        runTest {
            val p = M3uProvider(ProviderId("p"), "u") { Result.success("#EXTM3U\n") }
            assertTrue(p.loadChannels().exceptionOrNull() is ProviderError.BadResponse)
        }

    @Test
    fun `resolveStream zwraca adres i user agent kanalu`() =
        runTest {
            val p =
                M3uProvider(ProviderId("p"), "u") {
                    Result.success(
                        "#EXTM3U\n#EXTINF:-1,A\n#EXTVLCOPT:http-user-agent=UA1\nhttp://h.example.invalid/a\n",
                    )
                }
            val ch = p.loadChannels().getOrThrow().single()
            val spec = p.resolveStream(ch).getOrThrow()
            assertEquals("http://h.example.invalid/a", spec.url)
            assertEquals("UA1", spec.userAgent)
        }

    private val withVod =
        "#EXTM3U\n" +
            "#EXTINF:-1 group-title=\"PL| News\",TVN24\nhttp://h.example.invalid/live/u/p/1.ts\n" +
            "#EXTINF:-1 tvg-logo=\"http://i.example.invalid/p.jpg\" group-title=\"Dramat\",PL - Pitbull (2005)\n" +
            "http://h.example.invalid/movie/u/p/10.mkv\n" +
            "#EXTINF:-1 group-title=\"Seriale\",Rodzina Kowalskich S01E01 Pilot\n" +
            "http://h.example.invalid/series/u/p/21.mp4\n" +
            "#EXTINF:-1 group-title=\"Seriale\",Rodzina Kowalskich S01E02\n" +
            "http://h.example.invalid/series/u/p/22.mp4\n" +
            "#EXTINF:-1 group-title=\"Seriale\",Rodzina Kowalskich S02E01 Powrót\n" +
            "http://h.example.invalid/series/u/p/23.mp4\n" +
            "#EXTINF:-1 group-title=\"Seriale\",Inny Serial S01E01\nhttp://h.example.invalid/series/u/p/31.mp4\n"

    @Test
    fun `filmy i odcinki z listy trafiaja do katalogu VOD, seriale grupowane po nazwie`() =
        runTest {
            val p = M3uProvider(ProviderId("p"), "u") { Result.success(withVod) }
            assertEquals(listOf("TVN24"), p.loadChannels().getOrThrow().map { it.name })
            val vod = p.loadVod().getOrThrow()
            val movie = vod.items.single { it.kind == pl.ketchupplayer.tv.core.model.VodKind.MOVIE }
            assertEquals("Pitbull", movie.title)
            assertEquals(2005, movie.year)
            assertEquals("Dramat", movie.category)
            val series = vod.items.filter { it.kind == pl.ketchupplayer.tv.core.model.VodKind.SERIES }
            assertEquals(setOf("Rodzina Kowalskich", "Inny Serial"), series.map { it.title }.toSet())
            val kowalscy = series.single { it.title == "Rodzina Kowalskich" }
            val eps =
                vod.episodes
                    .filter { it.seriesId == kowalscy.id }
                    .sortedWith(compareBy({ it.season }, { it.number }))
            assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), eps.map { it.season to it.number })
            assertEquals(listOf("Pilot", "Odcinek 2", "Powrót"), eps.map { it.title })
            assertEquals("http://h.example.invalid/movie/u/p/10.mkv", p.resolveVod(movie.ref).getOrThrow().url)
        }
}
