package pl.ketchupplayer.tv.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.common.Redactor
import pl.ketchupplayer.tv.core.network.sanitizeDetail

/**
 * Test kontraktu `Redactor` (issue #12) w module uruchamianym w CI: `core:common:test` i `core:network:test` nie
 * wchodzą do polecenia testów w `ci.yml`, więc pełny `RedactorTest` działa tylko lokalnie. Tu zostają przypadki, bez
 * których regresja reguł przeszłaby niezauważona. Dane wyłącznie fikcyjne (`*.example.invalid`, user123, pass456).
 */
class RedactorContractTest {
    private val h = "http://srv.example.invalid:8080"

    @Test
    fun `adresy z danymi dostepu sa maskowane w kontekscie URL`() {
        val cases =
            mapOf(
                "http://user123:pass456@srv.example.invalid/list.m3u" to "http://***@srv.example.invalid/list.m3u",
                "$h/api?Login=user123&PWD=pass456&type=m3u" to "$h/api?Login=***&PWD=***&type=m3u",
                "$h/live/user123/pass456/77.ts" to "$h/live/***/***/77.ts",
                "$h/timeshift/user123/pass456/60/2026-01-01:20-00/77.ts" to
                    "$h/timeshift/***/***/60/2026-01-01:20-00/77.ts",
                "$h/user123/pass456/77" to "$h/***/***/77",
                "$h/user123/pass456/77.m3u8" to "$h/***/***/77.m3u8",
                "$h/user123/pass456/ch-7a" to "$h/***/***/ch-7a",
                "$h/user123/pass456/abc9.m3u8" to "$h/***/***/abc9.m3u8",
                "$h/user123/pass456/77.ts?token=tok789" to "$h/***/***/77.ts?token=***",
                "Błąd: nie można pobrać $h/user123/pass456/77.ts (HTTP 404)" to
                    "Błąd: nie można pobrać $h/***/***/77.ts (HTTP 404)",
            )
        for ((input, expected) in cases) assertEquals(expected, Redactor.redact(input))
    }

    @Test
    fun `zagniezdzone adresy sa usuwane w calosci bez dekodowania`() {
        assertEquals(
            "https://p.example.invalid/r?u=***&x=1",
            Redactor.redact("https://p.example.invalid/r?u=http%3A%2F%2Fh%2Fuser123%2Fpass456%2F1&x=1"),
        )
        assertEquals(
            "https://p.example.invalid/***/***",
            Redactor.redact("https://p.example.invalid/go/http%3A%2F%2Fh%2Fuser123%2Fpass456%2F1"),
        )
    }

    @Test
    fun `falszywe trafienia szerokiej reguly sa swiadome, a zasoby statyczne i tekst poza URL nietkniete`() {
        assertEquals(
            "https://cdn.example.invalid/***/***/tvp1.m3u8",
            Redactor.redact("https://cdn.example.invalid/live/pl/tvp1.m3u8"),
        )
        for (t in listOf(
            "https://img.example.invalid/logos/pl/tvp1.png",
            "plik /sdcard/Download/log/2026/11",
            "login=jan key=20",
        )) {
            assertEquals(t, Redactor.redact(t))
        }
    }

    @Test
    fun `idempotencja i dlugie wejscie`() {
        val inputs =
            listOf(
                "$h/user123/pass456/77",
                "http://user123:pass456@srv.example.invalid/x",
                "$h/get.php?password=pass456",
            )
        for (i in inputs) assertEquals(Redactor.redact(i), Redactor.redact(Redactor.redact(i)))
        val big = (1..20_000).joinToString("\n") { "wpis $it $h/user123/pass456/$it.ts?token=tok789" }
        val start = System.nanoTime()
        val out = Redactor.redact(big)
        assertFalse(out.contains("pass456") || out.contains("tok789"))
        assertTrue("maskowanie trwało zbyt długo", (System.nanoTime() - start) / 1_000_000 < 10_000)
    }

    @Test
    fun `tresc odpowiedzi serwera przechodzi przez Redactor w sanitizeDetail`() {
        val out = sanitizeDetail("<p>The requested URL $h/user123/pass456/77.ts was not found</p>")
        assertFalse(out, out.contains("user123") || out.contains("pass456"))
    }
}
