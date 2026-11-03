package pl.ketchupplayer.tv.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind

class TimeBudgetTest {
    private val min = 60_000L
    private val now = 1_800_000_000_000L

    private fun vod(
        id: String,
        positionMin: Long,
        durationMin: Long,
    ) = VodEntry(
        VodItem(id = id, providerId = ProviderId("p"), kind = VodKind.MOVIE, title = id, ref = "http://h.invalid/$id"),
        positionMs = positionMin * min,
        durationMs = durationMin * min,
    )

    private fun live(
        id: String,
        endsInMin: Long?,
    ) = ChannelNow(
        Channel(id, ProviderId("p"), id, "http://h.invalid/$id", 1),
        endsInMin?.let { EpgProgramme(id, now - 30 * min, now + it * min, "Audycja $id") },
    )

    @Test
    fun `film do dokonczenia miesci sie w czasie albo nie`() {
        val r =
            TimeBudget.pick(
                30,
                listOf(vod("krotki", 70, 90), vod("dlugi", 10, 120), vod("skonczony", 90, 90)),
                emptyList(),
                now,
            )
        assertEquals(listOf("krotki"), r.finish.map { it.entry.item.id })
        assertEquals(20, r.finish.single().remainingMin)
    }

    @Test
    fun `najlepiej pasujace sa pierwsze`() {
        val r = TimeBudget.pick(60, listOf(vod("a", 50, 60), vod("b", 20, 60), vod("c", 35, 60)), emptyList(), now)
        assertEquals(listOf("b", "c", "a"), r.finish.map { it.entry.item.id })
    }

    @Test
    fun `audycja na zywo musi sie skonczyc w czasie i miec sensowny zapas`() {
        val r =
            TimeBudget.pick(
                60,
                emptyList(),
                listOf(live("ok", 45), live("za-dluga", 80), live("koncowka", 5), live("bez-epg", null)),
                now,
            )
        assertEquals(listOf("ok"), r.live.map { it.item.channel.id })
        assertEquals(45, r.live.single().remainingMin)
    }

    @Test
    fun `brak danych daje puste propozycje`() {
        assertTrue(TimeBudget.pick(90, emptyList(), emptyList(), now).isEmpty)
        // film bez znanej dlugosci (nigdy nie odtwarzany) nie jest propozycja
        assertTrue(TimeBudget.pick(90, listOf(vod("nieznany", 0, 0)), emptyList(), now).isEmpty)
    }

    @Test
    fun `limit obcina liste`() {
        val many = (1..20).map { vod("f$it", 0, it.toLong() + 1) }
        assertEquals(8, TimeBudget.pick(120, many, emptyList(), now).finish.size)
    }

    private fun film(
        id: String,
        durationMin: Int?,
        rating: Double,
    ) = VodEntry(
        VodItem(
            id = id,
            providerId = ProviderId("p"),
            kind = VodKind.MOVIE,
            title = id,
            ref = "r",
            rating = rating,
            durationMin = durationMin,
        ),
    )

    /** Film z katalogu mieści się w czasie i wypełnia co najmniej 60%; lepsze oceny pierwsze. */
    @Test
    fun `film z katalogu wypelnia podany czas`() {
        val catalog =
            listOf(
                film("za-krotki", 40, 9.5),
                film("pasuje-slabszy", 85, 6.0),
                film("pasuje-lepszy", 100, 8.0),
                film("za-dlugi", 130, 9.9),
                film("bez-dlugosci", null, 9.9),
                film("nieznany", 0, 9.9),
            )
        val r = TimeBudget.pick(120, emptyList(), emptyList(), now, catalog)
        assertEquals(listOf("pasuje-lepszy", "pasuje-slabszy"), r.catalog.map { it.entry.item.id })
        assertEquals(100, r.catalog.first().durationMin)
        assertFalse(r.isEmpty)
    }

    /** Film już zaczęty jest w „do dokończenia”, więc nie powtarza się w propozycjach z katalogu. */
    @Test
    fun `zaczety film nie dubluje sie w katalogu`() {
        val started = vod("a", 10, 100)
        val r =
            TimeBudget.pick(
                120,
                listOf(started),
                emptyList(),
                now,
                listOf(film("a", 100, 9.0), film("b", 100, 8.0)),
            )
        assertEquals(listOf("b"), r.catalog.map { it.entry.item.id })
    }
}
