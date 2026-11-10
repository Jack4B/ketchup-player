package pl.ketchupplayer.tv.core.data.personalization

import androidx.sqlite.db.SimpleSQLiteQuery
import pl.ketchupplayer.tv.core.database.PersonalizationDao
import pl.ketchupplayer.tv.core.database.SportDao
import pl.ketchupplayer.tv.core.database.TeamAliasEntity
import pl.ketchupplayer.tv.core.database.TeamEntity
import pl.ketchupplayer.tv.core.database.TeamFollowEntity
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.personalization.FollowState
import pl.ketchupplayer.tv.core.model.personalization.FollowSuggestion
import pl.ketchupplayer.tv.core.model.personalization.HOUSEHOLD_PROFILE
import pl.ketchupplayer.tv.core.model.personalization.SportCard
import pl.ketchupplayer.tv.core.model.personalization.Team
import pl.ketchupplayer.tv.core.model.personalization.TeamAlias
import pl.ketchupplayer.tv.core.policy.personalization.AliasIndex
import pl.ketchupplayer.tv.core.policy.personalization.BundledTeams
import pl.ketchupplayer.tv.core.policy.personalization.EpgSlot
import pl.ketchupplayer.tv.core.policy.personalization.FollowDecision
import pl.ketchupplayer.tv.core.policy.personalization.FollowSuggester
import pl.ketchupplayer.tv.core.policy.personalization.InventoryChannel
import pl.ketchupplayer.tv.core.policy.personalization.PersonalizationParams
import pl.ketchupplayer.tv.core.policy.personalization.SportEvents
import pl.ketchupplayer.tv.core.policy.personalization.TeamEvidence
import pl.ketchupplayer.tv.core.policy.personalization.TeamParse
import pl.ketchupplayer.tv.core.policy.personalization.TeamParser
import javax.inject.Inject
import javax.inject.Singleton

/** Drużyna z jej stanem w profilu (do ekranu ustawień). */
data class TeamChoice(
    val team: Team,
    val state: FollowState?,
)

/**
 * Tor sportowy: katalog drużyn (wbudowany słownik + dodane ręcznie), wybory użytkownika, wydarzenia z EPG
 * i propozycje obserwowania. Niczego nie obserwuje sam: stan FOLLOWED ustawia wyłącznie [follow].
 */
@Singleton
@Suppress("TooManyFunctions") // jedno miejsce dla całego toru sportowego: katalog, wybory, wydarzenia, sugestie
class SportStore
    @Inject
    constructor(
        private val sport: SportDao,
        private val personalization: PersonalizationDao,
    ) {
        private val p = PersonalizationParams.DEFAULT
        private val profile = HOUSEHOLD_PROFILE

        suspend fun teams(): List<Team> =
            BundledTeams.teams +
                sport.teams().map { Team(it.teamId, it.displayName, it.competitionKey, it.country, userDefined = true) }

        private suspend fun aliases(): List<TeamAlias> =
            BundledTeams.aliases +
                sport.aliases().map { TeamAlias(it.teamId, it.aliasNorm, it.weak, it.ambiguousGroup) }

        suspend fun aliasIndex(): AliasIndex = AliasIndex(aliases())

        suspend fun choices(): List<TeamChoice> {
            val states = sport.follows(profile).associate { it.teamId to parseState(it.state) }
            return teams().map { TeamChoice(it, states[it.id]) }
        }

        suspend fun followedIds(): Set<String> =
            sport
                .follows(profile)
                .filter { it.state == FollowState.FOLLOWED.name }
                .map { it.teamId }
                .toSet()

        /** Ręczne dodanie drużyny spoza słownika; od razu obserwowana, bo użytkownik sam ją wskazał. */
        suspend fun addUserTeam(
            name: String,
            extraAliases: List<String>,
            nowMs: Long,
        ): Team? {
            val clean = name.trim()
            if (Channel.normalizeName(clean).length < MIN_NAME) return null
            val id = BundledTeams.userTeamId(clean)
            sport.putTeam(TeamEntity(id, clean, Channel.normalizeName(clean), null, null, "USER", 1))
            sport.deleteAliases(id)
            sport.putAliases(
                BundledTeams.userAliases(id, clean, extraAliases).map {
                    TeamAliasEntity(it.teamId, it.aliasNorm, language = null, ambiguousGroup = null)
                },
            )
            follow(id, nowMs)
            return Team(id, clean, userDefined = true)
        }

        suspend fun removeUserTeam(teamId: String) {
            sport.deleteFollow(profile, teamId)
            sport.deleteTeam(teamId)
        }

        suspend fun follow(
            teamId: String,
            nowMs: Long,
        ) = setState(teamId, FollowState.FOLLOWED, nowMs)

        suspend fun reject(
            teamId: String,
            nowMs: Long,
        ) = setState(teamId, FollowState.REJECTED, nowMs)

        suspend fun unfollow(teamId: String) = sport.deleteFollow(profile, teamId)

        private suspend fun setState(
            teamId: String,
            state: FollowState,
            nowMs: Long,
        ) {
            val old = sport.follows(profile).firstOrNull { it.teamId == teamId }
            sport.putFollow(
                TeamFollowEntity(
                    profileId = profile,
                    teamId = teamId,
                    state = state.name,
                    evidenceCount = old?.evidenceCount ?: 0,
                    lastEvidenceMs = old?.lastEvidenceMs,
                    reasonSummary = old?.reasonSummary,
                    updatedAtMs = nowMs,
                ),
            )
        }

        /** Parsuje tytuł audycji zapisany przy sesji (patrz [TeamParser.evidenceTitle]). */
        suspend fun parseStored(title: String): TeamParse = TeamParser.parse(title, null, null, aliasIndex())

        /** Dowody z obejrzanych transmisji: sesje kanałów ocenione jako oglądanie, z rozpoznaną drużyną. */
        private suspend fun evidence(nowMs: Long): List<TeamEvidence> {
            val index = aliasIndex()
            return personalization
                .liveEvidence(profile, nowMs - p.followWindowMs)
                .filter { it.activeWatchMs >= p.followMinWatchMs && it.appliedStrength > 0.0 }
                .flatMap { s ->
                    val parse = TeamParser.parse(s.programmeTitleNorm.orEmpty(), null, null, index)
                    if (parse.ambiguous || parse.confidence < p.minTeamConfidence) {
                        emptyList()
                    } else {
                        val at = s.programmeStartMs ?: s.startedAtMs
                        parse.teams.map { team ->
                            TeamEvidence(team, at, s.appliedStrength, parse.teams.firstOrNull { it != team })
                        }
                    }
                }
        }

        /**
         * Najmocniejsza propozycja obserwowania albo null. Zapisuje stan SUGGESTED (z liczbą dat), ale nigdy
         * FOLLOWED: to wymaga potwierdzenia.
         */
        suspend fun suggestion(nowMs: Long): FollowSuggestion? {
            val follows = sport.follows(profile)
            val decisions = follows.associate { it.teamId to FollowDecision(parseState(it.state), it.updatedAtMs) }
            val proposal = FollowSuggester.propose(evidence(nowMs), decisions, nowMs, p).firstOrNull() ?: return null
            val known = teams().associateBy { it.id }
            val teams = proposal.teamIds.mapNotNull { known[it] }
            if (teams.isEmpty()) return null
            teams.forEach { team ->
                val old = follows.firstOrNull { it.teamId == team.id }
                // Odrzucona drużyna po karencji wraca jako pytanie; data decyzji zostaje do liczenia kolejnej karencji.
                sport.putFollow(
                    TeamFollowEntity(
                        profileId = profile,
                        teamId = team.id,
                        state = FollowState.SUGGESTED.name,
                        evidenceCount = proposal.datesUtcMs.size,
                        lastEvidenceMs = proposal.datesUtcMs.maxOrNull(),
                        reasonSummary = proposal.datesUtcMs.joinToString(","),
                        updatedAtMs = old?.updatedAtMs ?: nowMs,
                    ),
                )
            }
            return FollowSuggestion(teams, proposal.datesUtcMs)
        }

        /** Przelicza wydarzenia obserwowanych drużyn z aktualnego EPG i listy kanałów. */
        suspend fun reindexEvents(nowMs: Long) {
            val followed = followedIds()
            val previous = storedEvents()
            if (followed.isEmpty()) {
                if (previous.isNotEmpty()) sport.replaceEvents(emptyList(), emptyList())
                return
            }
            val all = aliases()
            val slots = candidateSlots(all.filter { it.teamId in followed }, nowMs)
            val channels = inventory(slots.map { it.channelId }.distinct())
            val built = SportEvents.build(slots, followed, AliasIndex(all), channels, nowMs, p)
            val merged =
                SportEvents
                    .reconcile(previous, built, nowMs)
                    .filter { it.endUtcMs > nowMs - KEEP_FINISHED_MS }
            val providers = channelProviders(merged.flatMap { e -> e.availability.map { it.channelId } }.distinct())
            sport.replaceEvents(
                merged.map { it.toEntity() },
                merged.flatMap { e -> e.availability.map { it.toEntity(providers[it.channelId]) } },
            )
        }

        private suspend fun storedEvents() =
            sport.availability().groupBy { it.eventId }.let { byEvent ->
                sport.events().map { it.toModel(byEvent[it.eventId].orEmpty()) }
            }

        suspend fun cards(nowMs: Long): List<SportCard> {
            val events = storedEvents()
            val channels = inventory(events.flatMap { e -> e.availability.map { it.channelId } }.distinct())
            return SportEvents.cards(events, channels, nowMs, p)
        }

        /**
         * Audycje, których tytuł albo opis może dotyczyć obserwowanej drużyny. Wstępny filtr SQL (wzorzec z `_`
         * w miejscu liter, które w polskim mają znaki diakrytyczne) tylko zawęża zbiór; decyduje parser.
         */
        private suspend fun candidateSlots(
            aliases: List<TeamAlias>,
            nowMs: Long,
        ): List<EpgSlot> {
            val patterns = aliases.mapNotNull { likePattern(it.aliasNorm) }.distinct().take(MAX_PATTERNS)
            if (patterns.isEmpty()) return emptyList()
            val where = patterns.joinToString(" OR ") { "title LIKE ? OR description LIKE ?" }
            val args =
                buildList<Any> {
                    add(nowMs)
                    add(nowMs + p.sportHorizonMs)
                    patterns.forEach {
                        add(it)
                        add(it)
                    }
                }
            val sql =
                "SELECT channelId, startMs, endMs, title, substr(description, 1, 200) AS description, category " +
                    "FROM epg_programmes WHERE endMs > ? AND startMs < ? AND ($where) LIMIT $MAX_SLOTS"
            return personalization.epgSlots(SimpleSQLiteQuery(sql, args.toTypedArray())).map {
                EpgSlot(it.channelId, it.startMs, it.endMs, it.title, it.description, it.category)
            }
        }

        private suspend fun inventory(ids: List<String>): Map<String, InventoryChannel> =
            ids
                .chunked(SQL_CHUNK)
                .flatMap { personalization.inventoryChannels(it) }
                .associate { it.id to InventoryChannel(it.id, it.name, it.hidden) }

        private suspend fun channelProviders(ids: List<String>): Map<String, String> =
            ids
                .chunked(
                    SQL_CHUNK,
                ).flatMap { personalization.inventoryChannels(it) }
                .associate { it.id to it.providerId }

        suspend fun clear() = sport.clearSport()

        private fun parseState(value: String): FollowState =
            FollowState.entries.firstOrNull { it.name == value } ?: FollowState.SUGGESTED

        companion object {
            private const val MIN_NAME = 3
            private const val MAX_PATTERNS = 60
            private const val MAX_SLOTS = 4_000
            private const val SQL_CHUNK = 500
            private const val KEEP_FINISHED_MS = 2 * PersonalizationParams.DAY_MS
            private const val MIN_WORD = 4
            private const val DIACRITIC_PRONE = "acelnosz"

            /** Wzorzec LIKE z najdłuższego słowa aliasu; null, gdy alias jest za krótki, by cokolwiek zawęzić. */
            internal fun likePattern(aliasNorm: String): String? {
                val word =
                    aliasNorm.split(' ').maxByOrNull { it.length }?.takeIf { it.length >= MIN_WORD } ?: return null
                return "%" + word.map { if (it in DIACRITIC_PRONE) '_' else it }.joinToString("") + "%"
            }
        }
    }
