package pl.ketchupplayer.tv.feature.providers

import pl.ketchupplayer.tv.core.model.ProviderInfo

/** Stan źródła: sprawne, nieaktualne (dawno nieodświeżane), błąd ostatniego pobrania albo pusta lista. */
enum class HealthLevel { OK, STALE, ERROR, EMPTY }

data class SourceHealth(
    val level: HealthLevel,
    /** Wiek ostatniego udanego odświeżenia w godzinach; null, gdy nigdy się nie udało. */
    val ageHours: Long?,
)

/** „Zdrowie źródeł”: prosta ocena listy dostawcy z tego, co aplikacja już wie (bez dodatkowych zapytań do sieci). */
object SourceHealthRules {
    /** Po tylu godzinach bez odświeżenia lista jest „nieaktualna” (EPG odświeża się co 6 h, lista co kilka dni). */
    const val STALE_AFTER_HOURS = 7 * 24L
    private const val MS_PER_HOUR = 3_600_000L

    fun of(
        p: ProviderInfo,
        nowMs: Long,
    ): SourceHealth {
        val age = p.lastRefreshMs?.let { ((nowMs - it) / MS_PER_HOUR).coerceAtLeast(0) }
        val level =
            when {
                p.lastError != null -> HealthLevel.ERROR
                p.channelCount == 0 -> HealthLevel.EMPTY
                age == null || age >= STALE_AFTER_HOURS -> HealthLevel.STALE
                else -> HealthLevel.OK
            }
        return SourceHealth(level, age)
    }
}
