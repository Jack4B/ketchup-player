package pl.ketchupplayer.tv.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.common.ErrorText
import pl.ketchupplayer.tv.core.common.SafeErrorMessage
import java.time.Instant

/** Dane wyłącznie fikcyjne (`*.example.invalid`, user123, pass456). */
class ResponseDetailTest {
    private val host = "http://srv.example.invalid:8080"

    @Test
    fun `adres z danymi dostepu w tresci odpowiedzi jest maskowany`() {
        val out =
            sanitizeDetail("<html><body>The requested URL $host/user123/pass456/77.ts was not found</body></html>")
        assertFalse(out, out.contains("user123") || out.contains("pass456"))
        assertTrue(out, out.contains("was not found"))
    }

    @Test
    fun `przyciecie nastepuje po maskowaniu`() {
        val out = sanitizeDetail("x".repeat(140) + " $host/user123/pass456/77.ts")
        assertTrue(out.length <= 160)
        assertFalse(out, out.contains("user123") || out.contains("pass456"))
    }

    @Test
    fun `znana granica - sanitizeDetail nie maskuje sciezki bez schematu, wiec tresci serwera sie nie przepisuje`() {
        assertTrue(sanitizeDetail("The requested URL /user123/pass456/77 was not found").contains("pass456"))
        assertEquals("nie znaleziono zasobu", bodyHint("The requested URL /user123/pass456/77 was not found"))
        assertEquals(null, bodyHint("Internal user123 secret page"))
    }

    @Test
    fun `wyjatki sieciowe sa oznaczone jako bezpieczne, a ich tekst nie zawiera adresu`() {
        // Przypisanie do typu SafeErrorMessage sprawdza supertyp w kompilacji (bez ostrzeżenia „is always true”).
        val http: SafeErrorMessage = NetworkException.Http(404)
        assertEquals("HTTP 404", ErrorText.of(http as Throwable, "x"))
        val io = NetworkException.Io(IllegalArgumentException("unexpected url: $host/user123/pass456/77.ts"))
        assertEquals("Błąd sieci: IllegalArgumentException", ErrorText.of(io, "x"))
        assertEquals("HTTP 503", ErrorText.of(NetworkException.Http(503), "x"))
    }

    // --- Retry-After: nagłówek jest nieufny, do komunikatu idzie tylko sparsowana liczba sekund. ---

    private val now = Instant.parse("2026-11-08T20:00:00Z")

    @Test
    fun `Retry-After w sekundach jest parsowany`() {
        assertEquals(120L, parseRetryAfter("120", now))
        assertEquals(30L, parseRetryAfter(" 30 ", now))
        assertEquals(0L, parseRetryAfter("0", now))
    }

    @Test
    fun `Retry-After jako data HTTP daje sekundy wzgledem biezacego czasu`() {
        assertEquals(120L, parseRetryAfter("Sun, 08 Nov 2026 20:02:00 GMT", now))
        assertEquals(0L, parseRetryAfter("Sun, 08 Nov 2026 19:00:00 GMT", now))
    }

    @Test
    fun `niepoprawny Retry-After jest odrzucany, takze z danymi dostepu`() {
        val bad =
            listOf(
                "",
                "abc",
                "-5",
                "1.5",
                "12 s",
                "user123:pass456",
                "/user123/pass456/77",
                "120; user123:pass456",
                "Sun, 99 Foo 2026 user123 GMT",
                "Mon, 08 Nov 2026 20:02:00 GMT",
                "Thu, 01 Jan 2099 00:00:00 GMT",
                "9999999999",
            )
        bad.forEach { assertEquals(it, null, parseRetryAfter(it, now)) }
    }
}
