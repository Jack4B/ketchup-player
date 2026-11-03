package pl.ketchupplayer.tv.epg.radar

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.core.model.ProviderId

class InterestMatcherTest {
    private val pid = ProviderId("p")
    private val channels =
        listOf(
            Channel("p:1", pid, "TVP 1", "http://h.example.invalid/1", 1),
            Channel("p:2", pid, "Sport TV", "http://h.example.invalid/2", 2),
        )
    private val now = 1_000_000L

    private fun prog(
        ch: String,
        start: Long,
        title: String,
        desc: String? = null,
    ) = EpgProgramme(ch, start, start + 3_600_000, title, desc)

    @Test
    fun `dopasowuje po tytule bez wielkosci liter i polskich znakow`() {
        val hits =
            InterestMatcher.match(
                listOf("kleks"),
                channels,
                listOf(prog("p:1", now + 10, "Akademia pana Kleksa")),
                now,
            )
        assertEquals(1, hits.size)
        assertEquals("kleks", hits.single().keyword)
    }

    @Test
    fun `dopasowuje po opisie i pomija zakonczone audycje`() {
        val programmes =
            listOf(
                prog("p:2", now - 4_000_000, "Mecz", "Premier League na żywo"), // już skończona
                prog("p:2", now + 5_000, "Mecz", "Premier League na żywo"),
            )
        val hits = InterestMatcher.match(listOf("Premier League"), channels, programmes, now)
        assertEquals(listOf(now + 5_000), hits.map { it.programme.startMs })
    }

    @Test
    fun `sortuje po czasie i szanuje limit`() {
        val programmes = (1..5).map { prog("p:1", now + it * 1_000L, "Kleks $it") }.reversed()
        val hits = InterestMatcher.match(listOf("kleks"), channels, programmes, now, limit = 3)
        assertEquals(listOf(now + 1_000, now + 2_000, now + 3_000), hits.map { it.programme.startMs })
    }

    @Test
    fun `puste slowa i nieznane kanaly nic nie daja`() {
        assertEquals(
            emptyList<InterestHit>(),
            InterestMatcher.match(listOf(" "), channels, listOf(prog("p:1", now + 1, "x")), now),
        )
        assertEquals(
            emptyList<InterestHit>(),
            InterestMatcher.match(listOf("x"), channels, listOf(prog("p:9", now + 1, "x")), now),
        )
    }
}
