package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.personalization.FollowState

/** Dowód dla drużyny: jedna obejrzana transmisja (już po ocenie sesji), z przeciwnikiem, jeśli był rozpoznany. */
data class TeamEvidence(
    val teamId: String,
    val programmeStartMs: Long,
    val strength: Double,
    val opponentId: String? = null,
)

/** Decyzja użytkownika o drużynie i chwila jej podjęcia. */
data class FollowDecision(
    val state: FollowState,
    val updatedAtMs: Long,
)

/** Propozycja obserwowania: jedna drużyna (wyraźna przewaga) albo para oglądana zawsze razem. */
data class FollowProposal(
    val teamIds: List<String>,
    val datesUtcMs: List<Long>,
    val evidence: Double,
)

/**
 * Wniosek „może obserwować drużynę?”. Pojedynczy mecz niczego nie znaczy: potrzeba transmisji z kilku niezależnych
 * dni w oknie czasu i przewagi nad najczęstszym przeciwnikiem. Wynik to tylko pytanie; obserwowanie zaczyna dopiero
 * potwierdzenie użytkownika.
 */
object FollowSuggester {
    fun propose(
        history: List<TeamEvidence>,
        decisions: Map<String, FollowDecision>,
        nowMs: Long,
        p: PersonalizationParams = PersonalizationParams.DEFAULT,
    ): List<FollowProposal> {
        val recent = history.filter { it.strength > 0.0 && it.programmeStartMs in (nowMs - p.followWindowMs)..nowMs }
        val byTeam = recent.groupBy { it.teamId }
        val total = byTeam.mapValues { (_, list) -> list.sumOf { it.strength * SIDE_SHARE } }
        val dates = byTeam.mapValues { (_, list) -> independentDates(list.map { it.programmeStartMs }, p) }
        val open = byTeam.keys.filter { askable(decisions[it], nowMs, p) && dates.getValue(it).size >= p.followDates }
        val proposals = mutableListOf<FollowProposal>()
        val used = mutableSetOf<String>()
        for (team in open.sortedByDescending { total.getValue(it) }) {
            if (team in used) continue
            val rival = topOpponent(byTeam.getValue(team))
            val mine = total.getValue(team)
            val theirs = rival?.let { total[it] } ?: 0.0
            when {
                rival == null || mine >= p.asymmetry * theirs -> proposals += proposal(listOf(team), dates, total)
                // Zawsze ta sama para: nie zgadujemy strony, pytamy o obie (o ile druga też się kwalifikuje).
                rival in open && theirs < p.asymmetry * mine && mine < p.asymmetry * theirs -> {
                    proposals += proposal(listOf(team, rival), dates, total)
                    used += rival
                }
            }
            used += team
        }
        return proposals.filter { it.evidence.isFinite() }
    }

    /** Początki transmisji z różnych dni; kolejna liczy się, gdy jest co najmniej pół doby po poprzedniej. */
    fun independentDates(
        starts: List<Long>,
        p: PersonalizationParams,
    ): List<Long> {
        val out = mutableListOf<Long>()
        for (start in starts.distinct().sorted()) {
            val last = out.lastOrNull()
            if (last == null || start - last >= p.independentGapMs && !WarsawTime.sameDay(last, start)) out += start
        }
        return out
    }

    private fun askable(
        decision: FollowDecision?,
        nowMs: Long,
        p: PersonalizationParams,
    ): Boolean =
        when (decision?.state) {
            null, FollowState.SUGGESTED -> true
            FollowState.FOLLOWED -> false
            FollowState.REJECTED -> nowMs - decision.updatedAtMs >= p.rejectCooldownMs
        }

    private fun topOpponent(list: List<TeamEvidence>): String? =
        list
            .mapNotNull { it.opponentId }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.key

    private fun proposal(
        teams: List<String>,
        dates: Map<String, List<Long>>,
        total: Map<String, Double>,
    ) = FollowProposal(teams, dates.getValue(teams.first()), teams.sumOf { total.getValue(it) })

    private const val SIDE_SHARE = 0.5
}
