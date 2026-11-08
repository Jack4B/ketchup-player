package pl.ketchupplayer.tv.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTextTest {
    private val url = "http://srv.example.invalid:8080/user123/pass456/77.ts"

    private class Safe(
        message: String,
    ) : Exception(message),
        SafeErrorMessage

    @Test
    fun `safe maskuje adres w surowym tekscie`() {
        val out = ErrorText.safe("unexpected url: $url", "Błąd")
        assertFalse(out.contains("pass456"))
        assertTrue(out.startsWith("unexpected url: "))
    }

    @Test
    fun `safe - pusty lub brakujacy tekst daje komunikat zastepczy`() {
        assertEquals("Błąd", ErrorText.safe(null, "Błąd"))
        assertEquals("Błąd", ErrorText.safe("   ", "Błąd"))
    }

    @Test
    fun `safe przycina po maskowaniu, wiec ucieta czesc nie ujawnia danych`() {
        val long = "x".repeat(ErrorText.MAX_LENGTH - 5) + " $url"
        val out = ErrorText.safe(long, "Błąd")
        assertTrue(out.length <= ErrorText.MAX_LENGTH)
        assertFalse(out.contains("pass456"))
        assertFalse(out.contains("user123"))
    }

    @Test
    fun `of - wyjatek oznaczony jako bezpieczny zachowuje komunikat po maskowaniu`() {
        assertEquals("HTTP 404 · serwer: nginx", ErrorText.of(Safe("HTTP 404 · serwer: nginx"), "Błąd"))
        assertFalse(ErrorText.of(Safe("pobieranie $url"), "Błąd").contains("pass456"))
    }

    @Test
    fun `of - nieznany wyjatek daje komunikat ogolny z kodem klasy, nigdy surowy message`() {
        val out = ErrorText.of(IllegalArgumentException("unexpected url: $url"), "Błąd sieci")
        assertEquals("Błąd sieci (IllegalArgumentException)", out)
        assertEquals("Błąd", ErrorText.of(null, "Błąd"))
    }
}
