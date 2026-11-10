package pl.ketchupplayer.tv.core.data.personalization

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.ExplicitFeedbackEntity
import pl.ketchupplayer.tv.core.database.HomeSnapshotEntity
import pl.ketchupplayer.tv.core.database.PersonalizationDao
import pl.ketchupplayer.tv.core.model.personalization.BecauseCard
import pl.ketchupplayer.tv.core.model.personalization.ContentKind
import pl.ketchupplayer.tv.core.model.personalization.EndReason
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.FeatureWeight
import pl.ketchupplayer.tv.core.model.personalization.FeedbackKind
import pl.ketchupplayer.tv.core.model.personalization.HOUSEHOLD_PROFILE
import pl.ketchupplayer.tv.core.model.personalization.ItemFeature
import pl.ketchupplayer.tv.core.model.personalization.Provenance
import pl.ketchupplayer.tv.core.model.personalization.WatchSession
import pl.ketchupplayer.tv.core.policy.personalization.PersonalizationParams
import pl.ketchupplayer.tv.core.policy.personalization.ProfileUpdater
import pl.ketchupplayer.tv.core.policy.personalization.SessionMath
import pl.ketchupplayer.tv.core.policy.personalization.SessionStrength
import pl.ketchupplayer.tv.core.policy.personalization.VodCandidate
import pl.ketchupplayer.tv.core.policy.personalization.VodFeatures
import pl.ketchupplayer.tv.core.policy.personalization.VodRanker
import pl.ketchupplayer.tv.core.policy.personalization.WarsawTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silnik personalizacji: zamienia zamknięte sesje i jawne oceny na profil preferencji, a profil na gotowe pasy Home
 * (`home_snapshots`). Wszystko lokalnie i poza wątkiem UI; ekran tylko czyta wynik. Bez zgody nie robi nic.
 */
@Singleton
@Suppress("TooManyFunctions") // spina sesje, profil, oceny, pasy i konserwację w jednej kolejce (jeden Mutex)
class PersonalizationEngine
    @Inject
    constructor(
        private val dao: PersonalizationDao,
        private val settings: PersonalizationSettings,
        private val indexer: FeatureIndexer,
        private val sport: SportStore,
        private val clock: Clock,
    ) {
        private val p = PersonalizationParams.DEFAULT
        private val profile = HOUSEHOLD_PROFILE
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mutex = Mutex()
        private var pending: Job? = null

        /** Dolicza zamkniętą sesję do profilu. Wznowiona sesja dolicza tylko różnicę siły, nigdy całość drugi raz. */
        suspend fun onSessionClosed(session: WatchSession) {
            mutex.withLock { apply(session) }
            scheduleRefresh()
        }

        private suspend fun apply(session: WatchSession) {
            if (!settings.current().enabled || session.incognito) return
            val strength = SessionStrength.strength(session, p, seriesMsSameDay(session))
            val delta = strength - session.appliedStrength
            if (delta <= EPSILON) return
            val features = featuresFor(session)
            if (features.isNotEmpty()) {
                contribute(features, delta, countEvidence = session.appliedStrength <= 0.0)
            }
            dao.putSession(session.copy(appliedStrength = strength).toEntity())
        }

        private suspend fun seriesMsSameDay(session: WatchSession): Long {
            val series = session.seriesId ?: return 0
            if (session.kind != ContentKind.EPISODE) return 0
            val day = PersonalizationParams.DAY_MS
            val others = dao.seriesSessions(profile, series, session.startedAtMs - day, session.startedAtMs)
            return ProfileUpdater.seriesMsSameDay(session, others.map { it.toModel() }, p)
        }

        private suspend fun featuresFor(session: WatchSession): List<ItemFeature> =
            when (session.kind) {
                ContentKind.MOVIE -> indexer.featuresOf(session.itemId)
                ContentKind.EPISODE -> indexer.featuresOf(session.seriesId ?: session.itemId)
                ContentKind.LIVE -> liveFeatures(session)
            }

        /** Kanał na żywo: słaby ślad samego kanału i drużyn z jednoznacznie rozpoznanego tytułu audycji. */
        private suspend fun liveFeatures(session: WatchSession): List<ItemFeature> {
            val channel =
                ItemFeature(FeatureNamespace.CHANNEL, session.itemId, session.itemId, 1.0, Provenance.USER, 1.0)
            val title = session.programmeTitleNorm ?: return listOf(channel)
            val parse = sport.parseStored(title)
            if (parse.ambiguous || parse.confidence < p.minTeamConfidence) return listOf(channel)
            val names = sport.teams().associateBy { it.id }
            val share = 1.0 / parse.teams.size.coerceAtLeast(1)
            return listOf(channel) +
                parse.teams.map { id ->
                    val label = names[id]?.name ?: id
                    ItemFeature(FeatureNamespace.TEAM, id, label, share, Provenance.EPG_TITLE, parse.confidence)
                }
        }

        private suspend fun contribute(
            features: List<ItemFeature>,
            strength: Double,
            countEvidence: Boolean,
        ) {
            val current =
                dao.features(profile).associate {
                    VodFeatures.key(it.toModel().namespace, it.featureKey) to
                        it.toModel()
                }
            val changed = ProfileUpdater.contribute(current, features, strength, clock.nowMs(), p, countEvidence)
            if (changed.isNotEmpty()) dao.putFeatures(changed.values.map { it.toEntity(profile) })
        }

        /**
         * Jawna ocena pozycji VOD. LIKE dolicza stały wkład raz na zmianę stanu (cofnięcie go odejmuje); HIDE tylko
         * wyklucza pozycję z rekomendacji i nie zmienia wag gatunków.
         */
        suspend fun setFeedback(
            itemId: String,
            kind: FeedbackKind,
            on: Boolean,
        ) {
            if (!settings.current().enabled) return
            mutex.withLock {
                val had =
                    dao.feedback(profile).any {
                        it.targetType == VOD &&
                            it.targetId == itemId &&
                            it.feedback == kind.name
                    }
                if (had == on) return@withLock
                if (on) {
                    dao.putFeedback(ExplicitFeedbackEntity(profile, VOD, itemId, kind.name, clock.nowMs()))
                } else {
                    dao.deleteFeedback(profile, VOD, itemId, kind.name)
                }
                val sign = if (on) 1.0 else -1.0
                when (kind) {
                    FeedbackKind.LIKE ->
                        contribute(
                            indexer.featuresOf(itemId),
                            sign * ProfileUpdater.likeStrength(p),
                            countEvidence = on,
                        )
                    FeedbackKind.DISLIKE ->
                        contribute(
                            indexer.featuresOf(itemId),
                            sign * p.dislikeWeight,
                            countEvidence = on,
                        )
                    else -> Unit
                }
            }
            refreshNow()
        }

        /** Odświeża pasy z opóźnieniem (kolejne zdarzenia w tym oknie łączą się w jedno przeliczenie). */
        fun scheduleRefresh(delayMs: Long = REFRESH_DEBOUNCE_MS) {
            pending?.cancel()
            pending =
                scope.launch {
                    delay(delayMs)
                    refreshNow()
                }
        }

        /** Przelicza i zapisuje pasy Home. Błąd nie kasuje ostatniego dobrego stanu. */
        @Suppress("TooGenericExceptionCaught") // przeliczenie w tle nie może wywrócić aplikacji; zostaje stary pas
        suspend fun refreshNow() {
            mutex.withLock {
                try {
                    if (!settings.current().enabled) {
                        dao.clearSnapshots()
                        return@withLock
                    }
                    val now = clock.nowMs()
                    indexer.sync()
                    sport.reindexEvents(now)
                    val snapshot = SnapshotPayload.of(because(now), sport.cards(now), sport.suggestion(now))
                    dao.putSnapshot(HomeSnapshotEntity(profile, LANE, JSON.encodeToString(snapshot), now, now, now))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.javaClass.simpleName
                }
            }
        }

        /** Nazwa klasy ostatniego błędu przeliczenia (diagnostyka; bez treści, żeby nic nie wyciekło). */
        @Volatile
        var lastError: String? = null
            private set

        /** Pas „Bo oglądasz”. Zimny start (za mało dowodów) = pusty pas zamiast udawanych rekomendacji. */
        private suspend fun because(nowMs: Long): List<BecauseCard> {
            val likes = dao.feedback(profile).count { it.targetType == VOD && it.feedback == FeedbackKind.LIKE.name }
            if (dao.qualifyingVodSessions(profile) < p.coldStartSessions && likes == 0) return emptyList()
            val weights = dao.features(profile).map { it.toModel() }.filter { it.namespace in VOD_NAMESPACES }
            val indexed = dao.indexedItems()
            if (weights.isEmpty() || indexed == 0) return emptyList()
            val base =
                dao.featureCounts().associate {
                    "${it.namespace}:${it.featureKey}" to
                        it.items.toDouble() / indexed
                }
            val pref = ProfileUpdater.preferences(weights, base, nowMs, p)
            val top = topKeys(weights, pref)
            if (top.isEmpty()) return emptyList()
            val genreKeys = weights.filter { it.namespace == FeatureNamespace.GENRE }.map { it.key }
            val ids = (dao.candidateItems(top, CANDIDATES) + dao.itemsOutsideGenres(genreKeys, EXPLORE_POOL)).distinct()
            val features = ids.chunked(SQL_CHUNK).flatMap { dao.vodFeaturesOfItems(it) }.groupBy { it.itemId }
            val candidates = ids.map { id -> VodCandidate(id, features[id].orEmpty().map { it.toModel() }) }
            val inventory = ids.chunked(SQL_CHUNK).flatMap { dao.existingVodItems(it) }.toSet()
            val excluded = dao.excludedVodItems(profile).toSet()
            val seed = profile + WarsawTime.localDate(nowMs)
            return VodRanker.rank(inventory, candidates, pref, excluded, p, seed)
        }

        private fun topKeys(
            weights: List<FeatureWeight>,
            pref: Map<String, Double>,
        ): List<String> =
            weights
                .groupBy { it.namespace }
                .flatMap { (_, list) ->
                    list
                        .map { VodFeatures.key(it.namespace, it.key) }
                        .filter { (pref[it] ?: 0.0) > 0.0 }
                        .sortedByDescending { pref[it] }
                        .take(TOP_PER_NAMESPACE)
                }

        /**
         * Porządki po starcie aplikacji: sesje, które zostały otwarte po śmierci procesu, są zamykane z ostatnim
         * zapisanym czasem (nic nie jest doliczane z pozycji), a stare dane znikają zgodnie z retencją.
         */
        suspend fun maintain() {
            val now = clock.nowMs()
            val orphans = mutex.withLock { dao.openSessions().map { it.toModel() } }
            orphans.forEach { open ->
                val closed = SessionMath.close(open, EndReason.PROCESS_DEATH, open.lastEventAtMs, p)
                dao.putSession(closed.toEntity())
                mutex.withLock { apply(closed) }
            }
            val prefs = settings.current()
            mutex.withLock {
                dao.deleteSessionsBefore(now - prefs.retentionDays * PersonalizationParams.DAY_MS)
                dao.trimSessions(p.maxSessions)
                FeatureNamespace.entries.forEach { dao.pruneFeatures(profile, it.name, p.maxFeaturesPerNamespace) }
            }
            if (prefs.enabled) refreshNow()
        }

        /** Zatrzymuje zaplanowane przeliczenie (przed czyszczeniem danych). */
        fun cancelPending() {
            pending?.cancel()
            pending = null
        }

        /** Wykonuje [block] w tej samej kolejce co przeliczenia (czyszczenie nie ściga się z zapisem pasa). */
        suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }

        internal companion object {
            const val LANE = "HOME"
            const val VOD = "VOD"
            const val REFRESH_DEBOUNCE_MS = 5_000L
            private const val EPSILON = 1e-9
            private const val TOP_PER_NAMESPACE = 20
            private const val CANDIDATES = 600
            private const val EXPLORE_POOL = 40
            private const val SQL_CHUNK = 500
            private val VOD_NAMESPACES = setOf(FeatureNamespace.GENRE, FeatureNamespace.DIRECTOR, FeatureNamespace.CAST)
            val JSON = Json { ignoreUnknownKeys = true }
        }
    }
