package pl.ketchupplayer.tv.core.data.metadata

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.core.network.TextFetcher

/** Sprawdzanie kluczy: każdy serwis dostaje jedno znane zapytanie, a wynik mówi, czy klucz działa. */
class KeyProbeTest {
    private val seen = mutableListOf<Pair<String, Map<String, String>>>()

    private fun probe(routes: Map<String, Result<String>>): KeyProbe {
        val fetcher =
            object : TextFetcher {
                override suspend fun fetch(url: String) = fetchWithHeaders(url, emptyMap())

                override suspend fun fetchWithHeaders(
                    url: String,
                    headers: Map<String, String>,
                ): Result<String> {
                    seen += url to headers
                    return routes.entries.firstOrNull { it.key in url }?.value
                        ?: Result.failure(NetworkException.Http(404))
                }
            }
        return KeyProbe(fetcher, MetadataEndpoints())
    }

    @Test
    fun `kazdy serwis dostaje wlasny wynik, brak klucza jest pomijany`() =
        runBlocking {
            val p =
                probe(
                    mapOf(
                        "themoviedb.org" to Result.success("{}"),
                        "omdbapi.com" to Result.success("""{"Response":"False","Error":"Invalid API key!"}"""),
                        "mdblist.com" to Result.failure(NetworkException.Http(403)),
                        "trakt.tv" to Result.failure(NetworkException.Http(429)),
                    ),
                )
            val r = p.check(ApiKeys(tmdb = "k1", omdb = "k2", mdblist = "k3", trakt = "k4")).associateBy { it.source }
            assertEquals(KeyStatus.OK, r.getValue("TMDB").status)
            assertEquals(KeyStatus.BAD_KEY, r.getValue("OMDb").status)
            assertEquals(KeyStatus.BAD_KEY, r.getValue("MDBList").status)
            assertEquals(KeyStatus.LIMIT, r.getValue("Trakt").status)
            assertEquals(
                KeyStatus.NO_KEY,
                probe(emptyMap()).check(ApiKeys()).first().status,
            )
        }

    @Test
    fun `token TMDB idzie w naglowku Bearer, krotki klucz w adresie`() =
        runBlocking {
            val token = "eyJ" + "a".repeat(80)
            probe(mapOf("themoviedb.org" to Result.success("{}"))).check(ApiKeys(tmdb = token))
            val (url, headers) = seen.single()
            assertTrue("token nie wycieka do adresu", "api_key" !in url && token !in url)
            assertEquals("Bearer $token", headers["Authorization"])
            seen.clear()
            probe(mapOf("themoviedb.org" to Result.success("{}"))).check(ApiKeys(tmdb = "abcdef0123456789"))
            assertTrue("api_key=abcdef0123456789" in seen.single().first)
            assertTrue(seen.single().second.isEmpty())
        }
}
