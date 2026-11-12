package pl.ketchupplayer.tv.core.data.metadata

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.ketchupplayer.tv.core.network.TextFetcher

/** Opis z Wikipedii bez klucza: bierzemy tylko stronę, która jest filmem lub serialem o tym samym tytule i roku. */
class WikipediaSourceTest {
    private fun source(
        search: String,
        pages: Map<String, String>,
    ) = WikipediaSource(
        TextFetcher { url ->
            val body =
                if ("list=search" in url) search else pages.entries.firstOrNull { url.endsWith(it.key) }?.value
            body?.let { Result.success(it) } ?: Result.failure(IllegalStateException("404"))
        },
        MetadataEndpoints(),
    )

    private fun search(vararg titles: String) =
        """{"query":{"search":[${titles.joinToString(",") { """{"title":"$it"}""" }}]}}"""

    @Test
    fun bierzeOpisStronyFilmuZgodnegoTytuluIRoku() {
        val s =
            source(
                search("Pitbull (film)"),
                mapOf(
                    "Pitbull_(film)" to
                        """{"description":"polski film z 2005 roku",""" +
                        """"extract":"Pitbull to film sensacyjny z 2005 roku."}""",
                ),
            )
        assertEquals("Pitbull to film sensacyjny z 2005 roku.", runBlocking { s.lookup("Pitbull", 2005, false) })
    }

    @Test
    fun odrzucaStroneKtoraNieJestFilmem() {
        val s =
            source(
                search("Pitbull"),
                mapOf("Pitbull" to """{"description":"rasa psa","extract":"Pitbull to rasa psa."}"""),
            )
        assertNull(runBlocking { s.lookup("Pitbull", 2005, false) })
    }

    @Test
    fun odrzucaFilmZInnegoRoku() {
        val s =
            source(
                search("Pitbull (film)"),
                mapOf(
                    "Pitbull_(film)" to
                        """{"description":"amerykański film z 1999 roku","extract":"Pitbull to film z 1999 roku."}""",
                ),
            )
        assertNull(runBlocking { s.lookup("Pitbull", 2005, false) })
    }

    @Test
    fun odrzucaStroneOInnymTytule() {
        val s =
            source(
                search("Pitbull Terrier (film)"),
                mapOf("Pitbull_Terrier_(film)" to """{"description":"film","extract":"Inny film."}"""),
            )
        assertNull(runBlocking { s.lookup("Pitbull", null, false) })
    }

    @Test
    fun serialWymagaSlowaSerial() {
        val s =
            source(
                search("Dom z papieru"),
                mapOf(
                    "Dom_z_papieru" to
                        """{"description":"hiszpański serial telewizyjny","extract":"Serial o napadzie."}""",
                ),
            )
        assertEquals("Serial o napadzie.", runBlocking { s.lookup("Dom z papieru", null, true) })
        assertNull(runBlocking { s.lookup("Dom z papieru", null, false) })
    }
}
