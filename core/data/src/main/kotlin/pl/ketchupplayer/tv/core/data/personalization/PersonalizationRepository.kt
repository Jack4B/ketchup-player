package pl.ketchupplayer.tv.core.data.personalization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.PersonalizationDao
import pl.ketchupplayer.tv.core.database.SportDao
import pl.ketchupplayer.tv.core.model.personalization.FeedbackKind
import pl.ketchupplayer.tv.core.model.personalization.FollowState
import pl.ketchupplayer.tv.core.model.personalization.HOUSEHOLD_PROFILE
import pl.ketchupplayer.tv.core.model.personalization.HomeSnapshot
import pl.ketchupplayer.tv.core.model.personalization.Team
import javax.inject.Inject
import javax.inject.Singleton

/** Liczby do ekranu diagnostyki: ile danych zebrała personalizacja. Bez tytułów i bez identyfikatorów. */
data class PersonalizationDiagnostics(
    val sessions: Int = 0,
    val positive: Int = 0,
    val neutral: Int = 0,
    val failures: Int = 0,
    val features: Int = 0,
    val confidentFeatures: Int = 0,
    val followedTeams: Int = 0,
    val snapshotBuiltAtMs: Long? = null,
    val lastError: String? = null,
)

/**
 * Wejście do personalizacji dla ekranów. Wszystko jest lokalne: żadna z tych operacji nie wysyła historii oglądania
 * poza urządzenie.
 */
@Suppress("TooManyFunctions") // fasada całej funkcji dla UI: zgoda, pasy, oceny, drużyny, eksport, czyszczenie
interface PersonalizationRepository {
    val prefs: Flow<PersonalizationPrefs>

    /** Gotowe pasy Home; pusty stan, gdy personalizacja jest wyłączona albo nie ma jeszcze dowodów. */
    val snapshot: Flow<HomeSnapshot>

    /** Identyfikatory pozycji oznaczonych „lubię to”. */
    val liked: Flow<Set<String>>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setIncognito(incognito: Boolean)

    suspend fun setRetentionDays(days: Int)

    suspend fun setLiked(
        itemId: String,
        liked: Boolean,
    )

    suspend fun hide(itemId: String)

    fun teamChoices(): Flow<List<TeamChoice>>

    suspend fun follow(teamId: String)

    suspend fun unfollow(teamId: String)

    suspend fun reject(teamIds: List<String>)

    suspend fun addTeam(
        name: String,
        aliases: List<String> = emptyList(),
    ): Team?

    suspend fun removeTeam(teamId: String)

    /** Kopia zebranych danych jako JSON: bez adresów list, loginów, haseł, tokenów i nagłówków. */
    suspend fun export(): String

    /** Kasuje zebrane dane personalizacji. Katalog, Moja lista i postęp oglądania zostają. */
    suspend fun clear()

    suspend fun diagnostics(): PersonalizationDiagnostics

    /** Przelicza pasy teraz (np. po imporcie EPG albo z ekranu ustawień). */
    suspend fun refresh()

    /** Porządki przy starcie aplikacji: osierocone sesje, retencja, świeże pasy. */
    suspend fun onAppStart()

    /** Wersja bez danych (podglądy i testy ekranów, które nie dotyczą personalizacji). */
    object Disabled : PersonalizationRepository {
        override val prefs: Flow<PersonalizationPrefs> = flowOf(PersonalizationPrefs())
        override val snapshot: Flow<HomeSnapshot> = flowOf(HomeSnapshot())
        override val liked: Flow<Set<String>> = flowOf(emptySet())

        override suspend fun setEnabled(enabled: Boolean) = Unit

        override suspend fun setIncognito(incognito: Boolean) = Unit

        override suspend fun setRetentionDays(days: Int) = Unit

        override suspend fun setLiked(
            itemId: String,
            liked: Boolean,
        ) = Unit

        override suspend fun hide(itemId: String) = Unit

        override fun teamChoices(): Flow<List<TeamChoice>> = flowOf(emptyList())

        override suspend fun follow(teamId: String) = Unit

        override suspend fun unfollow(teamId: String) = Unit

        override suspend fun reject(teamIds: List<String>) = Unit

        override suspend fun addTeam(
            name: String,
            aliases: List<String>,
        ): Team? = null

        override suspend fun removeTeam(teamId: String) = Unit

        override suspend fun export(): String = "{}"

        override suspend fun clear() = Unit

        override suspend fun diagnostics() = PersonalizationDiagnostics()

        override suspend fun refresh() = Unit

        override suspend fun onAppStart() = Unit
    }
}

@Singleton
@Suppress("TooManyFunctions")
class DefaultPersonalizationRepository
    @Inject
    constructor(
        private val settings: PersonalizationSettings,
        private val dao: PersonalizationDao,
        private val sportDao: SportDao,
        private val sport: SportStore,
        private val engine: PersonalizationEngine,
        private val recorder: SessionRecorder,
        private val clock: Clock,
    ) : PersonalizationRepository {
        private val profile = HOUSEHOLD_PROFILE

        override val prefs: Flow<PersonalizationPrefs> = settings.prefs

        override val snapshot: Flow<HomeSnapshot> =
            combine(settings.prefs, dao.observeSnapshots(profile)) { prefs, rows ->
                val row = rows.firstOrNull { it.lane == PersonalizationEngine.LANE }
                if (!prefs.enabled || row == null) {
                    HomeSnapshot()
                } else {
                    SnapshotPayload.decode(row.payloadJson, row.builtAtMs)
                }
            }.distinctUntilChanged()

        override val liked: Flow<Set<String>> =
            dao
                .observeFeedback(profile)
                .map { rows -> rows.filter { it.feedback == FeedbackKind.LIKE.name }.map { it.targetId }.toSet() }
                .distinctUntilChanged()

        override suspend fun setEnabled(enabled: Boolean) {
            settings.setEnabled(enabled)
            io { engine.refreshNow() }
        }

        override suspend fun setIncognito(incognito: Boolean) = settings.setIncognito(incognito)

        override suspend fun setRetentionDays(days: Int) {
            settings.setRetentionDays(days)
            io { engine.maintain() }
        }

        override suspend fun setLiked(
            itemId: String,
            liked: Boolean,
        ) = io { engine.setFeedback(itemId, FeedbackKind.LIKE, liked) }

        override suspend fun hide(itemId: String) = io { engine.setFeedback(itemId, FeedbackKind.HIDE, true) }

        override fun teamChoices(): Flow<List<TeamChoice>> =
            combine(sportDao.observeFollows(profile), sportDao.observeTeams()) { _, _ -> sport.choices() }

        override suspend fun follow(teamId: String) =
            io {
                sport.follow(teamId, clock.nowMs())
                engine.refreshNow()
            }

        override suspend fun unfollow(teamId: String) =
            io {
                sport.unfollow(teamId)
                engine.refreshNow()
            }

        override suspend fun reject(teamIds: List<String>) =
            io {
                teamIds.forEach { sport.reject(it, clock.nowMs()) }
                engine.refreshNow()
            }

        override suspend fun addTeam(
            name: String,
            aliases: List<String>,
        ): Team? =
            io {
                sport.addUserTeam(name, aliases, clock.nowMs()).also { engine.refreshNow() }
            }

        override suspend fun removeTeam(teamId: String) =
            io {
                sport.removeUserTeam(teamId)
                engine.refreshNow()
            }

        override suspend fun clear() =
            io {
                engine.cancelPending()
                recorder.invalidate()
                engine.exclusive {
                    dao.clearLearned()
                    sport.clear()
                }
            }

        override suspend fun refresh() = io { engine.refreshNow() }

        override suspend fun onAppStart() = io { engine.maintain() }

        override suspend fun diagnostics(): PersonalizationDiagnostics =
            io {
                val counts = dao.evidenceCounts(profile).associate { it.evidenceClass to it.sessions }
                val features = dao.features(profile)
                PersonalizationDiagnostics(
                    sessions = counts.values.sum(),
                    positive = counts["POSITIVE"] ?: 0,
                    neutral = counts["NEUTRAL"] ?: 0,
                    failures = counts["UNKNOWN_FAILURE"] ?: 0,
                    features = features.size,
                    confidentFeatures = features.count { it.confidence > CONFIDENT },
                    followedTeams = sportDao.follows(profile).count { it.state == FollowState.FOLLOWED.name },
                    snapshotBuiltAtMs = dao.snapshots(profile).maxOfOrNull { it.builtAtMs },
                    lastError = engine.lastError,
                )
            }

        override suspend fun export(): String =
            io {
                val prefs = settings.current()
                buildJsonObject {
                    put("format", "ketchup-personalization-export")
                    put("version", 1)
                    put("exportedAtMs", clock.nowMs())
                    put(
                        "settings",
                        buildJsonObject {
                            put("enabled", prefs.enabled)
                            put("incognito", prefs.incognito)
                            put("retentionDays", prefs.retentionDays)
                        },
                    )
                    put("sessions", sessionsJson())
                    put("preferences", preferencesJson())
                    put("feedback", feedbackJson())
                    put("teams", teamsJson())
                }.toString()
            }

        private suspend fun sessionsJson(): JsonArray =
            buildJsonArray {
                dao.recentSessions(profile, EXPORT_SESSIONS).forEach { s ->
                    add(
                        buildJsonObject {
                            put("itemId", s.itemId)
                            put("kind", s.contentKind)
                            put("seriesId", s.seriesId)
                            put("programmeStartMs", s.programmeStartMs)
                            put("programmeTitle", s.programmeTitleNorm)
                            put("startedAtMs", s.startedAtMs)
                            put("endedAtMs", s.endedAtMs)
                            put("activeWatchMs", s.activeWatchMs)
                            put("pauseMs", s.pauseMs)
                            put("bufferMs", s.bufferMs)
                            put("durationMs", s.durationMs)
                            put("startReason", s.startReason)
                            put("endReason", s.endReason)
                            put("evidence", s.evidenceClass)
                        },
                    )
                }
            }

        private suspend fun preferencesJson(): JsonArray =
            buildJsonArray {
                dao.features(profile).forEach { f ->
                    add(
                        buildJsonObject {
                            put("namespace", f.namespace)
                            put("key", f.featureKey)
                            put("label", f.displayLabel)
                            put("weight", f.decayedWeight)
                            put("evidence", f.evidence)
                            put("confidence", f.confidence)
                            put("updatedAtMs", f.lastUpdatedMs)
                            put("provenance", f.provenance)
                        },
                    )
                }
            }

        private suspend fun feedbackJson(): JsonArray =
            buildJsonArray {
                dao.feedback(profile).forEach { f ->
                    add(
                        buildJsonObject {
                            put("targetType", f.targetType)
                            put("targetId", f.targetId)
                            put("feedback", f.feedback)
                            put("createdAtMs", f.createdAtMs)
                        },
                    )
                }
            }

        private suspend fun teamsJson(): JsonObject {
            val names = sport.teams().associate { it.id to it.name }
            return buildJsonObject {
                put(
                    "choices",
                    buildJsonArray {
                        sportDao.follows(profile).forEach { f ->
                            add(
                                buildJsonObject {
                                    put("teamId", f.teamId)
                                    put("name", names[f.teamId])
                                    put("state", f.state)
                                    put("updatedAtMs", f.updatedAtMs)
                                },
                            )
                        }
                    },
                )
                put(
                    "added",
                    buildJsonArray {
                        sportDao.teams().forEach { t ->
                            add(
                                buildJsonObject {
                                    put("teamId", t.teamId)
                                    put("name", t.displayName)
                                },
                            )
                        }
                    },
                )
            }
        }

        private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

        private companion object {
            const val CONFIDENT = 0.5
            const val EXPORT_SESSIONS = 5_000
        }
    }
