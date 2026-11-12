package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.personalization.FeatureWeight
import kotlin.math.exp

/** Profil preferencji: zanik wykładniczy w czasie i kurczenie przy małej liczbie obserwacji (punkt 8.3 konspektu). */
object PreferenceMath {
    fun decayFactor(
        fromMs: Long,
        toMs: Long,
        p: PersonalizationParams,
    ): Double {
        val ageDays = (toMs - fromMs).coerceAtLeast(0) / PersonalizationParams.DAY_MS.toDouble()
        return exp(-p.lambdaDay * ageDays)
    }

    /**
     * Dolicza wkład [x] (może być ujemny dla jawnego „nie lubię”). [countEvidence] = false dla dopłaty do tej samej,
     * wznowionej sesji: waga rośnie o różnicę, ale to nadal jedna obserwacja.
     */
    fun update(
        prev: FeatureWeight,
        x: Double,
        nowMs: Long,
        p: PersonalizationParams,
        sourceConfidence: Double,
        countEvidence: Boolean = true,
    ): FeatureWeight {
        if (!x.isFinite()) return prev
        val decay = if (prev.lastUpdatedMs > 0) decayFactor(prev.lastUpdatedMs, nowMs, p) else 1.0
        val weight = prev.decayedWeight * decay + x
        val evidence = prev.evidence * decay + if (countEvidence) 1.0 else 0.0
        val trust = shrink(evidence, p)
        return prev.copy(
            rawWeight = prev.rawWeight + x,
            decayedWeight = weight,
            evidence = evidence,
            confidence = (trust * sourceConfidence.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0),
            lastUpdatedMs = nowMs,
        )
    }

    /** Wynik cechy na chwilę [nowMs]: waga po zaniku, skurczona do zera przy małej liczbie obserwacji. */
    fun score(
        w: FeatureWeight,
        nowMs: Long,
        p: PersonalizationParams,
    ): Double {
        val decay = decayFactor(w.lastUpdatedMs, nowMs, p)
        val value = shrink(w.evidence * decay, p) * w.decayedWeight * decay
        return if (value.isFinite()) value else 0.0
    }

    /** Korekta o częstość cechy w katalogu: gatunek obecny wszędzie nie wygrywa samą powszechnością. */
    fun preference(
        score: Double,
        baseShare: Double,
        p: PersonalizationParams,
    ): Double = score / maxOf(baseShare, p.baseFloor)

    private fun shrink(
        evidence: Double,
        p: PersonalizationParams,
    ): Double {
        val denominator = evidence + p.shrinkK
        return if (denominator <= 0.0) 0.0 else (evidence / denominator).coerceIn(0.0, 1.0)
    }
}
