package pl.ketchupplayer.tv.core.data.metadata

import android.database.sqlite.SQLiteDatabase
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Film lub serial rozpoznany w wbudowanym indeksie (Wikidata, CC0): identyfikatory bez żadnego zapytania do sieci. */
data class FilmRef(
    val imdb: String,
    val tmdb: Long?,
    val year: Int,
    /** Tytuł strony polskiej Wikipedii (do pobrania opisu bez wyszukiwania), jeśli film ją ma. */
    val plWikiTitle: String?,
    val plTitle: String?,
    val enTitle: String?,
    /** Ocena IMDb z publicznych danych IMDb (migawka z dnia budowy indeksu), bez żadnego klucza API. */
    val imdbRating: Double? = null,
    val imdbVotes: Int? = null,
)

/** Wbudowany, offline indeks filmów i seriali od 1980 roku: tytuł (PL, EN, oryginalny) + rok -> identyfikatory. */
fun interface FilmIndex {
    /**
     * Pozycja dla [title] i [year] (±1 rok). Bez roku: gdy tytuł jest jednoznaczny albo jedna pozycja jest wyraźnie
     * najpopularniejsza ([FilmPicker]); w razie wątpliwości null (lepiej bez oceny niż z cudzą).
     */
    fun find(
        title: String,
        year: Int?,
        series: Boolean,
    ): FilmRef?
}

/** Pusty indeks (brak pliku w kompilacji): wszystko idzie starą drogą, przez wyszukiwanie w sieci. */
object NoFilmIndex : FilmIndex {
    override fun find(
        title: String,
        year: Int?,
        series: Boolean,
    ): FilmRef? = null
}

/**
 * Normalizacja tytułu do klucza wyszukiwania: małe litery, bez znaków diakrytycznych (ł -> l), tylko a-z0-9.
 * MUSI być identyczna z `normalize` w `tools/wikidata/pack_index.py` (inaczej indeks nic nie znajdzie).
 */
object FilmTitles {
    private val extra = mapOf('ł' to "l", 'đ' to "d", 'ø' to "o", 'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'ı' to "i")
    private val marks = Regex("\\p{Mn}+")

    fun normalize(title: String): String {
        val lower = title.lowercase(Locale.ROOT)
        val mapped = StringBuilder()
        for (ch in lower) mapped.append(extra[ch] ?: ch.toString())
        val decomposed = Normalizer.normalize(mapped, Normalizer.Form.NFD).replace(marks, "")
        return decomposed.filter { it in 'a'..'z' || it in '0'..'9' }
    }
}

/** Indeks w SQLite (tabele `film` i `title`, patrz `tools/wikidata/pack_index.py`), otwarty tylko do odczytu. */
class SqliteFilmIndex(
    private val db: SQLiteDatabase,
) : FilmIndex {
    override fun find(
        title: String,
        year: Int?,
        series: Boolean,
    ): FilmRef? {
        val norm = FilmTitles.normalize(title)
        if (norm.isEmpty()) return null
        val kind = if (series) 1 else 0
        FilmPicker.pick(candidates(norm, kind), year)?.let { return it }
        // Tytuł z liczbą podobną do roku (`Wonder Woman 1984`) bywa rozbity na tytuł i rok: próbujemy pełnej formy.
        if (year == null) return null
        return FilmPicker.pick(candidates(FilmTitles.normalize("$title $year"), kind), null)
    }

    private fun candidates(
        norm: String,
        kind: Int,
    ): List<FilmRef> {
        val found = ArrayList<FilmRef>()
        db
            .rawQuery(
                "SELECT f.imdb, f.tmdb, f.year, f.plwiki, f.pl, f.en, f.rating, f.votes " +
                    "FROM title t JOIN film f ON f.id = t.film " +
                    "WHERE t.norm = ? AND f.kind = ? LIMIT $MAX_CANDIDATES",
                arrayOf(norm, kind.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    found +=
                        FilmRef(
                            imdb = c.getString(0),
                            tmdb = if (c.isNull(1)) null else c.getLong(1),
                            year = c.getInt(2),
                            plWikiTitle = c.getString(3),
                            plTitle = c.getString(4),
                            enTitle = c.getString(5),
                            // Oceny w indeksie to dziesiąte części (74 = 7,4); brak = film bez ocen w danych IMDb.
                            imdbRating = if (c.isNull(6)) null else c.getInt(6) / TENTHS,
                            imdbVotes = if (c.isNull(7)) null else c.getInt(7),
                        )
                }
            }
        return found
    }

    private companion object {
        const val MAX_CANDIDATES = 50
        const val TENTHS = 10.0
    }
}

/**
 * Wybór pozycji spośród kandydatów o tym samym tytule. Z rokiem: najbliższy (±1), a przy remisie ten z większą liczbą
 * głosów. Bez roku: jedyny kandydat albo lider, który ma co najmniej [MIN_LEADER_VOTES] głosów i [LEADER_RATIO] razy
 * więcej głosów niż drugi (np. nowszy, głośny remake); gdy lider nie jest wyraźnie lepszy, null zamiast zgadywania.
 * Progi dobrane na syntetycznej próbce popularnych pozycji z indeksu: więcej trafień przy błędach poniżej 1%.
 */
internal object FilmPicker {
    const val MIN_LEADER_VOTES = 10_000
    const val LEADER_RATIO = 20
    private const val YEAR_TOLERANCE = 1

    fun pick(
        candidates: List<FilmRef>,
        year: Int?,
    ): FilmRef? =
        when {
            candidates.isEmpty() -> null
            year != null ->
                candidates
                    .filter { abs(it.year - year) <= YEAR_TOLERANCE }
                    .sortedWith(compareBy<FilmRef> { abs(it.year - year) }.thenByDescending { it.imdbVotes ?: 0 })
                    .firstOrNull()
            candidates.size == 1 -> candidates.first()
            else -> clearLeader(candidates)
        }

    private fun clearLeader(candidates: List<FilmRef>): FilmRef? {
        val ranked = candidates.sortedByDescending { it.imdbVotes ?: 0 }
        val leader = ranked[0]
        val leaderVotes = leader.imdbVotes ?: 0
        val runnerUpVotes = ranked[1].imdbVotes ?: 0
        return leader.takeIf { leaderVotes >= MIN_LEADER_VOTES && leaderVotes >= LEADER_RATIO.toLong() * runnerUpVotes }
    }
}
