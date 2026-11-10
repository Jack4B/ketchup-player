package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.personalization.ContentKind
import pl.ketchupplayer.tv.core.model.personalization.FeatureWeight
import pl.ketchupplayer.tv.core.model.personalization.ItemFeature
import pl.ketchupplayer.tv.core.model.personalization.WatchSession

/** Przenosi siłę sesji albo jawną ocenę na cechy pozycji i liczy z profilu preferencje do rankingu. */
object ProfileUpdater {
    /**
     * Zwraca zmienione wpisy profilu po doliczeniu [strength] do cech pozycji. Wkład cechy to siła razy udział cechy
     * w pozycji razy waga przestrzeni (gatunek 1.0, reżyser 1.1, obsada 0.6).
     */
    @Suppress("LongParameterList")
    fun contribute(
        profile: Map<String, FeatureWeight>,
        features: List<ItemFeature>,
        strength: Double,
        nowMs: Long,
        p: PersonalizationParams = PersonalizationParams.DEFAULT,
        countEvidence: Boolean = true,
    ): Map<String, FeatureWeight> {
        if (!strength.isFinite() || strength == 0.0) return emptyMap()
        return features.associate { f ->
            val key = VodFeatures.fullKey(f)
            val prev = profile[key] ?: FeatureWeight(f.namespace, f.key, f.label, provenance = f.provenance)
            val x = strength * f.weightOnItem * p.namespaceWeight(f.namespace)
            key to PreferenceMath.update(prev, x, nowMs, p, f.confidence, countEvidence).copy(label = f.label)
        }
    }

    /** Siła jawnego „lubię to”: jak [PersonalizationParams.likeWeight] minimalnych seansów filmu. */
    fun likeStrength(p: PersonalizationParams): Double = p.likeWeight * SessionStrength.timeValue(p.minMovieMs)

    /** Preferencje do rankingu: wynik cechy po zaniku i kurczeniu, skorygowany o jej częstość w katalogu. */
    fun preferences(
        profile: Collection<FeatureWeight>,
        baseShare: Map<String, Double>,
        nowMs: Long,
        p: PersonalizationParams = PersonalizationParams.DEFAULT,
    ): Map<String, Double> =
        profile.associate { w ->
            val key = VodFeatures.key(w.namespace, w.key)
            key to PreferenceMath.preference(PreferenceMath.score(w, nowMs, p), baseShare[key] ?: 0.0, p)
        }

    /** Udział pozycji katalogu mających daną cechę (0..1). */
    fun baseShare(items: Collection<List<ItemFeature>>): Map<String, Double> {
        if (items.isEmpty()) return emptyMap()
        val counts = HashMap<String, Int>()
        items.forEach { features ->
            features.map(VodFeatures::fullKey).distinct().forEach { counts.merge(it, 1, Int::plus) }
        }
        return counts.mapValues { it.value.toDouble() / items.size }
    }

    /**
     * Ile czasu tej samej serii zaliczono już tego dnia (w Warszawie) w innych sesjach: do sufitu dziennego, żeby
     * maraton jednego serialu nie zdominował profilu.
     */
    fun seriesMsSameDay(
        session: WatchSession,
        others: List<WatchSession>,
        p: PersonalizationParams = PersonalizationParams.DEFAULT,
    ): Long {
        val series = session.seriesId ?: return 0
        if (session.kind != ContentKind.EPISODE) return 0
        return others
            .filter { it.id != session.id && it.seriesId == series && it.startedAtMs < session.startedAtMs }
            .filter { WarsawTime.sameDay(it.startedAtMs, session.startedAtMs) && SessionStrength.qualifies(it, p) }
            .sumOf { minOf(it.activeWatchMs, p.episodeCapMs) }
            .coerceAtMost(p.seriesCapDayMs)
    }
}
