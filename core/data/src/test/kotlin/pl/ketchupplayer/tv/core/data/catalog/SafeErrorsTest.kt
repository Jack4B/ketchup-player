package pl.ketchupplayer.tv.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.provider.api.ProviderError

/** Dane wyłącznie fikcyjne (`*.example.invalid`, user123, pass456). */
class SafeErrorsTest {
    private val secretUrl = "http://srv.example.invalid:8080/user123/pass456/77.ts"

    @Test
    fun `nosniki bezpiecznych komunikatow zachowuja tekst`() {
        assertEquals("HTTP 404", safeErrorText(ProviderError.Network("HTTP 404"), "x"))
        assertEquals("HTTP 503", safeErrorText(NetworkException.Http(503), "x"))
        assertEquals("Za mało pamięci", safeErrorText(UserFacingException("Za mało pamięci"), "x"))
    }

    @Test
    fun `nosniki bezpiecznych komunikatow sa dodatkowo maskowane`() {
        val out = safeErrorText(ProviderError.BadResponse("odpowiedź z $secretUrl"), "x")
        assertFalse(out, out.contains("user123") || out.contains("pass456"))
    }

    @Test
    fun `nieznany wyjatek daje komunikat ogolny z kodem klasy, nigdy surowy tekst`() {
        val out = safeErrorText(IllegalArgumentException("unexpected url: $secretUrl"), "Błąd sieci")
        assertEquals("Błąd sieci (IllegalArgumentException)", out)
        assertTrue(safeErrorText(null, "Błąd") == "Błąd")
    }

    @Test
    fun `probe opisuje ksztalt odpowiedzi zamiast przepisywac jej tresc`() {
        assertEquals("lista M3U (#EXTM3U)", probeShape("#EXTM3U url-tvg=\"$secretUrl\""))
        assertEquals("odpowiedź JSON", probeShape("""{"user_info":{"username":"user123","password":"pass456"}}"""))
        val page = "The requested URL /user123/pass456/77 was not found"
        assertEquals("odpowiedź w innym formacie", probeShape(page))
        assertEquals("pusta odpowiedź", probeShape("  "))
    }
}
