package pl.ketchupplayer.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyLogTest {
    /** Format jest kontraktem ze skryptem analizy: zmiana tu wymaga zmiany `tools/tvbox/analyze_keys.sh`. */
    @Test
    fun `linia ma stale pola w stalej kolejnosci`() {
        assertEquals(
            "KG t=1000 key=20 rep=0 age=3 gap=42 dev=5 src=501 drop=1",
            KeyLog.line(KeyLog.Record(1000, 20, 0, 3, 42, 5, 0x501, true)),
        )
        assertEquals(
            "KG t=1 key=19 rep=2 age=0 gap=-1 dev=0 src=0 drop=0",
            KeyLog.line(KeyLog.Record(1, 19, 2, 0, null, 0, 0, false)),
        )
    }

    /** Linia przechodzi przez Redactor bez zmian (inaczej analizator na boxie nie sparsowałby logu). */
    @Test
    fun `redactor nie zniekształca linii klawisza`() {
        val line = KeyLog.line(KeyLog.Record(123_456_789, 20, 0, 3, 42, 5, 0x501, false))
        assertEquals(
            line,
            pl.ketchupplayer.tv.core.common.Redactor
                .redact(line),
        )
    }
}
