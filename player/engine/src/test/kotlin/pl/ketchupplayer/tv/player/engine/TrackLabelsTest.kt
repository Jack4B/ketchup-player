package pl.ketchupplayer.tv.player.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackLabelsTest {
    @Test
    fun `audio ma jezyk kodek i uklad kanalow`() {
        assertEquals("Polski · AC3 5.1", TrackLabels.audio("pl", null, 6, "audio/ac3", 0))
        assertEquals("Angielski · AAC stereo", TrackLabels.audio("en", null, 2, "audio/mp4a-latm", 1))
    }

    @Test
    fun `etykieta z listy ma pierwszenstwo przed jezykiem`() {
        assertEquals("Lektor · E-AC3 5.1", TrackLabels.audio("pl", "Lektor", 6, "audio/eac3", 0))
    }

    @Test
    fun `brak jezyka daje numer sciezki`() {
        assertEquals("Ścieżka 3", TrackLabels.audio(null, null, 0, null, 2))
        assertEquals("Ścieżka 1", TrackLabels.audio("und", "", 0, "video/unknown", 0))
    }

    @Test
    fun `napisy maja jezyk albo numer`() {
        assertEquals("Polski", TrackLabels.text("pl", null, 0))
        assertEquals("Napisy 2", TrackLabels.text(null, null, 1))
        assertEquals("Polski (SDH)", TrackLabels.text("pl", "Polski (SDH)", 0))
    }
}
