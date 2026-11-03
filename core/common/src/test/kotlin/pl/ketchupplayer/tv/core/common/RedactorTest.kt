package pl.ketchupplayer.tv.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    @Test
    fun `maskuje parametry zapytania`() {
        val out = Redactor.redact("http://h/get.php?username=jan&password=tajne&type=m3u")
        assertFalse(out.contains("jan"))
        assertFalse(out.contains("tajne"))
        assertEquals("http://h/get.php?username=***&password=***&type=m3u", out)
    }

    @Test
    fun `maskuje token`() = assertEquals("token=***", Redactor.redact("token=abc123"))

    @Test
    fun `maskuje sciezke Xtream`() {
        assertEquals("http://h:80/live/***/***/123.ts", Redactor.redact("http://h:80/live/jan/tajne/123.ts"))
        assertEquals("/movie/***/***/9.mkv", Redactor.redact("/movie/u/p/9.mkv"))
        assertEquals("/series/***/***/9.mkv", Redactor.redact("/series/u/p/9.mkv"))
    }

    @Test
    fun `maskuje naglowki`() {
        assertEquals("Authorization: ***", Redactor.redact("Authorization: Bearer xyz"))
        assertEquals("Cookie: ***", Redactor.redact("Cookie: a=b; c=d"))
    }

    @Test
    fun `maskuje adres MAC`() {
        assertEquals("mac ***", Redactor.redact("mac 00:1A:79:AB:CD:EF"))
        assertEquals("id ***", Redactor.redact("id 00-1a-79-ab-cd-ef"))
    }

    @Test
    fun `zwykly tekst bez zmian`() = assertEquals("Odtwarzanie kanalu 5", Redactor.redact("Odtwarzanie kanalu 5"))
}
