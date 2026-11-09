package pl.ketchupplayer.tv.provider.xtream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.RequestMethod
import pl.ketchupplayer.tv.core.network.FormBody
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.core.network.TextFetcher

/** Tryb POST dla Xtream: wszystkie wywołania API w ciele, bez danych w adresie. Dane wyłącznie fikcyjne. */
class XtreamPostTest {
    private val creds = XtreamCredentials("http://h.example.invalid:8080", "user123", "pa ss&456")
    private val auth = """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000"}}"""
    private val cats = """[{"category_id":"1","category_name":"PL | News"}]"""
    private val streams = """[{"num":"1","name":"JEDEN HD","stream_id":"101","category_id":"1"}]"""
    private val vod = """[{"stream_id":"7","name":"Film (2019)","container_extension":"mp4","year":"2019"}]"""
    private val series = """[{"series_id":"9","name":"Serial","year":"2020"}]"""

    private class PostOnly(
        private val answers: Map<String, String>,
    ) : TextFetcher {
        val posts = mutableListOf<Pair<String, String>>()
        val gets = mutableListOf<String>()

        override suspend fun fetch(url: String): Result<String> {
            gets += url
            return Result.failure(NetworkException.Http(504))
        }

        override suspend fun fetchWithBody(
            url: String,
            body: FormBody,
        ): Result<String> {
            posts += url to body.encoded
            val action = Regex("action=([a-z_]+)").find(body.encoded)?.groupValues?.get(1) ?: ""
            return answers[action]?.let { Result.success(it) } ?: Result.failure(NetworkException.Http(404))
        }
    }

    private fun fetcher() =
        PostOnly(
            mapOf(
                "" to auth,
                "get_live_categories" to cats,
                "get_live_streams" to streams,
                "get_vod_categories" to cats,
                "get_series_categories" to cats,
                "get_vod_streams" to vod,
                "get_series" to series,
            ),
        )

    @Test
    fun `kanaly - uwierzytelnienie, kategorie i strumienie idzie POST bez danych w adresie`() =
        runTest {
            val f = fetcher()
            val channels = XtreamProvider(ProviderId("x"), creds, f, RequestMethod.POST).loadChannels().getOrThrow()
            assertEquals(listOf("JEDEN HD"), channels.map { it.name })
            assertTrue(f.gets.isEmpty())
            assertEquals(3, f.posts.size)
            f.posts.forEach { (url, body) ->
                assertEquals("http://h.example.invalid:8080/player_api.php", url)
                assertTrue(body, body.startsWith("username=user123&password=pa%20ss%26456"))
            }
            assertEquals(
                listOf("", "get_live_categories", "get_live_streams"),
                f.posts.map { Regex("action=([a-z_]+)").find(it.second)?.groupValues?.get(1) ?: "" },
            )
        }

    @Test
    fun `katalog VOD i seriale tez idzie POST`() =
        runTest {
            val f = fetcher()
            val catalog = XtreamProvider(ProviderId("x"), creds, f, RequestMethod.POST).loadVod().getOrThrow()
            assertEquals(2, catalog.items.size)
            assertTrue(f.gets.isEmpty())
            assertTrue(f.posts.any { it.second.contains("action=get_vod_streams") })
            assertTrue(
                f.posts.any { it.second.contains("action=get_series&") || it.second.endsWith("action=get_series") },
            )
        }

    @Test
    fun `EPG i adresy strumieni zostaja bez zmian niezaleznie od metody`() =
        runTest {
            val get = XtreamProvider(ProviderId("x"), creds, fetcher(), RequestMethod.GET)
            val post = XtreamProvider(ProviderId("x"), creds, fetcher(), RequestMethod.POST)
            assertEquals(get.epgUrl(), post.epgUrl())
            assertEquals(creds.liveUrl("101", "ts"), "http://h.example.invalid:8080/live/user123/pa%20ss%26456/101.ts")
        }

    @Test
    fun `tryb GET zachowuje dotychczasowe adresy API`() {
        val r = creds.apiRequest(RequestMethod.GET, "get_live_streams")
        assertEquals(creds.apiUrl("get_live_streams"), r.url)
        assertEquals(null, r.body)
    }

    @Test
    fun `blad POST nie ujawnia ciala w komunikacie`() =
        runTest {
            val failing =
                object : TextFetcher {
                    override suspend fun fetch(url: String) = Result.failure<String>(NetworkException.Http(504))

                    override suspend fun fetchWithBody(
                        url: String,
                        body: FormBody,
                    ) = Result.failure<String>(NetworkException.Http(504))
                }
            val error =
                XtreamProvider(
                    ProviderId("x"),
                    creds,
                    failing,
                    RequestMethod.POST,
                ).loadChannels().exceptionOrNull()
            val text = error?.message.orEmpty() + error.toString()
            assertFalse(text, text.contains("user123") || text.contains("pa%20ss"))
        }
}
