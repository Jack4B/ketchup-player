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
}
