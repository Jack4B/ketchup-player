package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import kotlin.math.ln

/**
 * Progi i wagi personalizacji (docs/design/PERSONALIZACJA-KONSPEKT.md, punkt 7.2). Jedno miejsce do strojenia; testy
 * podają własne wartości tylko tam, gdzie sprawdzają konkretny próg.
 */
data class PersonalizationParams(
    val surfMs: Long = 45_000,
    val shortExitMs: Long = 90_000,
    val minMovieMs: Long = 180_000,
    val minEpisodeMs: Long = 120_000,
    val sessionGapMs: Long = 30 * MINUTE_MS,
    val previewPromoteMs: Long = 20_000,
    val episodeCapMs: Long = 45 * MINUTE_MS,
    val seriesCapDayMs: Long = 150 * MINUTE_MS,
    val castTopN: Int = 5,
    val lambdaDay: Double = ln(2.0) / 60.0,
    val shrinkK: Double = 8.0,
    val likeWeight: Double = 3.0,
    val dislikeWeight: Double = -4.0,
    val exploreShare: Double = 0.08,
    val mmrLambda: Double = 0.7,
    val rowSize: Int = 12,
    val minRowSize: Int = 1,
    val maxSameDirector: Int = 2,
    val maxSameTopGenre: Int = 3,
    val checkpointMs: Long = 15_000,
    val staleOpenMs: Long = 6 * HOUR_MS,
    /** Dłuższa przerwa między próbkami to uśpienie urządzenia albo zamrożony proces: nie liczymy jej jako oglądania. */
    val maxSampleGapMs: Long = 2 * MINUTE_MS,
    val seekJumpMs: Long = 5_000,
    val minWhyConfidence: Double = 0.4,
    val minFeatureConfidence: Double = 0.4,
    val baseFloor: Double = 0.01,
    val coldStartSessions: Int = 2,
    val followDates: Int = 3,
    val followWindowMs: Long = 60 * DAY_MS,
    val independentGapMs: Long = 12 * HOUR_MS,
    val asymmetry: Double = 1.8,
    val minTeamConfidence: Double = 0.5,
    val channelThreshold: Double = 0.75,
    val rejectCooldownMs: Long = 90 * DAY_MS,
    val sportHorizonMs: Long = 14 * DAY_MS,
    val sportSoonMs: Long = 48 * HOUR_MS,
    val sportCards: Int = 3,
    val maxSessions: Int = 5_000,
    val maxFeaturesPerNamespace: Int = 200,
    val defaultRetentionDays: Int = 180,
) {
    fun namespaceWeight(ns: FeatureNamespace): Double =
        when (ns) {
            FeatureNamespace.GENRE -> 1.0
            FeatureNamespace.DIRECTOR -> 1.1
            FeatureNamespace.CAST -> 0.6
            FeatureNamespace.TEAM -> 1.2
            FeatureNamespace.COMPETITION -> 0.7
            FeatureNamespace.CHANNEL -> 0.4
        }

    companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS
        const val DAY_MS = 24 * HOUR_MS
        val DEFAULT = PersonalizationParams()
    }
}
