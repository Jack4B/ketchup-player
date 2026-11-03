package pl.ketchupplayer.tv.provider.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.ProviderId

/**
 * Lista o rozmiarze prawdziwego panelu (ok. 100 tys. pozycji: 4,5 tys. kanałów, 19 tys. filmów, 84 tys. odcinków).
 * Na boxie import trwał minuty; test pilnuje, żeby parser nie wrócił do kosztownych regexów
 * (budżet z dużym zapasem dla wolnego CI).
 */
class M3uPerformanceTest {
    private fun extinf(
        name: String,
        logo: String,
        group: String,
    ) = "#EXTINF:-1 tvg-id=\"\" tvg-name=\"$name\" tvg-logo=\"$logo\" group-title=\"$group\",$name"

    private fun synthetic(): List<String> {
        val out = ArrayList<String>(SIZE)
        out += "#EXTM3U"
        for (i in 1..LIVE) {
            out += extinf("PL| Kanał $i HD", "http://logo.example.invalid/l$i.png", "PL| INFO")
            out += "http://srv.example.invalid:80/user/pass/$i"
        }
        for (i in 1..MOVIES) {
            out += extinf("PL - Film numer $i (2019)", "http://img.example.invalid/m$i.jpg", "|PL| FILMY")
            out += "http://srv.example.invalid:80/movie/user/pass/$i.mkv"
        }
        for (i in 1..EPISODES) {
            val tag = "Serial ${i / EPISODES_PER_SHOW} S${(i % 3) + 1}E${(i % EPISODES_PER_SHOW) + 1}"
            out += extinf(tag, "http://img.example.invalid/s$i.jpg", "|PL| SERIALE")
            out += "http://srv.example.invalid:80/series/user/pass/$i.mkv"
        }
        return out
    }

    @Test
    fun parsowanieStuTysiecyPozycjiMiesciSieWBudzecie() {
        val lines = synthetic()
        val vod = M3uVodCollector(ProviderId("p"))
        val t0 = System.nanoTime()
        val playlist = M3uParser.parse(ProviderId("p"), lines.asSequence(), liveOnly = true, vod = vod)
        val ms = (System.nanoTime() - t0) / NANOS_PER_MS
        val cat = vod.build()
        assertEquals(LIVE, playlist.channels.size)
        assertEquals(MOVIES, cat.items.count { it.kind == pl.ketchupplayer.tv.core.model.VodKind.MOVIE })
        assertEquals(80_000, cat.episodes.size) // limit odcinków chroni słabe boxy przed brakiem pamięci
        assertTrue("parsowanie 100 tys. pozycji trwało $ms ms (budżet $BUDGET_MS ms)", ms < BUDGET_MS)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val BUDGET_MS = 15_000L
        const val LIVE = 4578
        const val MOVIES = 19351
        const val EPISODES = 83935
        const val EPISODES_PER_SHOW = 12
        const val SIZE = 210_000
    }
}
