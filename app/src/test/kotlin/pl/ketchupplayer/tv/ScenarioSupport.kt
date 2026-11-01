package pl.ketchupplayer.tv

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Serwer HTTP dostawcy: ścieżka -> treść. Nieznane ścieżki dają 404 (jak prawdziwy serwer). */
class ProviderServer {
    private val routes = java.util.concurrent.ConcurrentHashMap<String, (RecordedRequest) -> String>()
    private val server =
        MockWebServer().apply {
            dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.path.orEmpty().substringBefore('?')
                        // Klucz „ścieżka?action” (API Xtream) ma pierwszeństwo przed samą ścieżką.
                        val action = request.requestUrl?.queryParameter("action")
                        val handler =
                            (action?.let { routes["$path?$it"] } ?: routes[path])
                                ?: return MockResponse().setResponseCode(404)
                        // Wyjątek w handlerze = awaria serwera dostawcy: 503, jak prawdziwy serwer za proxy.
                        val body =
                            runCatching { handler(request) }.getOrElse { return MockResponse().setResponseCode(503) }
                        // Treść „HTTP404” = serwer odrzuca żądanie (np. nieznany User-Agent), jak panele IPTV.
                        if (body == NOT_FOUND) return MockResponse().setResponseCode(404)
                        return MockResponse().setBody(body)
                    }
                }
            start()
        }

    fun serve(
        path: String,
        body: () -> String,
    ) {
        routes[path] = { body() }
    }

    /** Jak [serve], ale odpowiedź zależy od żądania (np. dane logowania w zapytaniu Xtream). */
    fun serveRequest(
        path: String,
        body: (RecordedRequest) -> String,
    ) {
        routes[path] = body
    }

    /** Adres serwera bez ścieżki (np. `http://127.0.0.1:1234`), jak wpisuje użytkownik w formularzu Xtream. */
    fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    fun url(path: String): String = server.url(path).toString()

    val port: Int get() = server.port

    fun shutdown() = server.shutdown()

    companion object {
        const val NOT_FOUND = "HTTP404"
    }
}

/** Kształt prawdziwych list dostawców IPTV, generowany deterministycznie (bez danych żadnego konta). */
object ListFactory {
    /**
     * Lista o skali i strukturze prawdziwej: kanały na żywo w grupach `PL| ...`/`UK| ...` z przedrostkami
     * w nazwach, filmy (`/movie/`) i odcinki seriali (`/series/`).
     * Domyślnie ~4,6 tys. kanałów, ~19 tys. filmów, ~76 tys. odcinków.
     */
    fun bigProviderList(
        live: Int = 4_578,
        movies: Int = 19_157,
        episodes: Int = 76_517,
    ): String {
        val sb = StringBuilder(32 * 1024 * 1024)
        sb.append("#EXTM3U\n")
        val groups = listOf("PL| POLAND VIP", "PL| SPORTOWE", "PL| REGIONALNE", "UK| GENERAL", "UK| BBCI", "USA", "")
        for (i in 0 until live) {
            val g = groups[i % groups.size]
            val prefix =
                if (g.startsWith("PL")) {
                    "PL-VIP| "
                } else if (g.startsWith("UK")) {
                    "UK| "
                } else {
                    ""
                }
            val id = if (i % 3 == 0) """tvg-id="Kanal$i.pl" """ else """tvg-id="" """
            sb.append("#EXTINF:-1 ${id}tvg-name=\"$prefix Kanal $i\" tvg-logo=\"http://logo.example.invalid/$i.png\" ")
            sb.append("group-title=\"$g\",$prefix Kanal $i\nhttp://p.example.invalid:80/live/u/p/$i.ts\n")
        }
        for (i in 0 until movies) {
            sb.append("#EXTINF:-1 tvg-id=\"\" group-title=\"FILM| Dramat\",Film $i (2020)\n")
            sb.append("http://p.example.invalid:80/movie/u/p/$i.mp4\n")
        }
        for (i in 0 until episodes) {
            sb.append("#EXTINF:-1 tvg-id=\"\" group-title=\"SERIAL| Netflix\",Serial ${i / 10} S01 E${i % 10}\n")
            sb.append("http://p.example.invalid:80/series/u/p/$i.mp4\n")
        }
        return sb.toString()
    }

    /** XMLTV z audycją trwającą teraz (od -10 min do +50 min) i następną, dla kanału o `tvg-id` = [epgId]. */
    fun xmltvNow(
        epgId: String,
        nowTitle: String,
        nextTitle: String,
        nowMs: Long = System.currentTimeMillis(),
        displayName: String = epgId,
    ): String {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun t(ms: Long) = fmt.format(Date(ms)) + " +0000"
        val min = 60_000L
        return """<?xml version="1.0" encoding="UTF-8"?>
<tv>
<channel id="$epgId"><display-name>$displayName</display-name></channel>
<programme start="${t(
            nowMs - 10 * min,
        )}" stop="${t(nowMs + 50 * min)}" channel="$epgId"><title lang="pl">$nowTitle</title></programme>
<programme start="${t(
            nowMs + 50 * min,
        )}" stop="${t(nowMs + 110 * min)}" channel="$epgId"><title lang="pl">$nextTitle</title></programme>
</tv>
"""
    }
}
