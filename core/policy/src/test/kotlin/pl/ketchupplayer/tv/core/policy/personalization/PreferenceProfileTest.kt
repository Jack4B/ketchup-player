package pl.ketchupplayer.tv.core.policy.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.personalization.ContentKind
import pl.ketchupplayer.tv.core.model.personalization.EndReason
import pl.ketchupplayer.tv.core.model.personalization.EvidenceClass
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.FeatureWeight
import pl.ketchupplayer.tv.core.model.personalization.ItemFeature
import pl.ketchupplayer.tv.core.model.personalization.SessionStatus
import pl.ketchupplayer.tv.core.model.personalization.WatchSession

/** Zanik, kurczenie i kontrfakty profilu (punkty 8.3 i 16.2 konspektu). Wszystkie tytuły i osoby są zmyślone. */
class PreferenceProfileTest {
    private val p = PersonalizationParams.DEFAULT
    private val day = PersonalizationParams.DAY_MS

    private fun weight() = FeatureWeight(FeatureNamespace.GENRE, "crime", "Kryminał")

    private fun closed(
        id: String,
        kind: ContentKind,
        activeMin: Int,
        durationMin: Int,
        startedAt: Long = T0,
        seriesId: String? = null,
    ) = WatchSession(
        id = id,
        profileId = "household",
        providerId = "p1",
        itemId = id,
        kind = kind,
        seriesId = seriesId,
        startedAtMs = startedAt,
        lastEventAtMs = startedAt + activeMin * MIN,
        endedAtMs = startedAt + activeMin * MIN,
        status = SessionStatus.CLOSED,
        activeWatchMs = activeMin * MIN,
        durationMs = durationMin * MIN,
        endReason = EndReason.USER_STOP,
        evidence = if (activeMin * MIN >= p.shortExitMs) EvidenceClass.POSITIVE else EvidenceClass.NEUTRAL,
        dedupKey = id,
    )

    @Test
    fun wagaPolowiejePoSzescdziesieciuDniach() {
        val w = PreferenceMath.update(weight(), 4.0, T0, p, sourceConfidence = 0.9)
        assertEquals(4.0, w.decayedWeight, 1e-9)
        assertEquals(1.0, w.evidence, 1e-9)
        val later = PreferenceMath.update(w, 0.0, T0 + 60 * day, p, 0.9, countEvidence = false)
        assertEquals(2.0, later.decayedWeight, 1e-9)
        assertEquals(0.5, later.evidence, 1e-9)
        assertEquals(
            "wynik bez dopisywania: zanik liczony w locie",
            PreferenceMath.score(w, T0 + 60 * day, p),
            PreferenceMath.score(
                later,
                T0 + 60 * day,
                p,
            ),
            1e-9,
        )
    }

    @Test
    fun kurczeniePrzyMalejLiczbieObserwacji() {
        val one = PreferenceMath.update(weight(), 4.0, T0, p, 0.9)
        assertEquals("1 obserwacja: 1/(1+8) wagi", 4.0 / 9.0, PreferenceMath.score(one, T0, p), 1e-9)
        assertEquals(0.9 / 9.0, one.confidence, 1e-9)
        var many = weight()
        repeat(8) { many = PreferenceMath.update(many, 4.0, T0, p, 0.9) }
        assertEquals("8 obserwacji: połowa wagi", 16.0, PreferenceMath.score(many, T0, p), 1e-9)
        assertEquals(0.45, many.confidence, 1e-9)
    }

    @Test
    fun niepoprawnyWkladNiePsujeWag() {
        val w = PreferenceMath.update(weight(), 4.0, T0, p, 0.9)
        assertEquals(w, PreferenceMath.update(w, Double.NaN, T0, p, 0.9))
        assertEquals(w, PreferenceMath.update(w, Double.POSITIVE_INFINITY, T0, p, 0.9))
        assertTrue(PreferenceMath.preference(1.0, 0.0, p).isFinite())
    }

    private fun genre(key: String) =
        ItemFeature(
            FeatureNamespace.GENRE,
            key,
            key,
            1.0,
            pl.ketchupplayer.tv.core.model.personalization.Provenance.PROVIDER,
            0.6,
        )

    private fun director(key: String) =
        ItemFeature(
            FeatureNamespace.DIRECTOR,
            key,
            key,
            1.0,
            pl.ketchupplayer.tv.core.model.personalization.Provenance.TMDB,
            0.9,
        )

    private fun cast(vararg keys: String) =
        keys.map {
            ItemFeature(
                FeatureNamespace.CAST,
                it,
                it,
                1.0 / Math.sqrt(keys.size.toDouble()),
                pl.ketchupplayer.tv.core.model.personalization.Provenance.PROVIDER,
                0.6,
            )
        }

    private fun apply(
        profile: MutableMap<String, FeatureWeight>,
        features: List<ItemFeature>,
        strength: Double,
        at: Long = T0,
        count: Boolean = true,
    ) {
        profile.putAll(ProfileUpdater.contribute(profile, features, strength, at, p, count))
    }

    /** Kontrfakt 1: dwa dokończone filmy reżysera R ważą więcej niż krótkie wejście w film popularnego gatunku G. */
    @Test
    fun dokonczoneFilmyRezyseraWygrywajaZKrotkimWejsciemWPopularnyGatunek() {
        val profile = mutableMapOf<String, FeatureWeight>()
        listOf("film-a", "film-b").forEach { id ->
            apply(
                profile,
                listOf(director("anna testowa"), genre("crime")),
                SessionStrength.strength(closed(id, ContentKind.MOVIE, 95, 100), p),
            )
        }
        apply(
            profile,
            listOf(genre("comedy")),
            SessionStrength.strength(closed("film-c", ContentKind.MOVIE, 1, 100), p),
        )
        // Komedia jest w 60% katalogu, reżyserka w 1%: korekta o powszechność niczego nie odwraca, bo G ma zero.
        val pref =
            ProfileUpdater.preferences(
                profile.values,
                mapOf(
                    "GENRE:comedy" to 0.6,
                    "DIRECTOR:anna testowa" to 0.01,
                ),
                T0,
                p,
            )
        assertTrue(pref.getValue("DIRECTOR:anna testowa") > 0.0)
        assertEquals("krótkie wejście nie zostawia śladu", null, pref["GENRE:comedy"])
    }

    /** Kontrfakt 2: maraton 8 odcinków jednego dnia nie przeważa obsadą dwóch dokończonych filmów reżysera. */
    @Test
    fun maratonSerialuNiePrzewazaDwochDokonczonychFilmow() {
        val profile = mutableMapOf<String, FeatureWeight>()
        val actors = cast("jan przykladowy", "ewa zmyslona", "piotr testowy", "olga probna", "adam fikcyjny")
        val episodes =
            (1..8).map {
                closed("e$it", ContentKind.EPISODE, 48, 50, startedAt = T0 + it * 50 * MIN, seriesId = "serial-port")
            }
        var total = 0.0
        episodes.forEach { e ->
            val used = ProfileUpdater.seriesMsSameDay(e, episodes, p)
            val strength = SessionStrength.strength(e, p, used)
            total += SessionStrength.cappedMs(e, p, used)
            apply(profile, actors, strength)
        }
        assertEquals("wkład serii tego dnia = sufit dzienny", p.seriesCapDayMs.toDouble(), total, 0.0)
        listOf("film-x", "film-y").forEach { id ->
            apply(
                profile,
                listOf(director("marek wymyslony")),
                SessionStrength.strength(closed(id, ContentKind.MOVIE, 95, 100), p),
            )
        }
        val pref = ProfileUpdater.preferences(profile.values, emptyMap(), T0, p)
        assertTrue(
            "reżyser ${pref["DIRECTOR:marek wymyslony"]} > aktor ${pref["CAST:jan przykladowy"]}",
            pref.getValue("DIRECTOR:marek wymyslony") > pref.getValue("CAST:jan przykladowy"),
        )
    }

    /** Kontrfakt 4: wznowiona sesja dolicza tylko różnicę siły i nie liczy się jako druga obserwacja. */
    @Test
    fun wznowionaSesjaNieDublujeWagi() {
        val features = listOf(genre("crime"))
        val once = mutableMapOf<String, FeatureWeight>()
        val full = SessionStrength.strength(closed("film-a", ContentKind.MOVIE, 95, 100), p)
        apply(once, features, full)

        val twice = mutableMapOf<String, FeatureWeight>()
        val firstPart = SessionStrength.strength(closed("film-a", ContentKind.MOVIE, 40, 100), p)
        apply(twice, features, firstPart)
        apply(twice, features, full - firstPart, count = false)

        assertEquals(once.getValue("GENRE:crime").decayedWeight, twice.getValue("GENRE:crime").decayedWeight, 1e-9)
        assertEquals(1.0, twice.getValue("GENRE:crime").evidence, 1e-9)
    }

    @Test
    fun polubienieWazyJakTrzyMinimalneSeanseAOdrzucenieJestStaleIUjemne() {
        val profile = mutableMapOf<String, FeatureWeight>()
        apply(profile, listOf(genre("crime")), ProfileUpdater.likeStrength(p))
        assertEquals(3.0 * Math.log1p(3.0), profile.getValue("GENRE:crime").decayedWeight, 1e-9)
        apply(profile, listOf(genre("horror")), p.dislikeWeight)
        assertEquals(-4.0, profile.getValue("GENRE:horror").decayedWeight, 1e-9)
    }

    private companion object {
        /** 28.05.2026 08:00 UTC (10:00 w Warszawie): maraton z testu mieści się w jednym dniu. */
        const val T0 = 1_779_955_200_000L
        const val MIN = 60_000L
    }
}
