package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.personalization.ContentKind
import pl.ketchupplayer.tv.core.model.personalization.EndReason
import pl.ketchupplayer.tv.core.model.personalization.EvidenceClass
import pl.ketchupplayer.tv.core.model.personalization.PlaybackSample
import pl.ketchupplayer.tv.core.model.personalization.SessionStatus
import pl.ketchupplayer.tv.core.model.personalization.StartReason
import pl.ketchupplayer.tv.core.model.personalization.WatchSession
import kotlin.math.abs

/**
 * Sesja oglądania z próbek stanu odtwarzacza. Liczy się wyłącznie czas, gdy odtwarzacz naprawdę grał: pauza,
 * buforowanie, błąd i przewinięcie nie są oglądaniem, a sama pozycja (np. po skoku na napisy końcowe) nie jest dowodem.
 */
object SessionMath {
    /** Klucz deduplikacji: ten sam film/odcinek albo ta sama audycja na kanale to jedna sesja, także po wznowieniu. */
    fun dedupKey(
        profileId: String,
        kind: ContentKind,
        itemId: String,
        programmeStartMs: Long?,
        startedAtMs: Long,
    ): String {
        val bucket =
            when (kind) {
                ContentKind.LIVE -> programmeStartMs ?: (startedAtMs - startedAtMs % LIVE_BUCKET_MS)
                else -> startedAtMs
            }
        return listOf(profileId, kind.name, itemId, bucket.toString()).joinToString("|")
    }

    /** Czy nowy start tej samej pozycji ma kontynuować [existing], zamiast liczyć się drugi raz. */
    fun canResume(
        existing: WatchSession,
        nowMs: Long,
        p: PersonalizationParams,
    ): Boolean = nowMs - existing.lastEventAtMs in 0..p.sessionGapMs

    /** Otwiera na nowo zamkniętą sesję; dotychczasowy aktywny czas zostaje, nic nie jest doliczane z pozycji. */
    fun resume(
        existing: WatchSession,
        nowMs: Long,
    ): WatchSession =
        existing.copy(
            status = SessionStatus.OPEN,
            endedAtMs = null,
            endReason = null,
            lastEventAtMs = nowMs,
        )

    /** Dolicza odcinek czasu między próbkami [prev] i [cur]; o tym, co się działo, decyduje stan z [prev]. */
    fun accumulate(
        s: WatchSession,
        prev: PlaybackSample,
        cur: PlaybackSample,
        p: PersonalizationParams,
    ): WatchSession {
        val raw = cur.wallClockMs - prev.wallClockMs
        val dt = if (raw in 0..p.maxSampleGapMs) raw else 0L
        val started = s.activeWatchMs > 0
        val jumped = s.kind != ContentKind.LIVE && raw >= 0 && isSeek(prev, cur, dt, p)
        val rebuffer = started && prev.playing && cur.buffering && !jumped
        return s.copy(
            lastEventAtMs = maxOf(s.lastEventAtMs, cur.wallClockMs),
            activeWatchMs = s.activeWatchMs + if (prev.playing) dt else 0L,
            wallWatchMs = s.wallWatchMs + dt,
            pauseMs = s.pauseMs + if (prev.paused) dt else 0L,
            bufferMs = s.bufferMs + if (prev.buffering && started) dt else 0L,
            seekCount = s.seekCount + if (jumped) 1 else 0,
            rebufferCount = s.rebufferCount + if (rebuffer) 1 else 0,
            maxPositionMs = maxOf(s.maxPositionMs, cur.positionMs.coerceAtLeast(0)),
            durationMs = cur.durationMs?.takeIf { it > 0 } ?: s.durationMs,
        )
    }

    /** Zamyka sesję i ocenia ją: krótkie wejście, przełączanie kanałów, podgląd i awaria nie są dowodem. */
    fun close(
        s: WatchSession,
        requested: EndReason,
        atMs: Long,
        p: PersonalizationParams,
    ): WatchSession {
        val end = endReason(s, requested, p)
        val evidence = evidence(s, end, p)
        return s.copy(
            status = SessionStatus.CLOSED,
            endedAtMs = maxOf(atMs, s.startedAtMs),
            lastEventAtMs = maxOf(s.lastEventAtMs, atMs),
            endReason = end,
            evidence = evidence,
        )
    }

    /** Pozycja przeskoczyła inaczej, niż wynika z upływu czasu grania: przewinięcie. */
    private fun isSeek(
        prev: PlaybackSample,
        cur: PlaybackSample,
        dt: Long,
        p: PersonalizationParams,
    ): Boolean {
        val expected = if (prev.playing) dt else 0L
        return abs((cur.positionMs - prev.positionMs) - expected) > p.seekJumpMs
    }

    private fun endReason(
        s: WatchSession,
        requested: EndReason,
        p: PersonalizationParams,
    ): EndReason =
        when {
            s.startReason == StartReason.AUTOPLAY_PREVIEW && s.activeWatchMs < p.previewPromoteMs -> EndReason.PREVIEW
            requested == EndReason.ERROR -> EndReason.ERROR
            s.kind == ContentKind.LIVE && s.activeWatchMs < p.surfMs -> EndReason.SURF
            else -> requested
        }

    private fun evidence(
        s: WatchSession,
        end: EndReason,
        p: PersonalizationParams,
    ): EvidenceClass {
        val neutralEnd = end == EndReason.SURF || end == EndReason.PREVIEW
        val tooShort = s.kind != ContentKind.LIVE && s.activeWatchMs < p.shortExitMs
        return when {
            end == EndReason.ERROR -> EvidenceClass.UNKNOWN_FAILURE
            s.incognito || neutralEnd || s.startReason == StartReason.AUTOPLAY_PREVIEW -> EvidenceClass.NEUTRAL
            tooShort || s.activeWatchMs <= 0L -> EvidenceClass.NEUTRAL
            else -> EvidenceClass.POSITIVE
        }
    }

    /** Czas trwania do oceny ukończenia: film/odcinek z odtwarzacza, kanał z ram audycji EPG; brak danych = null. */
    fun knownDurationMs(s: WatchSession): Long? =
        when (s.kind) {
            ContentKind.LIVE -> {
                val start = s.programmeStartMs
                val end = s.programmeEndMs
                if (start != null && end != null && end > start) end - start else null
            }
            else -> s.durationMs?.takeIf { it > 0 }
        }

    /** Ukończenie z aktywnego czasu, nie z pozycji. Null, gdy czas trwania jest nieznany lub niedodatni. */
    fun completion(s: WatchSession): Double? {
        val duration = knownDurationMs(s) ?: return null
        return (s.activeWatchMs.coerceAtLeast(0).toDouble() / duration.toDouble()).coerceIn(0.0, 1.0)
    }

    private const val LIVE_BUCKET_MS = 2 * PersonalizationParams.HOUR_MS
}
