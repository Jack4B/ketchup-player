package pl.ketchupplayer.tv.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Dane wyłącznie fikcyjne (`*.example.invalid`, user123, pass456). Allow-list zapisanych błędów (issue #12). */
class StoredErrorsTest {
    private fun assertNoSecret(out: String?) =
        assertFalse(out, out != null && (out.contains("user123") || out.contains("pass456") || "requested URL" in out))

    @Test
    fun `stary tekst serwera bez schematu zamienia sie na ogolna informacje`() {
        val out = presentStoredError("The requested URL /user123/pass456/77 was not found")
        assertNoSecret(out)
        assertEquals(STORED_ERROR_GENERIC, out)
    }

    @Test
    fun `nieznane teksty z danymi dostepu, takze ze schematem, dawaja ogolna informacje`() {
        listOf(
            "unexpected url: http://srv.example.invalid:8080/user123/pass456/77.ts",
            "/user123/pass456/77",
            "Błąd sieci (user123/pass456)",
            "Nie udało się pobrać listy (user123:pass456)",
            "HTTP 404 · serwer: user123",
            "{\"password\":\"pass456\"}",
        ).forEach {
            val out = presentStoredError(it)
            assertNoSecret(out)
            assertEquals(it, STORED_ERROR_GENERIC, out)
        }
    }

    @Test
    fun `stan bledu jest zachowany`() {
        assertNull(presentStoredError(null))
        assertEquals(STORED_ERROR_GENERIC, presentStoredError(""))
        assertEquals(STORED_ERROR_GENERIC, presentStoredError("   "))
        assertNotNull(presentStoredError("cokolwiek"))
    }

    @Test
    fun `znane bezpieczne komunikaty przechodza bez zmian`() {
        listOf(
            httpMessage(404),
            httpMessage(503),
            MSG_TIMEOUT,
            MSG_HOST,
            MSG_NO_CHANNELS,
            VOD_SKIPPED,
            VOD_SKIPPED_MEMORY,
            "Lista jest zbyt duża (limit 50 MB). Wpisz mniejszą listę.",
            "Nie udało się pobrać listy (IllegalArgumentException)",
            "Błąd sieci: SocketTimeoutException",
            "Nieprawidłowy login lub hasło",
            "Za mało pamięci na tak dużą listę",
            httpMessage(513) + "\nOdpowiedź serwera: ponów po: 30 s, «zbyt wiele połączeń»",
        ).forEach { assertEquals(it, presentStoredError(it)) }
    }

    @Test
    fun `komunikaty produkowane przez friendlyImportError sa rozpoznawane (brak rozjazdu)`() {
        listOf(
            null,
            "",
            "HTTP 401",
            "HTTP 404 · «nie znaleziono zasobu»",
            "HTTP 503 · ponów po: 120 s",
            "Odpowiedź większa niż 52428800 B",
            "Lista nie zawiera żadnych kanałów ani filmów",
            "Błąd sieci: UnknownHostException",
            "Błąd sieci: SSLHandshakeException",
            "Błąd sieci: ConnectException",
            "Nie udało się pobrać listy (IllegalStateException)",
            "Nieprawidłowa odpowiedź serwera",
            "Konto nieaktywne lub wygasłe",
            "Za mało pamięci na tak dużą listę",
        ).forEach {
            val friendly = friendlyImportError(it)
            assertEquals(it, friendly, presentStoredError(friendly))
        }
    }

    @Test
    fun `dawny naglowek i tresc serwera w czesci Odpowiedz serwera sa odrzucane, komunikat glowny zostaje`() {
        val old =
            httpMessage(404) +
                "\nOdpowiedź serwera: serwer: nginx, «The requested URL /user123/pass456/77 was not found»"
        val out = presentStoredError(old)
        assertNoSecret(out)
        assertEquals(httpMessage(404), out)
    }
}
