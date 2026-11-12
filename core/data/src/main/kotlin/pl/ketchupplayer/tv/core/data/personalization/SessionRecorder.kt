package pl.ketchupplayer.tv.core.data.personalization

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.ChannelDao
import pl.ketchupplayer.tv.core.database.EpgDao
import pl.ketchupplayer.tv.core.database.PersonalizationDao
import pl.ketchupplayer.tv.core.database.VodDao
import pl.ketchupplayer.tv.core.model.personalization.ContentKind
import pl.ketchupplayer.tv.core.model.personalization.EndReason
import pl.ketchupplayer.tv.core.model.personalization.HOUSEHOLD_PROFILE
import pl.ketchupplayer.tv.core.model.personalization.PlaybackSample
import pl.ketchupplayer.tv.core.model.personalization.SessionStatus
import pl.ketchupplayer.tv.core.model.personalization.StartReason
import pl.ketchupplayer.tv.core.model.personalization.WatchSession
import pl.ketchupplayer.tv.core.policy.personalization.PersonalizationParams
import pl.ketchupplayer.tv.core.policy.personalization.SessionMath
import pl.ketchupplayer.tv.core.policy.personalization.TeamParser
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Co jest odtwarzane: wyłącznie identyfikator z katalogu, nigdy adres strumienia. */
sealed interface PlaybackTarget {
    data class Live(
        val channelId: String,
    ) : PlaybackTarget

    data class Vod(
        val itemId: String,
        val resumed: Boolean,
    ) : PlaybackTarget
}

/** Stan odtwarzacza przekazywany obserwatorowi (bez adresu i bez nagłówków). */
data class PlaybackSnapshot(
    val playing: Boolean,
    val buffering: Boolean,
    val error: Boolean,
    val ended: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
)

/**
 * Obserwator odtwarzania. Tylko słucha: nie steruje odtwarzaczem, nie zmienia obsługi pilota, dotyku ani failoveru.
 * Wywołania są nieblokujące i bezpieczne z wątku głównego.
 */
interface PlaybackObserver {
    fun open(target: PlaybackTarget): PlaybackTracking

    object None : PlaybackObserver {
        override fun open(target: PlaybackTarget): PlaybackTracking = PlaybackTracking.None
    }
}

interface PlaybackTracking {
    fun onState(state: PlaybackSnapshot)

    fun close()

    object None : PlaybackTracking {
        override fun onState(state: PlaybackSnapshot) = Unit

        override fun close() = Unit
    }
}

/**
 * Zapisuje lokalne sesje oglądania z próbek stanu odtwarzacza. Bez zgody albo w trybie „nie ucz się” nie zapisuje
 * niczego. Stan trafia do bazy co kilkanaście sekund, więc śmierć procesu zostawia ostatni zapisany czas, a nie zero
 * i nie „całą pozycję”. Wszystko dzieje się poza wątkiem UI, w kolejce jednej sesji.
 */
@Singleton
class SessionRecorder
    @Inject
    constructor(
        private val dao: PersonalizationDao,
        private val channels: ChannelDao,
        private val vod: VodDao,
        private val epg: EpgDao,
        private val settings: PersonalizationSettings,
        private val engine: PersonalizationEngine,
        private val clock: Clock,
    ) : PlaybackObserver {
        private val p = PersonalizationParams.DEFAULT
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val jobs = ConcurrentHashMap.newKeySet<Job>()
        private val generation = AtomicInteger()

        override fun open(target: PlaybackTarget): PlaybackTracking = Tracking(target, generation.get())

        /** Czeka, aż wszystkie sesje w toku zostaną zapisane (testy i czyszczenie danych). */
        suspend fun awaitIdle() {
            while (true) {
                val running = jobs.toList()
                if (running.isEmpty()) return
                running.joinAll()
            }
        }

        /** Po wyczyszczeniu danych trwające sesje przestają zapisywać (nie odtworzą skasowanych wierszy). */
        fun invalidate() {
            generation.incrementAndGet()
        }

        private sealed interface Event {
            val atMs: Long

            data class State(
                override val atMs: Long,
                val state: PlaybackSnapshot,
            ) : Event

            data class Tick(
                override val atMs: Long,
            ) : Event

            data class Close(
                override val atMs: Long,
            ) : Event
        }

        private inner class Tracking(
            private val target: PlaybackTarget,
            private val born: Int,
        ) : PlaybackTracking {
            private val events = Channel<Event>(Channel.UNLIMITED)
            private var session: WatchSession? = null
            private var last: PlaybackSample? = null
            private var lastState: PlaybackSnapshot? = null
            private var checkpointAt = 0L

            init {
                // Czas otwarcia bierzemy od razu, w wątku wywołującego (nie dopiero, gdy ruszy korutyna).
                val openedAt = clock.nowMs()
                val job = scope.launch { run(openedAt) }
                jobs += job
                job.invokeOnCompletion { jobs -= job }
            }

            override fun onState(state: PlaybackSnapshot) {
                events.trySend(Event.State(clock.nowMs(), state))
            }

            override fun close() {
                events.trySend(Event.Close(clock.nowMs()))
                events.close()
            }

            private suspend fun run(startedAt: Long) {
                session = begin(target, startedAt)
                if (session == null) {
                    // Brak zgody, tryb „nie ucz się” albo pozycja spoza katalogu: nic nie zapisujemy.
                    for (ignored in events) Unit
                    return
                }
                checkpointAt = startedAt
                val ticker =
                    scope.launch {
                        while (isActive) {
                            delay(TICK_MS)
                            events.trySend(Event.Tick(clock.nowMs()))
                        }
                    }
                try {
                    for (event in events) handle(event)
                } finally {
                    ticker.cancel()
                }
            }

            private suspend fun handle(event: Event) {
                if (generation.get() != born) return
                val current = session ?: return
                val sample = sampleAt(event)
                val updated = last?.let { SessionMath.accumulate(current, it, sample, p) } ?: current
                last = sample
                session = updated
                when {
                    event is Event.Close -> finish(updated, event.atMs, endReason())
                    programmeOver(updated, event.atMs) -> rollOver(updated, event.atMs)
                    event.atMs - checkpointAt >= p.checkpointMs -> {
                        dao.putSession(updated.toEntity())
                        checkpointAt = event.atMs
                    }
                }
            }

            /** Próbka dla zdarzenia: nowy stan z odtwarzacza albo poprzedni stan przesunięty w czasie (tyknięcie). */
            private fun sampleAt(event: Event): PlaybackSample {
                if (event is Event.State) lastState = event.state
                val state = lastState
                val previous = last
                val position =
                    when {
                        event is Event.State -> event.state.positionMs
                        previous == null -> 0L
                        previous.playing -> previous.positionMs + (event.atMs - previous.wallClockMs).coerceAtLeast(0)
                        else -> previous.positionMs
                    }
                val playing = state?.playing == true
                val buffering = state?.buffering == true && !playing
                val error = state?.error == true
                return PlaybackSample(
                    wallClockMs = event.atMs,
                    playing = playing,
                    paused = state != null && !playing && !buffering && !error && !state.ended,
                    buffering = buffering,
                    positionMs = position,
                    durationMs = state?.durationMs,
                    error = error,
                )
            }

            private fun endReason(): EndReason =
                when {
                    lastState?.error == true -> EndReason.ERROR
                    lastState?.ended == true -> EndReason.COMPLETED
                    else -> EndReason.USER_STOP
                }

            private fun programmeOver(
                s: WatchSession,
                nowMs: Long,
            ): Boolean = s.kind == ContentKind.LIVE && s.programmeEndMs?.let { nowMs >= it } == true

            /** Na kanale zaczęła się następna audycja: zamykamy sesję poprzedniej i otwieramy nową. */
            private suspend fun rollOver(
                s: WatchSession,
                nowMs: Long,
            ) {
                finish(s, nowMs, EndReason.PROGRAMME_CHANGE)
                session = begin(target, nowMs)
                checkpointAt = nowMs
            }

            private suspend fun finish(
                s: WatchSession,
                nowMs: Long,
                reason: EndReason,
            ) {
                val closed = SessionMath.close(s, reason, nowMs, p)
                dao.putSession(closed.toEntity())
                session = null
                engine.onSessionClosed(closed)
            }
        }

        /** Otwiera sesję albo wznawia poprzednią tej samej pozycji; null, gdy nie wolno lub nie ma czego zapisać. */
        private suspend fun begin(
            target: PlaybackTarget,
            nowMs: Long,
        ): WatchSession? {
            if (!settings.current().learning) return null
            val fresh =
                when (target) {
                    is PlaybackTarget.Live -> liveSession(target.channelId, nowMs)
                    is PlaybackTarget.Vod -> vodSession(target, nowMs)
                } ?: return null
            val existing =
                dao.sessionByKey(HOUSEHOLD_PROFILE, fresh.dedupKey)?.toModel()
                    ?: dao
                        .lastSessionOf(
                            HOUSEHOLD_PROFILE,
                            fresh.itemId,
                        )?.toModel()
                        ?.takeIf { resumable(it, fresh, nowMs) }
            val session = existing?.let { SessionMath.resume(it, nowMs) } ?: fresh
            dao.putSession(session.toEntity())
            return session
        }

        private fun resumable(
            old: WatchSession,
            fresh: WatchSession,
            nowMs: Long,
        ): Boolean =
            old.kind == fresh.kind &&
                old.programmeStartMs == fresh.programmeStartMs &&
                (old.status == SessionStatus.OPEN || SessionMath.canResume(old, nowMs, p))

        private suspend fun liveSession(
            channelId: String,
            nowMs: Long,
        ): WatchSession? {
            val channel = channels.get(channelId) ?: return null
            val programme = epg.range(channelId, nowMs, nowMs + 1).firstOrNull()
            return newSession(
                providerId = channel.providerId,
                itemId = channelId,
                kind = ContentKind.LIVE,
                nowMs = nowMs,
            ).let {
                it.copy(
                    programmeStartMs = programme?.startMs,
                    programmeEndMs = programme?.endMs,
                    programmeTitleNorm = programme?.title?.let(TeamParser::evidenceTitle)?.take(MAX_TITLE),
                    dedupKey = SessionMath.dedupKey(it.profileId, it.kind, channelId, programme?.startMs, nowMs),
                )
            }
        }

        private suspend fun vodSession(
            target: PlaybackTarget.Vod,
            nowMs: Long,
        ): WatchSession? {
            val item = vod.get(target.itemId)
            val episode = if (item == null) vod.episode(target.itemId) else null
            val providerId = item?.providerId ?: episode?.let { vod.get(it.seriesId)?.providerId } ?: return null
            val kind = if (episode != null) ContentKind.EPISODE else ContentKind.MOVIE
            return newSession(providerId, target.itemId, kind, nowMs).copy(
                seriesId = episode?.seriesId,
                startReason = if (target.resumed) StartReason.RESUME else StartReason.USER,
            )
        }

        private fun newSession(
            providerId: String,
            itemId: String,
            kind: ContentKind,
            nowMs: Long,
        ) = WatchSession(
            id = UUID.randomUUID().toString(),
            profileId = HOUSEHOLD_PROFILE,
            providerId = providerId,
            itemId = itemId,
            kind = kind,
            startedAtMs = nowMs,
            lastEventAtMs = nowMs,
            dedupKey = SessionMath.dedupKey(HOUSEHOLD_PROFILE, kind, itemId, null, nowMs),
        )

        private companion object {
            const val TICK_MS = 5_000L
            const val MAX_TITLE = 160
        }
    }
