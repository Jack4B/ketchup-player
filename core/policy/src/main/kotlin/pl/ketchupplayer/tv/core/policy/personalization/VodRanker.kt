package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.personalization.BecauseCard
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.ItemFeature
import pl.ketchupplayer.tv.core.model.personalization.WhyChip

/** Kandydat do pasa „Bo oglądasz”: pozycja katalogu z cechami z metadanych. */
data class VodCandidate(
    val itemId: String,
    val features: List<ItemFeature>,
)

/**
 * Ranking pasa „Bo oglądasz”. Kandydat musi być w katalogu użytkownika ([inventory]) i nie może być wykluczony.
 * Trafność to suma preferencji cech, które pozycja naprawdę ma; kolejność układa MMR, żeby rząd nie był jednym
 * gatunkiem. Uzasadnienia powstają tylko z cech, które weszły do wyniku.
 */
object VodRanker {
    data class Scored(
        val candidate: VodCandidate,
        val relevance: Double,
        val contributions: List<Pair<ItemFeature, Double>>,
    )

    fun score(
        c: VodCandidate,
        pref: Map<String, Double>,
        p: PersonalizationParams,
    ): Scored {
        val parts =
            c.features
                .filter { it.namespace in VOD_NAMESPACES && it.confidence >= p.minFeatureConfidence }
                .mapNotNull { f ->
                    val w = pref[VodFeatures.fullKey(f)] ?: return@mapNotNull null
                    val part = w * f.weightOnItem * f.confidence
                    if (part.isFinite() && part != 0.0) f to part else null
                }
        return Scored(c, parts.sumOf { it.second }, parts)
    }

    @Suppress("LongParameterList")
    fun rank(
        inventory: Set<String>,
        candidates: List<VodCandidate>,
        pref: Map<String, Double>,
        excluded: Set<String>,
        p: PersonalizationParams = PersonalizationParams.DEFAULT,
        exploreSeed: String = "",
    ): List<BecauseCard> {
        val allowed = candidates.filter { it.itemId in inventory && it.itemId !in excluded }.distinctBy { it.itemId }
        val scored =
            allowed
                .map { score(it, pref, p) }
                .filter { it.relevance.isFinite() && it.relevance > 0.0 }
                .sortedWith(compareByDescending<Scored> { it.relevance }.thenBy { it.candidate.itemId })
                .take(POOL)
        if (scored.isEmpty()) return emptyList()
        val exploreSlots = if (scored.size >= p.rowSize) (p.rowSize * p.exploreShare).toInt().coerceAtLeast(1) else 0
        val picked = diversify(scored, p.rowSize - exploreSlots, p)
        val cards = picked.map { BecauseCard(it.candidate.itemId, why(it, p)) }.toMutableList()
        if (exploreSlots > 0) {
            val taken = picked.map { it.candidate.itemId }.toSet()
            allowed
                .filter { it.itemId !in taken && it.features.isNotEmpty() && score(it, pref, p).relevance == 0.0 }
                .sortedBy { (exploreSeed + it.itemId).hashCode() }
                .take(exploreSlots)
                .forEach { cards += BecauseCard(it.itemId, emptyList(), explore = true) }
        }
        return cards
    }

    private fun diversify(
        scored: List<Scored>,
        size: Int,
        p: PersonalizationParams,
    ): List<Scored> {
        val top = scored.first().relevance
        val remaining = scored.toMutableList()
        val selected = mutableListOf<Scored>()
        while (selected.size < size && remaining.isNotEmpty()) {
            val next =
                remaining
                    .filter { fits(it, selected, p) }
                    .maxByOrNull { c ->
                        val similarity = selected.maxOfOrNull { similarity(c.candidate, it.candidate) } ?: 0.0
                        p.mmrLambda * (c.relevance / top) - (1 - p.mmrLambda) * similarity
                    } ?: break
            selected += next
            remaining -= next
        }
        return selected
    }

    private fun fits(
        c: Scored,
        selected: List<Scored>,
        p: PersonalizationParams,
    ): Boolean {
        val directors = keys(c.candidate, FeatureNamespace.DIRECTOR)
        val sameDirector = selected.count { s -> keys(s.candidate, FeatureNamespace.DIRECTOR).any { it in directors } }
        if (directors.isNotEmpty() && sameDirector >= p.maxSameDirector) return false
        val genre = topGenre(c.candidate) ?: return true
        return selected.count { topGenre(it.candidate) == genre } < p.maxSameTopGenre
    }

    /** Podobieństwo Jaccarda po kluczach gatunku i reżysera. */
    fun similarity(
        a: VodCandidate,
        b: VodCandidate,
    ): Double {
        val ka = similarityKeys(a)
        val kb = similarityKeys(b)
        val union = (ka + kb).size
        return if (union == 0) 0.0 else ka.intersect(kb).size.toDouble() / union
    }

    private fun similarityKeys(c: VodCandidate): Set<String> =
        c.features
            .filter { it.namespace == FeatureNamespace.GENRE || it.namespace == FeatureNamespace.DIRECTOR }
            .map { VodFeatures.fullKey(it) }
            .toSet()

    private fun keys(
        c: VodCandidate,
        ns: FeatureNamespace,
    ): Set<String> =
        c.features
            .filter { it.namespace == ns }
            .map { it.key }
            .toSet()

    private fun topGenre(c: VodCandidate): String? =
        c.features.firstOrNull { it.namespace == FeatureNamespace.GENRE }?.key

    private fun why(
        s: Scored,
        p: PersonalizationParams,
    ): List<WhyChip> =
        s.contributions
            .filter { (f, part) -> part > 0.0 && f.confidence >= p.minWhyConfidence }
            .sortedByDescending { it.second }
            .take(MAX_CHIPS)
            .map { (f, _) -> WhyChip(f.namespace, f.label) }

    private val VOD_NAMESPACES = setOf(FeatureNamespace.GENRE, FeatureNamespace.DIRECTOR, FeatureNamespace.CAST)
    private const val POOL = 80
    private const val MAX_CHIPS = 2
}
