package pl.ketchupplayer.tv.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // --- Adresy w kontekście URL (issue #12). Dane wyłącznie fikcyjne: *.example.invalid, user123, pass456. ---

    private val host = "http://srv.example.invalid:8080"
    private val secrets = listOf("user123", "pass456", "tok789", "key789", "p%40ss")

    private fun assertNoSecret(out: String) = secrets.forEach { assertFalse("wyciek '$it' w: $out", out.contains(it)) }

    @Test
    fun `userinfo w adresie jest maskowane`() {
        assertEquals(
            "http://***@srv.example.invalid/list.m3u",
            Redactor.redact("http://user123:pass456@srv.example.invalid/list.m3u"),
        )
        assertEquals("https://***@h.example.invalid:8443/x", Redactor.redact("https://tok789@h.example.invalid:8443/x"))
    }

    @Test
    fun `parametry zapytania w adresie - rozszerzona lista nazw i wielkosc liter`() {
        val out =
            Redactor.redact(
                "$host/api?Login=user123&PWD=pass456&access_token=tok789&auth=a1&secret=s2&key=key789&type=m3u",
            )
        assertNoSecret(out)
        assertEquals("$host/api?Login=***&PWD=***&access_token=***&auth=***&secret=***&key=***&type=m3u", out)
    }

    @Test
    fun `parametry po sredniku i we fragmencie`() {
        assertNoSecret(Redactor.redact("$host/x;token=tok789;a=1"))
        assertNoSecret(Redactor.redact("$host/x#password=pass456"))
    }

    @Test
    fun `nowe nazwy parametrow dzialaja tylko w adresie, nie w zwyklym tekscie`() {
        assertEquals("login=jan key=20 auth=1", Redactor.redact("login=jan key=20 auth=1"))
    }

    @Test
    fun `sciezka z prefiksem live movie series timeshift - z rozszerzeniem i bez`() {
        assertEquals("$host/live/***/***/77.ts", Redactor.redact("$host/live/user123/pass456/77.ts"))
        assertEquals("$host/live/***/***/77", Redactor.redact("$host/live/user123/pass456/77"))
        assertEquals("$host/movie/***/***/77.mkv", Redactor.redact("$host/movie/user123/pass456/77.mkv"))
        assertEquals("$host/series/***/***/77.mp4", Redactor.redact("$host/series/user123/pass456/77.mp4"))
        assertEquals(
            "$host/timeshift/***/***/60/2026-01-01:20-00/77.ts",
            Redactor.redact("$host/timeshift/user123/pass456/60/2026-01-01:20-00/77.ts"),
        )
    }

    @Test
    fun `sciezka bez prefiksu - identyfikator liczbowy, tekstowy, z rozszerzeniem i bez`() {
        assertEquals("$host/***/***/77", Redactor.redact("$host/user123/pass456/77"))
        assertEquals("$host/***/***/77.ts", Redactor.redact("$host/user123/pass456/77.ts"))
        assertEquals("$host/***/***/77.m3u8", Redactor.redact("$host/user123/pass456/77.m3u8"))
        assertEquals("$host/***/***/ch-7a", Redactor.redact("$host/user123/pass456/ch-7a"))
        assertEquals("$host/***/***/abc9.m3u8", Redactor.redact("$host/user123/pass456/abc9.m3u8"))
        assertEquals("$host/***/***/77/index.m3u8", Redactor.redact("$host/user123/pass456/77/index.m3u8"))
        assertEquals("$host/***/***/77", Redactor.redact("$host/user123//77")) // puste hasło
        assertEquals("$host//***/***/77", Redactor.redact("$host//user123/pass456/77")) // podwójny ukośnik po hoście
        assertEquals("$host/***/***/77/", Redactor.redact("$host/user123/pass456/77/")) // końcowy ukośnik
    }

    @Test
    fun `sciezka z zapytaniem po identyfikatorze`() {
        assertEquals("$host/***/***/77.ts?token=***", Redactor.redact("$host/user123/pass456/77.ts?token=tok789"))
    }

    @Test
    fun `znaki zakodowane w segmentach i parametrach`() {
        assertNoSecret(Redactor.redact("$host/user123/p%40ss%2Fxx/77.ts"))
        assertNoSecret(Redactor.redact("$host/get.php?username=user123&password=p%40ss%2F456&type=m3u"))
    }

    @Test
    fun `adres w tekscie wyjatku - przed, po, w cudzyslowie i nawiasie, kilka adresow`() {
        assertEquals(
            "Błąd: nie można pobrać $host/***/***/77.ts (HTTP 404), spróbuj ponownie",
            Redactor.redact("Błąd: nie można pobrać $host/user123/pass456/77.ts (HTTP 404), spróbuj ponownie"),
        )
        assertEquals(
            "Failed (\"$host/live/***/***/77.ts\")",
            Redactor.redact("Failed (\"$host/live/user123/pass456/77.ts\")"),
        )
        assertEquals("unexpected url: $host/***/***/77.", Redactor.redact("unexpected url: $host/user123/pass456/77."))
        val two = Redactor.redact("a $host/user123/pass456/1 i $host/get.php?password=pass456 koniec")
        assertNoSecret(two)
        assertTrue(two.startsWith("a ") && two.endsWith(" koniec"))
    }

    @Test
    fun `zagniezdzony adres zakodowany - cala wartosc jest usuwana, bez dekodowania`() {
        val nested =
            "https://proxy.example.invalid/r?u=http%3A%2F%2Fsrv.example.invalid%2Flive%2Fuser123%2Fpass456%2F77.ts&x=1"
        assertEquals("https://proxy.example.invalid/r?u=***&x=1", Redactor.redact(nested))
        assertEquals(
            "https://proxy.example.invalid/r?u=***",
            Redactor.redact("https://proxy.example.invalid/r?u=$host/user123/pass456/77.ts"),
        )
        assertEquals(
            "https://p.example.invalid/r?u=***",
            Redactor.redact("https://p.example.invalid/r?u=http%253A%252F%252Fh%252Fuser123%252Fpass456%252F1"),
        )
        assertEquals(
            "https://p.example.invalid/r?next=***",
            Redactor.redact("https://p.example.invalid/r?next=a%3Fpassword%3Dpass456"),
        )
    }

    @Test
    fun `zagniezdzony adres w sciezce - segment, reszta sciezki i poprzedzajace segmenty sa usuwane`() {
        val out =
            Redactor.redact(
                "https://p.example.invalid/go/http%3A%2F%2Fsrv.example.invalid%2Fuser123%2Fpass456%2F77.ts",
            )
        assertEquals("https://p.example.invalid/***/***", out)
        assertEquals(
            "https://p.example.invalid/***/***",
            Redactor.redact("https://p.example.invalid/go/$host/user123/pass456/77.ts"),
        )
        // Ślad w trzecim segmencie: pierwsze dwa maskuje szeroka reguła, reszta od śladu znika.
        assertEquals(
            "https://p.example.invalid/***/***/***",
            Redactor.redact("https://p.example.invalid/a/b/http%3A%2F%2Fh%2Fuser123%2Fpass456"),
        )
    }

    // --- Fałszywe trafienia (świadome: szeroka reguła w kontekście URL) i granice ---

    @Test
    fun `falszywe trafienia szerokiej reguly - maskuje dwa pierwsze segmenty sciezki o trzech i wiecej segmentach`() {
        assertEquals(
            "https://cdn.example.invalid/***/***/tvp1.m3u8",
            Redactor.redact("https://cdn.example.invalid/live/pl/tvp1.m3u8"),
        )
        assertEquals(
            "https://api.example.invalid/***/***/tt0133093",
            Redactor.redact("https://api.example.invalid/v1/titles/tt0133093"),
        )
    }

    @Test
    fun `nie dotyka zasobow statycznych, krotkich sciezek ani tekstu poza adresem`() {
        for (u in listOf(
            "https://img.example.invalid/logos/pl/tvp1.png",
            "https://epg.example.invalid/guide/pl/epg.xml",
            "https://epg.example.invalid/guide/pl/epg.xml.gz",
            "https://example.invalid/channels/77.m3u8",
            "https://example.invalid/",
            "https://example.invalid",
            "file:///data/user/0/app/files/index.db",
            "content://media/external/video/media/1",
        )) {
            assertEquals(u, Redactor.redact(u))
        }
        for (t in listOf(
            "plik /sdcard/Download/log/2026/11",
            "a/b/c.txt",
            "srv.example.invalid:8080/user123/pass456/77",
            "Odtwarzanie kanalu 5",
        )) {
            assertEquals(t, Redactor.redact(t))
        }
    }

    @Test
    fun `znana granica - adres bez schematu, JSON i wlasne naglowki nie sa maskowane`() {
        assertTrue(Redactor.redact("srv.example.invalid:8080/user123/pass456/77").contains("pass456"))
        assertTrue(Redactor.redact("{\"password\":\"pass456\"}").contains("pass456"))
        assertTrue(Redactor.redact("trakt-api-key: key789").contains("key789"))
    }

    @Test
    fun `idempotencja - powtorne maskowanie nic nie zmienia`() {
        val inputs =
            listOf(
                "$host/live/user123/pass456/77.ts",
                "$host/user123/pass456/77",
                "http://user123:pass456@srv.example.invalid/list.m3u",
                "$host/get.php?username=user123&password=pass456&type=m3u",
                "https://p.example.invalid/r?u=http%3A%2F%2Fh%2Fuser123%2Fpass456%2F1",
                "Błąd: $host/user123/pass456/77.ts (HTTP 404)",
                "plik /sdcard/Download/log/2026/11",
            )
        for (i in inputs) {
            val once = Redactor.redact(i)
            assertEquals(once, Redactor.redact(once))
        }
    }

    @Test
    fun `dlugie wejscie nie zawiesza maskowania`() {
        val big = StringBuilder()
        repeat(20_000) { big.append("wpis $it $host/user123/pass456/$it.ts?token=tok789 http://a/b/c/d/e/f\n") }
        val patho = "http://h/" + "a/".repeat(50_000) + "%".repeat(50_000)
        val start = System.nanoTime()
        val out = Redactor.redact(big.toString())
        Redactor.redact(patho)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertNoSecret(out)
        assertTrue("maskowanie trwało $ms ms", ms < 10_000)
    }
}
