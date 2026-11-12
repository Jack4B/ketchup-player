package pl.ketchupplayer.tv.core.policy.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.personalization.ContentKind
import pl.ketchupplayer.tv.core.model.personalization.EndReason
import pl.ketchupplayer.tv.core.model.personalization.EvidenceClass
import pl.ketchupplayer.tv.core.model.personalization.PlaybackSample
import pl.ketchupplayer.tv.core.model.personalization.SessionStatus
import pl.ketchupplayer.tv.core.model.personalization.StartReason
import pl.ketchupplayer.tv.core.model.personalization.WatchSession

/** Co jest oglądaniem: tylko czas faktycznego grania. Dane syntetyczne. */
class SessionMathTest {
    private val p = PersonalizationParams.DEFAULT

    private fun session(
        kind: ContentKind = ContentKind.MOVIE,
        start: StartReason = StartReason.USER,
        incognito: Boolean = false,
    ) = WatchSession(
        id = "s1",
        profileId = "household",
        providerId = "p1",
        itemId = "film-a",
        kind = kind,
        startedAtMs = T0,
        lastEventAtMs = T0,
        startReason = start,
        dedupKey = "k",
        incognito = incognito,
    )

    private enum class St { PLAY, PAUSE, BUFFER, ERROR }

    private fun sample(
        atMs: Long,
        st: St,
        position: Long = 0,
        duration: Long? = null,
    ) = PlaybackSample(
        wallClockMs = atMs,
        playing = st == St.PLAY,
        paused = st == St.PAUSE,
        buffering = st == St.BUFFER,
        positionMs = position,
        durationMs = duration,
        error = st == St.ERROR,
    )

    /** Odtwarza [minutes] minut ciągłego grania próbkami co 30 s (pozycja idzie z zegarem). */
    private fun play(
        s: WatchSession,
        fromMs: Long,
        minutes: Int,
        fromPosition: Long = 0,
        duration: Long? = null,
    ): WatchSession {
        var out = s
        var prev = sample(fromMs, St.PLAY, fromPosition, duration)
        repeat(minutes * 2) { i ->
            val t = fromMs + (i + 1) * HALF_MIN
            val cur = sample(t, St.PLAY, fromPosition + (i + 1) * HALF_MIN, duration)
            out = SessionMath.accumulate(out, prev, cur, p)
            prev = cur
        }
        return out
    }

    @Test
    fun graniePauzaIBuforSaLiczoneOsobno() {
        var s = session()
        val a = sample(T0, St.BUFFER)
        val b = sample(T0 + 4_000, St.PLAY)
        val c = sample(T0 + 64_000, St.PAUSE, position = 60_000)
        val d = sample(T0 + 94_000, St.PLAY, position = 60_000)
        val e = sample(T0 + 104_000, St.BUFFER, position = 70_000)
        val f = sample(T0 + 110_000, St.PLAY, position = 70_000)
        listOf(a to b, b to c, c to d, d to e, e to f).forEach { (x, y) -> s = SessionMath.accumulate(s, x, y, p) }
        assertEquals("gra: 60 s + 10 s", 70_000, s.activeWatchMs)
        assertEquals(30_000, s.pauseMs)
        assertEquals("pierwsze buforowanie po starcie nie jest zacięciem", 6_000, s.bufferMs)
        assertEquals(1, s.rebufferCount)
        assertEquals(110_000, s.wallWatchMs)
        assertEquals(0, s.seekCount)
    }

    @Test
    fun skokNaNapisyKoncoweNieJestUkonczeniem() {
        var s = play(session(), T0, minutes = 2, duration = FILM)
        val before = sample(T0 + 120_000, St.PLAY, 120_000, FILM)
        val after = sample(T0 + 121_000, St.PLAY, FILM - 30_000, FILM)
        s = SessionMath.accumulate(s, before, after, p)
        val end = sample(T0 + 151_000, St.PLAY, FILM, FILM)
        s = SessionMath.accumulate(s, after, end, p)
        val closed = SessionMath.close(s, EndReason.COMPLETED, T0 + 151_000, p)
        assertEquals(1, closed.seekCount)
        assertEquals("pozycja doszła do końca", FILM, closed.maxPositionMs)
        assertEquals("ale grało tylko 2 min 31 s", 151_000, closed.activeWatchMs)
        assertTrue(SessionMath.completion(closed)!! < 0.05)
        assertEquals("film poniżej 3 minut grania nie jest dowodem", 0.0, SessionStrength.strength(closed, p), 0.0)
    }

    @Test
    fun krotkieWyjscieZFilmuJestNeutralne() {
        val s = play(session(), T0, minutes = 1)
        val closed = SessionMath.close(s, EndReason.USER_STOP, T0 + 60_000, p)
        assertEquals(EvidenceClass.NEUTRAL, closed.evidence)
        assertEquals(0.0, SessionStrength.strength(closed, p), 0.0)
    }

    @Test
    fun przelaczanieKanalowJestNeutralne() {
        var s = session(ContentKind.LIVE)
        s = SessionMath.accumulate(s, sample(T0, St.PLAY), sample(T0 + 30_000, St.PLAY), p)
        val closed = SessionMath.close(s, EndReason.USER_STOP, T0 + 30_000, p)
        assertEquals(EndReason.SURF, closed.endReason)
        assertEquals(EvidenceClass.NEUTRAL, closed.evidence)
        assertFalse(SessionStrength.qualifies(closed, p))
    }

    @Test
    fun podgladNigdyNieJestDowodemNawetDlugi() {
        val short =
            SessionMath.close(
                play(session(ContentKind.LIVE, StartReason.AUTOPLAY_PREVIEW), T0, 0),
                EndReason.USER_STOP,
                T0,
                p,
            )
        assertEquals(EndReason.PREVIEW, short.endReason)
        val long = play(session(ContentKind.LIVE, StartReason.AUTOPLAY_PREVIEW), T0, minutes = 30)
        val closed = SessionMath.close(long, EndReason.USER_STOP, T0 + 30 * MIN, p)
        assertEquals(EvidenceClass.NEUTRAL, closed.evidence)
        assertEquals(0.0, SessionStrength.strength(closed, p), 0.0)
    }

    @Test
    fun bladOdtwarzaniaToNieznanaAwariaBezWplywu() {
        var s = session()
        s = SessionMath.accumulate(s, sample(T0, St.BUFFER), sample(T0 + 3_000, St.ERROR), p)
        val closed = SessionMath.close(s, EndReason.ERROR, T0 + 3_000, p)
        assertEquals(EvidenceClass.UNKNOWN_FAILURE, closed.evidence)
        assertEquals(0, closed.activeWatchMs)
        assertEquals(0.0, SessionStrength.strength(closed, p), 0.0)
    }

    @Test
    fun dlugiSeansJestPozytywnyASilaRosnieZUkonczeniem() {
        val full = SessionMath.close(play(session(), T0, 95, duration = FILM), EndReason.USER_STOP, T0 + 95 * MIN, p)
        val half = SessionMath.close(play(session(), T0, 50, duration = FILM), EndReason.USER_STOP, T0 + 50 * MIN, p)
        assertEquals(EvidenceClass.POSITIVE, full.evidence)
        assertEquals(0.95, SessionMath.completion(full)!!, 0.001)
        assertEquals(Math.log1p(95.0) * (0.5 + 0.5 * 0.95), SessionStrength.strength(full, p), 1e-9)
        assertTrue(SessionStrength.strength(full, p) > SessionStrength.strength(half, p))
    }

    @Test
    fun ukonczenieNieJestLiczoneBezDodatniegoCzasuTrwania() {
        listOf(0L, -5L).forEach { bad ->
            val s = SessionMath.close(play(session(), T0, 10, duration = bad), EndReason.USER_STOP, T0 + 10 * MIN, p)
            assertNull(SessionMath.completion(s))
            assertEquals(Math.log1p(10.0) * 0.7, SessionStrength.strength(s, p), 1e-9)
        }
    }

    @Test
    fun kanalBezEpgNieMaUkonczeniaAzRamamiAudycjiMa() {
        val bare = SessionMath.close(play(session(ContentKind.LIVE), T0, 40), EndReason.USER_STOP, T0 + 40 * MIN, p)
        assertNull(SessionMath.completion(bare))
        val framed =
            SessionMath.close(
                play(session(ContentKind.LIVE).copy(programmeStartMs = T0, programmeEndMs = T0 + 100 * MIN), T0, 40),
                EndReason.USER_STOP,
                T0 + 40 * MIN,
                p,
            )
        assertEquals(0.4, SessionMath.completion(framed)!!, 0.001)
    }

    @Test
    fun przerwaDluzszaNizDwieMinutyToUspienieNieOgladanie() {
        var s = play(session(), T0, minutes = 5)
        val before = sample(T0 + 5 * MIN, St.PLAY, 5 * MIN)
        val afterSleep = sample(T0 + 8 * 60 * MIN, St.PLAY, 5 * MIN + 1_000)
        s = SessionMath.accumulate(s, before, afterSleep, p)
        assertEquals(5 * MIN, s.activeWatchMs)
    }

    @Test
    fun wznowienieWOknieJestTaSamaSesjaAPoOknieNowa() {
        val closed = SessionMath.close(play(session(), T0, 20), EndReason.USER_STOP, T0 + 20 * MIN, p)
        assertTrue(SessionMath.canResume(closed, T0 + 45 * MIN, p))
        assertFalse(SessionMath.canResume(closed, T0 + 51 * MIN, p))
        val resumed = SessionMath.resume(closed, T0 + 45 * MIN)
        assertEquals(SessionStatus.OPEN, resumed.status)
        assertEquals("nic nie jest doliczane z pozycji", 20 * MIN, resumed.activeWatchMs)
        assertNull(resumed.endReason)
    }

    @Test
    fun kluczDeduplikacjiLaczyTeSamaAudycjeIRozdzielaInne() {
        val a = SessionMath.dedupKey("household", ContentKind.LIVE, "ch1", T0, T0 + 5 * MIN)
        val b = SessionMath.dedupKey("household", ContentKind.LIVE, "ch1", T0, T0 + 50 * MIN)
        val c = SessionMath.dedupKey("household", ContentKind.LIVE, "ch1", T0 + 120 * MIN, T0 + 125 * MIN)
        assertEquals(a, b)
        assertFalse(a == c)
    }

    @Test
    fun sufitOdcinkaISerialuNaDzien() {
        val episode =
            SessionMath.close(
                play(session(ContentKind.EPISODE), T0, 60, duration = 60 * MIN),
                EndReason.USER_STOP,
                T0 + 60 * MIN,
                p,
            )
        assertEquals(45 * MIN, SessionStrength.cappedMs(episode, p))
        assertEquals(
            "zostało 15 minut dziennego limitu serii",
            15 * MIN,
            SessionStrength.cappedMs(episode, p, 135 * MIN),
        )
        assertEquals(0, SessionStrength.cappedMs(episode, p, 150 * MIN))
        assertEquals(
            "po wyczerpaniu limitu kolejny odcinek nic nie dodaje",
            0.0,
            SessionStrength.strength(
                episode,
                p,
                150 * MIN,
            ),
            0.0,
        )
        assertTrue(SessionStrength.strength(episode, p) > 0.0)
    }

    @Test
    fun incognitoNigdyNieJestDowodem() {
        val s =
            SessionMath.close(
                play(session(incognito = true), T0, 95, duration = FILM),
                EndReason.USER_STOP,
                T0 + 95 * MIN,
                p,
            )
        assertEquals(EvidenceClass.NEUTRAL, s.evidence)
        assertEquals(0.0, SessionStrength.strength(s, p), 0.0)
    }

    private companion object {
        const val T0 = 1_780_000_000_000L
        const val MIN = 60_000L
        const val HALF_MIN = 30_000L
        const val FILM = 100 * MIN
    }
}
