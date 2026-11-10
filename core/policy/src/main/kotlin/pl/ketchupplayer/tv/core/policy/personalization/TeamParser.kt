package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.personalization.TeamAlias

/** Wynik parsera tytułu EPG. [ambiguous] = tytuł pasuje do kilku drużyn: nie jest dowodem dla żadnej. */
data class TeamParse(
    val teams: List<String>,
    val competitionKey: String?,
    val pattern: String,
    val ambiguous: Boolean,
    val confidence: Double,
    val replay: Boolean = false,
    /** Tytuł ma postać pary „A - B” (druga strona może być spoza katalogu). */
    val pair: Boolean = false,
)

/** Indeks aliasów: najdłuższe dopasowanie całych słów, żeby „Wisła Kraków” wygrała z samą „Wisła”. */
class AliasIndex(
    aliases: List<TeamAlias>,
) {
    data class Hit(
        val teamIds: Set<String>,
        val weak: Boolean,
        val ambiguous: Boolean,
        val start: Int,
        val end: Int,
    )

    private data class Entry(
        val teamIds: Set<String>,
        val weak: Boolean,
        val ambiguous: Boolean,
    )

    private val byAlias: Map<List<String>, Entry> =
        aliases
            .filter { it.aliasNorm.isNotBlank() }
            .groupBy { it.aliasNorm.split(' ').filter(String::isNotEmpty) }
            .mapValues { (_, list) ->
                val ids = list.map { it.teamId }.toSet()
                Entry(ids, list.all { it.weak }, ids.size > 1 || list.any { it.ambiguousGroup != null })
            }
    private val maxWords = byAlias.keys.maxOfOrNull { it.size } ?: 0

    /** Trafienia w [normalized] (tekst po `Channel.normalizeName`), od lewej, bez nachodzenia na siebie. */
    fun find(normalized: String): List<Hit> {
        val tokens = normalized.split(' ').filter(String::isNotEmpty)
        val hits = mutableListOf<Hit>()
        var i = 0
        while (i < tokens.size) {
            val hit = longestAt(tokens, i)
            if (hit == null) {
                i++
            } else {
                hits += hit
                i = hit.end
            }
        }
        return hits
    }

    private fun longestAt(
        tokens: List<String>,
        at: Int,
    ): Hit? {
        for (len in minOf(maxWords, tokens.size - at) downTo 1) {
            val entry = byAlias[tokens.subList(at, at + len)] ?: continue
            return Hit(entry.teamIds, entry.weak, entry.ambiguous, at, at + len)
        }
        return null
    }
}

/**
 * Parser drużyn z tytułu audycji EPG. Podział na strony („A - B”, „A vs B”) robi przed normalizacją, bo normalizacja
 * usuwa separatory. Niczego nie zgaduje: nieznana nazwa to brak drużyny, alias wspólny dla kilku drużyn to
 * niejednoznaczność.
 */
object TeamParser {
    const val PAIR_CONFIDENCE = 0.85
    const val SINGLE_CONFIDENCE = 0.55
    const val WEAK_CONFIDENCE = 0.3
    const val AMBIGUOUS_CONFIDENCE = 0.2
    private const val DESCRIPTION_PENALTY = 0.1

    fun parse(
        title: String,
        description: String?,
        category: String?,
        index: AliasIndex,
    ): TeamParse {
        val replay = isReplay(title, description)
        val competition = competition(Channel.normalizeName(title + " " + description.orEmpty()))
        val sport = sportHint(category)
        val fromTitle = parseText(title, sport, index)
        val parsed =
            if (fromTitle.teams.isEmpty() && !fromTitle.ambiguous && !description.isNullOrBlank()) {
                val first =
                    description
                        .lineSequence()
                        .firstOrNull()
                        .orEmpty()
                        .take(MAX_DESCRIPTION)
                parseText(first, sport, index).let {
                    it.copy(
                        pattern = "desc-" + it.pattern,
                        confidence = (it.confidence - DESCRIPTION_PENALTY).coerceAtLeast(0.0),
                    )
                }
            } else {
                fromTitle
            }
        return parsed.copy(competitionKey = competition, replay = replay)
    }

    private fun parseText(
        text: String,
        sport: Boolean?,
        index: AliasIndex,
    ): TeamParse {
        val sides = splitHeadToHead(text)
        return if (sides != null) parsePair(sides, sport, index) else parseLoose(text, sport, index)
    }

    private fun parsePair(
        sides: Pair<String, String>,
        sport: Boolean?,
        index: AliasIndex,
    ): TeamParse {
        val left = index.find(Channel.normalizeName(sides.first)).lastOrNull()
        val right = index.find(Channel.normalizeName(sides.second)).firstOrNull()
        val hits = listOfNotNull(left, right)
        if (hits.any { it.ambiguous }) return ambiguous(hits)
        val teams = hits.map { it.teamIds.single() }.distinct()
        // Jedna znana strona: liczy się tylko, gdy nic nie wskazuje, że to nie sport, a słaby alias wymaga kategorii.
        val lonelyWeak = hits.size == 1 && hits.single().weak && sport != true
        val confidence =
            when {
                teams.size == 2 -> PAIR_CONFIDENCE
                teams.size == 1 && sport != false && !lonelyWeak -> SINGLE_CONFIDENCE
                else -> WEAK_CONFIDENCE
            }
        return TeamParse(teams, null, "h2h", false, confidence, pair = true)
    }

    private fun parseLoose(
        text: String,
        sport: Boolean?,
        index: AliasIndex,
    ): TeamParse {
        val hits = index.find(Channel.normalizeName(text)).filter { !it.weak }
        if (hits.any { it.ambiguous }) return ambiguous(hits)
        val teams = hits.map { it.teamIds.single() }.distinct().take(2)
        val confidence =
            when {
                teams.isEmpty() -> 0.0
                sport == false -> WEAK_CONFIDENCE
                teams.size == 2 -> PAIR_CONFIDENCE - DESCRIPTION_PENALTY
                else -> SINGLE_CONFIDENCE
            }
        return TeamParse(teams, null, "alias", false, confidence, pair = teams.size == 2)
    }

    private fun ambiguous(hits: List<AliasIndex.Hit>): TeamParse =
        TeamParse(hits.flatMap { it.teamIds }.distinct(), null, "ambiguous", true, AMBIGUOUS_CONFIDENCE)

    /** Dzieli „Liga: A - B” na strony; prefiks rozgrywek przed ostatnim dwukropkiem lewej strony jest pomijany. */
    fun splitHeadToHead(text: String): Pair<String, String>? {
        val match = SEPARATOR.find(text) ?: return null
        val left = text.substring(0, match.range.first).substringAfterLast(':').trim()
        val right = text.substring(match.range.last + 1).trim()
        return if (left.isEmpty() || right.isEmpty()) null else left to right
    }

    fun isReplay(
        title: String,
        description: String?,
    ): Boolean {
        val words = Channel.normalizeName(title).split(' ').toSet()
        val inDescription = Channel.normalizeName(description.orEmpty()).split(' ').toSet()
        return words.any { it in REPLAY_WORDS } || inDescription.any { it in REPLAY_DESCRIPTION_WORDS }
    }

    /** Odwołanie albo przełożenie wpisane w tytuł lub opis audycji. */
    fun scheduleNote(
        title: String,
        description: String?,
    ): ScheduleNote? {
        val words = Channel.normalizeName(title + " " + description.orEmpty()).split(' ').toSet()
        return when {
            words.any { it in CANCELED_WORDS } -> ScheduleNote.CANCELED
            words.any { it in POSTPONED_WORDS } -> ScheduleNote.POSTPONED
            else -> null
        }
    }

    /** `true`/`false`, gdy kategoria EPG mówi, czy to sport; `null`, gdy kategorii brak. Tylko filtr, nie drużyna. */
    fun sportHint(category: String?): Boolean? {
        val norm = Channel.normalizeName(category.orEmpty())
        if (norm.isEmpty()) return null
        return SPORT_WORDS.any { it in norm }
    }

    private fun competition(normalized: String): String? {
        val padded = " $normalized "
        return COMPETITIONS.firstOrNull { (alias, _) -> " $alias " in padded }?.second
    }

    private val SEPARATOR = Regex("""\s+(?:-|–|—|vs\.?|v\.?|kontra)\s+""", RegexOption.IGNORE_CASE)
    private const val MAX_DESCRIPTION = 160
    private val REPLAY_WORDS =
        setOf("powtorka", "skrot", "skroty", "replay", "studio", "magazyn", "skrocony", "retransmisja", "highlights")
    private val REPLAY_DESCRIPTION_WORDS = setOf("powtorka", "retransmisja", "replay")
    private val CANCELED_WORDS = setOf("odwolany", "odwolane", "odwolana", "canceled", "cancelled")
    private val POSTPONED_WORDS = setOf("przelozony", "przelozone", "przelozona", "postponed")
    private val SPORT_WORDS =
        listOf("sport", "pilka", "mecz", "liga", "football", "soccer", "futbol", "tenis", "siatkow", "koszyk", "zuzel")
    private val COMPETITIONS =
        listOf(
            "liga mistrzow" to "uefa-cl",
            "champions league" to "uefa-cl",
            "liga europy" to "uefa-el",
            "europa league" to "uefa-el",
            "liga konferencji" to "uefa-ecl",
            "ekstraklasa" to "pl-ekstraklasa",
            "puchar polski" to "pl-cup",
            "premier league" to "en-pl",
            "la liga" to "es-laliga",
            "laliga" to "es-laliga",
            "bundesliga" to "de-bundesliga",
            "serie a" to "it-seriea",
            "ligue 1" to "fr-ligue1",
        )
}

enum class ScheduleNote { POSTPONED, CANCELED }
