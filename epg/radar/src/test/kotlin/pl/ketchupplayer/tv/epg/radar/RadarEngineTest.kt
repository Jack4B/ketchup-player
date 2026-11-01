package pl.ketchupplayer.tv.epg.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme
import pl.ketchupplayer.tv.core.model.ProviderId

class RadarEngineTest {
    private val m = 60_000L
    private val now = 1_800_000_000_000L
    private val pid = ProviderId("p")
    private val w =
        RadarPolicy.Window(now - 6 * 60 * m, now + 18 * 60 * m, now + 3 * 60 * m, now + 7 * 60 * m)
    private val channels =
        listOf(
            Channel("p:1", pid, "TVN", "u1"),
            Channel("p:2", pid, "Polsat", "u2"),
            Channel("p:3", pid, "TV Puls", "u3"),
        )

    private fun prog(
        ch: String,
        title: String,
        startMin: Long,
        endMin: Long,
        desc: String? = null,
        cat: String? = null,
    ) = EpgProgramme(ch, now + startMin * m, now + endMin * m, title, desc, cat)

    @Test
    fun `Teraz zawiera tylko trwajace audycje, Za chwile tylko w ciagu 90 minut po czasie startu`() {
        val ps =
            listOf(
                prog("p:1", "Teraz", -10, 50),
                prog("p:2", "Za 30 min", 30, 90),
                prog("p:3", "Za 3 godziny", 180, 240),
            )
        assertEquals(listOf("Teraz"), RadarEngine.build(RadarSlot.NOW, now, w, channels, ps).map { it.programme.title })
        assertEquals(
            listOf("Za 30 min"),
            RadarEngine.build(RadarSlot.SOON, now, w, channels, ps).map { it.programme.title },
        )
    }

    @Test
    fun `ocena z opisu podnosi wynik i trafia do pozycji`() {
        val ps =
            listOf(
                prog("p:1", "Dobry film", -10, 100, "Opis. O: 8,5/10", "Film"),
                prog("p:2", "Przecietny film", -10, 100, "Opis. O: 4,0/10", "Film"),
            )
        val top = RadarEngine.build(RadarSlot.NOW, now, w, channels, ps)
        assertEquals(listOf("Dobry film", "Przecietny film"), top.map { it.programme.title })
        assertEquals(85, top.first().ratingTenths)
    }

    @Test
    fun `ta sama audycja na wielu kanalach pojawia sie raz`() {
        val ps = listOf(prog("p:1", "Wiadomosci", -10, 20), prog("p:2", "Wiadomości", -10, 20, "O: 9/10"))
        val items = RadarEngine.build(RadarSlot.NOW, now, w, channels, ps)
        assertEquals(1, items.size)
        assertEquals("p:2", items.single().channel.id)
    }

    @Test
    fun `telesklep i krotkie wstawki sa odsuniete, ulubiony kanal wyzej`() {
        val ps =
            listOf(
                prog("p:1", "Telesklep", -5, 55),
                prog("p:2", "Wstawka", -2, 8),
                prog("p:3", "Zwykly program", -5, 55),
            )
        val items = RadarEngine.build(RadarSlot.NOW, now, w, channels, ps, favoriteChannelIds = setOf("p:3"))
        assertEquals("Zwykly program", items.first().programme.title)
        assertTrue(items.last().score < items.first().score)
    }

    @Test
    fun `TOP DNIA nie ma trzech tych samych gatunkow z rzedu gdy jest rownie dobra alternatywa`() {
        val ps =
            (1..4).map { prog("p:${(it % 3) + 1}", "Film $it", -10, 100 + it.toLong(), "O: 8/10", "Film") } +
                prog("p:1", "Dokument o ptakach", -10, 120, "O: 8/10", "Dokument")
        val items = RadarEngine.build(RadarSlot.TOP_DAY, now, w, channels, ps)
        val genres = items.take(3).map { RadarEngine.genre(it.programme) }
        assertTrue("trzy takie same z rzędu: $genres", genres.toSet().size > 1)
    }

    @Test
    fun `wynik ograniczony do 30 pozycji`() {
        val many = (1..50).map { prog("p:${(it % 3) + 1}", "Program $it", -10, 100 + it.toLong()) }
        assertEquals(RadarPolicy.MAX_RESULTS, RadarEngine.build(RadarSlot.NOW, now, w, channels, many).size)
    }
}
