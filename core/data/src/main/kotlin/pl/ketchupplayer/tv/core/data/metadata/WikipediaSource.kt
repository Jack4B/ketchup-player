package pl.ketchupplayer.tv.core.data.metadata

import pl.ketchupplayer.tv.core.network.TextFetcher
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * Opis filmu lub serialu z polskiej Wikipedii, bez klucza API (REST: wyszukiwanie i streszczenie strony). To jedyne
 * źródło opisów, które działa od razu po instalacji; oceny wciąż wymagają kluczy TMDB/OMDb. Nie scrapujemy żadnych
 * serwisów ocen (Filmweb, IMDb): ich regulaminy tego zabraniają, a Filmweb nie ma publicznego API.
 *
 * Trafienie musi wyglądać na stronę filmu: opis strony zawiera „film” albo „serial”, tytuł strony zgadza się z tytułem
 * pozycji, a rok (jeśli znamy go z obu stron) różni się najwyżej o 1.
 */
class WikipediaSource(
    private val fetcher: TextFetcher,
    private val endpoints: MetadataEndpoints,
) {
    suspend fun lookup(
        title: String,
        year: Int?,
        series: Boolean,
    ): String? {
        val kindWord = if (series) "serial" else "film"
        val query = URLEncoder.encode("$title $kindWord ${year ?: ""}".trim(), "UTF-8")
        val searchUrl =
            "${endpoints.wikipedia}/w/api.php?action=query&list=search&srsearch=$query&srlimit=$CANDIDATES&format=json"
        val hits =
            fetcher
                .fetch(searchUrl)
                .getOrNull()
                ?.let(MetadataJson::parse)
                ?.get("query")
                .obj()
                ?.get("search")
                .arr()
                ?.mapNotNull { it.obj()?.str("title") }
                .orEmpty()
        return hits.firstNotNullOfOrNull { pageTitle ->
            if (sameTitle(pageTitle, title)) filmExtract(pageTitle, year, series) else null
        }
    }

    /** Streszczenie strony, o ile to film lub serial (nie strona ujednoznaczniająca ani pies o tej samej nazwie). */
    private suspend fun filmExtract(
        pageTitle: String,
        year: Int?,
        series: Boolean,
    ): String? {
        val encoded =
            URLEncoder
                .encode(pageTitle.replace(' ', '_'), "UTF-8")
                .replace("+", "%20")
                .replace("%28", "(")
                .replace("%29", ")")
        val page =
            fetcher
                .fetch("${endpoints.wikipedia}/api/rest_v1/page/summary/$encoded")
                .getOrNull()
                ?.let(MetadataJson::parse) ?: return null
        val extract = page.str("extract") ?: return null
        val film =
            page.str("type") != "disambiguation" &&
                looksLikeFilm(page.str("description").orEmpty(), extract, year, series)
        return if (film) shorten(extract) else null
    }

    private fun looksLikeFilm(
        description: String,
        extract: String,
        year: Int?,
        series: Boolean,
    ): Boolean {
        val d = description.lowercase(Locale.ROOT)
        val isFilm = if (series) "serial" in d || "telenowela" in d else "film" in d
        if (!isFilm) return false
        if (year == null) return true
        val years = YEAR.findAll("$description $extract").map { it.value.toInt() }.toList()
        return years.isEmpty() || years.any { kotlin.math.abs(it - year) <= 1 }
    }

    /** Tytuł strony bywa z dopiskiem „(film)”: porównujemy część przed nawiasem. */
    private fun sameTitle(
        pageTitle: String,
        title: String,
    ): Boolean = normalize(pageTitle.substringBefore(" (")) == normalize(title)

    private fun normalize(s: String): String =
        Normalizer
            .normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NON_ALNUM, "")

    /** Streszczenie bywa długie: bierzemy pierwsze zdania do około [MAX_CHARS] znaków. */
    private fun shorten(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val cut = text.take(MAX_CHARS)
        val end = cut.lastIndexOf(". ")
        return if (end > MAX_CHARS / 2) cut.take(end + 1) else cut.trimEnd() + "…"
    }

    private companion object {
        const val CANDIDATES = 4
        const val MAX_CHARS = 700
        val YEAR = Regex("""\b(19|20)\d{2}\b""")
        val DIACRITICS = Regex("""\p{InCombiningDiacriticalMarks}+""")
        val NON_ALNUM = Regex("""[^a-z0-9]""")
    }
}
