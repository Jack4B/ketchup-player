package pl.ketchupplayer.tv.core.data.logos

import java.text.Normalizer

/**
 * Indeks logo stacji z publicznej bazy iptv-org (plik `assets/logos/iptv-org.tsv`, budowany przez
 * `tools/logos/build_logo_index.py`). Pozwala uzupełnić logo, gdy lista dostawcy go nie podaje.
 *
 * Dopasowanie: najpierw `tvg-id` równe identyfikatorowi iptv-org (np. „TVN.pl”), potem znormalizowana nazwa
 * (bez wielkości liter, polskich znaków, przedrostka kraju „PL|”, nawiasów i sufiksów jakości HD/FHD/4K).
 */
class LogoIndex private constructor(
    private val byId: Map<String, String>,
    private val byName: Map<String, String>,
) {
    val size: Int get() = byId.size

    fun find(
        name: String,
        epgId: String? = null,
    ): String? {
        epgId?.takeIf { it.isNotBlank() }?.let { id -> byId[id.lowercase()]?.let { return it } }
        val key = normalize(name)
        return if (key.isEmpty()) null else byName[key]
    }

    companion object {
        /** Wiersz: `id<TAB>nazwa1|nazwa2<TAB>url`. Błędne wiersze są pomijane. */
        fun parse(tsv: String): LogoIndex {
            val ids = HashMap<String, String>()
            val names = HashMap<String, String>()
            for (line in tsv.lineSequence()) {
                val cols = line.split('\t')
                if (cols.size < COLUMNS || !cols[2].startsWith("https://")) continue
                ids[cols[0].lowercase()] = cols[2]
                for (n in cols[1].split('|')) if (n.isNotEmpty()) names.putIfAbsent(n, cols[2])
            }
            return LogoIndex(ids, names)
        }

        val EMPTY = LogoIndex(emptyMap(), emptyMap())

        /** Indeks używany przez mapowanie kanałów; ustawiany raz przy starcie aplikacji ([install]). */
        @Volatile
        var current: LogoIndex = EMPTY
            private set

        fun install(index: LogoIndex) {
            current = index
        }

        private const val COLUMNS = 3
        private val COUNTRY_PREFIX = Regex("""^\s*\[?[A-Za-z]{2}]?\s*[|:]\s*""")
        private val PARENS = Regex("""\([^)]*\)|\[[^]]*]""")
        private val QUALITY = Regex("""\b(hd|fhd|uhd|4k|sd|hevc|h265|tv)\b""")
        private val NON_ALNUM = Regex("""[^a-z0-9]+""")
        private val MARKS = Regex("""\p{M}+""")

        /** Ta sama normalizacja co w `build_logo_index.py` plus czyszczenie typowe dla list IPTV. */
        fun normalize(raw: String): String {
            var s = raw.replace(COUNTRY_PREFIX, "").replace(PARENS, " ")
            s = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "").replace('ł', 'l')
            return s.replace(QUALITY, " ").replace(NON_ALNUM, "")
        }
    }
}
