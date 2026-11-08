package pl.ketchupplayer.tv.core.data.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportErrorsTest {
    private fun msg(raw: String?) = friendlyImportError(raw)

    @Test
    fun `kody HTTP dostaja wskazowke co zrobic`() {
        assertTrue(msg("HTTP 404").contains("Sprawdź adres"))
        assertTrue(msg("Dostawca odmówił dostępu (HTTP 403)").contains("nieaktywne"))
        assertTrue(msg("HTTP 401").contains("nieaktywne"))
        assertTrue(msg("HTTP 503").contains("awarię"))
        assertTrue(msg("HTTP 429").contains("błąd (HTTP 429)"))
    }

    @Test
    fun `bledy sieci sa opisane po polsku`() {
        assertTrue(msg("Błąd sieci: SocketTimeoutException").contains("nie odpowiada"))
        assertTrue(msg("Błąd sieci: UnknownHostException").contains("Nie znaleziono serwera"))
        assertTrue(msg("Błąd sieci: SSLHandshakeException").contains("certyfikat"))
        assertTrue(msg("Błąd sieci: ConnectException").contains("adres i port"))
    }

    @Test
    fun `za duza lista podaje limit w MB`() {
        assertTrue(msg("Odpowiedź większa niż 67108864 B").contains("64 MB"))
    }

    @Test
    fun `odpowiedz bez kanalow tlumaczy mozliwe przyczyny`() {
        assertTrue(msg("Lista nie zawiera żadnych kanałów ani filmów").contains("nieaktywne"))
    }

    @Test
    fun `pusty i nieznany blad nie ukrywa tresci`() {
        assertTrue(msg(null).contains("Nieznany"))
        assertTrue(msg("coś innego") == "coś innego")
    }

    @Test
    fun `komunikat HTTP zawiera odpowiedz serwera`() {
        val msg = friendlyImportError("HTTP 513 · serwer: nginx, «Too many connections»")
        assertTrue("HTTP 513" in msg && "Too many connections" in msg && "nginx" in msg)
    }

    // --- Issue #12: dane dostępu w tekście błędu (wyłącznie fikcyjne). ---

    private val secretUrl = "http://srv.example.invalid:8080/user123/pass456/77.ts"

    private fun assertNoSecret(out: String) = assertFalse(out, out.contains("user123") || out.contains("pass456"))

    @Test
    fun `nieznany tekst z adresem danych dostepu jest maskowany`() {
        val out = msg("unexpected url: $secretUrl")
        assertNoSecret(out)
        assertTrue(out.startsWith("unexpected url: "))
    }

    @Test
    fun `odpowiedz serwera z adresem w komunikacie HTTP jest maskowana`() {
        val out = msg("HTTP 404 · serwer: nginx, «nie znaleziono $secretUrl»")
        assertNoSecret(out)
        assertTrue("HTTP 404" in out || "404" in out)
    }

    @Test
    fun `dlugi nieznany tekst jest przyciety po maskowaniu`() {
        val out = msg("x".repeat(230) + " $secretUrl")
        assertTrue(out.length <= 240)
        assertNoSecret(out)
    }
}
