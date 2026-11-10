package pl.ketchupplayer.tv

/**
 * Mały syntetyczny katalog o cechach dużej listy panelu IPTV, bez żadnych prawdziwych danych: kanały na żywo pod
 * adresami host/użytkownik/hasło/id (bez prefiksu), filmy i odcinki pod `/movie/` i `/series/`, grupy z prefiksem
 * kraju, tytuły z rokiem i znacznikiem S01E01, część wpisów bez grupy i bez tvg-id. Dane i adresy są zmyślone
 * (`example.invalid`, użytkownik `user123`).
 */
object SyntheticCatalog {
    const val LIVE = 4
    const val MOVIES = 6
    const val SHOWS = 2

    private const val EPISODES_PER_SHOW = 3

    private fun extinf(
        name: String,
        group: String?,
        number: Int,
    ): String {
        val groupAttr = group?.let { " group-title=\"$it\"" }.orEmpty()
        val logo = "http://l.example.invalid/$number.png"
        return "#EXTINF:-1 tvg-id=\"\" tvg-name=\"$name\" tvg-logo=\"$logo\"$groupAttr,$name"
    }

    fun m3u(host: String): String =
        buildString {
            appendLine("#EXTM3U")
            val live = listOf("PL| Kanał Jeden HD", "PL| Kanał Dwa", "PL| Kanał Trzy FHD", "Kanał Bez Grupy")
            live.forEachIndexed { i, name ->
                appendLine(extinf(name, "PL| INFO".takeIf { i < LIVE - 1 }, i + 1))
                appendLine("$host/user123/pass456/${i + 1}") // jak w prawdziwych panelach: bez /live/
            }
            val films =
                listOf("Burza", "Cichy świadek", "Złote miasto", "Most nad rzeką", "Nocny lot", "Dom nad jeziorem")
            films.forEachIndexed { i, title ->
                val group = if (i % 2 == 0) "|PL| FILMY" else "|PL| KOMEDIE"
                appendLine(extinf("PL - $title (${2018 + i})", group, 100 + i))
                appendLine("$host/movie/user123/pass456/${100 + i}.${if (i % 2 == 0) "mkv" else "mp4"}")
            }
            listOf("Komisariat 7", "Rodzina Kowalskich").forEachIndexed { s, show ->
                for (e in 1..EPISODES_PER_SHOW) {
                    appendLine(extinf("$show S01E0$e", "|PL| SERIALE", 200 + s * 10 + e))
                    appendLine("$host/series/user123/pass456/${200 + s * 10 + e}.mp4")
                }
            }
        }
}
