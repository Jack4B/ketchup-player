package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.personalization.ContentKind
import pl.ketchupplayer.tv.core.model.personalization.EvidenceClass
import pl.ketchupplayer.tv.core.model.personalization.SessionStatus
import pl.ketchupplayer.tv.core.model.personalization.StartReason
import pl.ketchupplayer.tv.core.model.personalization.WatchSession
import kotlin.math.ln1p

/** Siła zamkniętej sesji jako dowodu preferencji (punkt 8.1 konspektu). */
object SessionStrength {
    /** Czas sesji po sufitach odcinka i serialu na dzień ([seriesMsToday] = wkład innych sesji tej serii tego dnia). */
    fun cappedMs(
        s: WatchSession,
        p: PersonalizationParams,
        seriesMsToday: Long = 0,
    ): Long {
        val ms = s.activeWatchMs.coerceAtLeast(0)
        if (s.kind != ContentKind.EPISODE) return ms
        val room = (p.seriesCapDayMs - seriesMsToday.coerceAtLeast(0)).coerceAtLeast(0)
        return minOf(ms, p.episodeCapMs, room)
    }

    fun minQualifyingMs(
        kind: ContentKind,
        p: PersonalizationParams,
    ): Long =
        when (kind) {
            ContentKind.MOVIE -> p.minMovieMs
            ContentKind.EPISODE -> p.minEpisodeMs
            ContentKind.LIVE -> p.surfMs
        }

    /** Czy sesja jest dowodem preferencji (pozytywna, dość długa, nie podgląd, nie incognito). */
    fun qualifies(
        s: WatchSession,
        p: PersonalizationParams,
    ): Boolean =
        s.status == SessionStatus.CLOSED &&
            s.evidence == EvidenceClass.POSITIVE &&
            !s.incognito &&
            s.startReason != StartReason.AUTOPLAY_PREVIEW &&
            s.activeWatchMs >= minQualifyingMs(s.kind, p)

    /** Siła sesji: logarytm minut (tłumi maratony) razy ukończenie; 0 dla sesji, które nie są dowodem. */
    fun strength(
        s: WatchSession,
        p: PersonalizationParams,
        seriesMsToday: Long = 0,
    ): Double {
        if (!qualifies(s, p)) return 0.0
        val ms = cappedMs(s, p, seriesMsToday)
        if (ms < minQualifyingMs(s.kind, p)) return 0.0
        val time = timeValue(ms)
        val duration = SessionMath.knownDurationMs(s)
        val completion = duration?.let { (ms.toDouble() / it.toDouble()).coerceIn(0.0, 1.0) }
        val strength = if (completion == null) time * UNKNOWN_COMPLETION else time * (HALF + HALF * completion)
        return strength.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    }

    /** `ln(1 + minuty)`; nigdy logarytm z zera ani z liczby ujemnej. */
    fun timeValue(ms: Long): Double = ln1p(ms.coerceAtLeast(0) / PersonalizationParams.MINUTE_MS.toDouble())

    private const val UNKNOWN_COMPLETION = 0.7
    private const val HALF = 0.5
}
