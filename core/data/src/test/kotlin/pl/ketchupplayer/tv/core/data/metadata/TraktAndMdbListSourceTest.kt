package pl.ketchupplayer.tv.core.data.metadata

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pl.ketchupplayer.tv.core.network.TextFetcher

/** Trakt (Client ID w nagłówku) i MDBList (klucz w adresie): kształty odpowiedzi czytane tolerancyjnie. */
class TraktAndMdbListSourceTest {
    private val seenHeaders = mutableListOf<Map<String, String>>()
    private val seenUrls = mutableListOf<String>()

    private fun fetcher(routes: Map<String, String>) =
        object : TextFetcher {
            override suspend fun fetch(url: String): Result<String> = fetchWithHeaders(url, emptyMap())

            override suspend fun fetchWithHeaders(
                url: String,
                headers: Map<String, String>,
            ): Result<String> {
                seenUrls += url
                seenHeaders += headers
                val hit = routes.entries.firstOrNull { it.key in url }
                return hit?.let { Result.success(it.value) } ?: Result.failure(IllegalStateException("404"))
            }
        }

    @Test
    fun traktBierzeOcenePolskiOpisIIdImdbZNaglowkiemKlucza() {
        val f =
            fetcher(
                mapOf(
                    "/search/movie" to
                        """[{"type":"movie","movie":{"ids":{"slug":"pitbull-2005","trakt":1,"imdb":"tt0397777"}}}]""",
                    "/translations/pl" to """[{"language":"pl","overview":"Opis po polsku."}]""",
                    "/movies/pitbull-2005" to
                        """{"rating":7.4,"overview":"English plot.","runtime":100,"ids":{"imdb":"tt0397777"}}""",
                ),
            )
        val meta = runBlocking { TraktSource(f, MetadataEndpoints()).lookup("moj-client-id", "Pitbull", 2005, false) }
        assertNotNull(meta)
        assertEquals(7.4, meta!!.rating!!, 0.001)
        assertEquals("Opis po polsku.", meta.overview)
        assertEquals("English plot.", meta.overviewEn)
        assertEquals("tt0397777", meta.imdbId)
        assertEquals("moj-client-id", seenHeaders.first()["trakt-api-key"])
        assertEquals("2", seenHeaders.first()["trakt-api-version"])
    }

    @Test
    fun traktBezTrafieniaDajeNull() {
        val f = fetcher(mapOf("/search/movie" to "[]"))
        assertNull(runBlocking { TraktSource(f, MetadataEndpoints()).lookup("id", "Nieistniejacy", null, false) })
    }

    @Test
    fun mdblistCzytaOcenyZKilkuSerwisow() {
        val f =
            fetcher(
                mapOf(
                    "/imdb/movie/tt0397777" to
                        """{"description":"Opis MDBList.","ratings":[{"source":"imdb","value":7.6},""" +
                        """{"source":"tomatoes","value":83},{"source":"metacritic","value":71},""" +
                        """{"source":"trakt","value":74},{"source":"tmdb","value":70}]}""",
                ),
            )
        val m =
            runBlocking {
                MdbListSource(
                    f,
                    MetadataEndpoints(),
                ).lookup("klucz", "tt0397777", "Pitbull", 2005, false)
            }!!
        assertEquals(7.6, m.imdb!!, 0.001)
        assertEquals(83, m.rtPercent)
        assertEquals(71, m.metacritic)
        assertEquals(7.4, m.trakt!!, 0.001) // skala procentowa 0-100 sprowadzona do 0-10
        assertEquals(7.0, m.tmdb!!, 0.001)
        assertEquals("Opis MDBList.", m.description)
        assertEquals(true, seenUrls.single().contains("apikey=klucz"))
    }

    @Test
    fun mdblistBezIdSzukaPoTytule() {
        val f =
            fetcher(
                mapOf(
                    "/search/movie" to """{"search":[{"title":"Pitbull","imdbid":"tt0397777"}]}""",
                    "/imdb/movie/tt0397777" to """{"ratings":[{"source":"imdb","value":7.6}]}""",
                ),
            )
        val m = runBlocking { MdbListSource(f, MetadataEndpoints()).lookup("k", null, "Pitbull", 2005, false) }
        assertEquals(7.6, m!!.imdb!!, 0.001)
    }

    @Test
    fun serwisMergujeOcenyZeWszystkichZrodel() {
        val f =
            fetcher(
                mapOf(
                    "/imdb/movie/tt1" to
                        """{"ratings":[{"source":"imdb","value":8.0},{"source":"metacritic","value":66}]}""",
                    "/search/movie" to """{"search":[{"imdbid":"tt1"}]}""",
                ),
            )
        val e = MetadataEndpoints()
        val service =
            MetadataService(
                MetadataSources(
                    tmdb = TmdbSource(f, e),
                    omdb = OmdbSource(f, e),
                    trakt = TraktSource(f, e),
                    mdblist = MdbListSource(f, e),
                ),
            )
        val item =
            pl.ketchupplayer.tv.core.model.VodItem(
                id = "x",
                providerId =
                    pl.ketchupplayer.tv.core.model
                        .ProviderId("p"),
                kind = pl.ketchupplayer.tv.core.model.VodKind.MOVIE,
                title = "Test",
                ref = "1",
            )
        val r = runBlocking { service.fetch(item, ApiKeys(mdblist = "k")) }!!
        assertEquals(8.0, r.ratings.imdb!!, 0.001)
        assertEquals(66, r.ratings.metacritic)
    }
}
