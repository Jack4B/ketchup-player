package pl.ketchupplayer.tv.core.policy

enum class FailoverSensitivity { FAST, STANDARD, CAUTIOUS }

enum class IncidentReason { REBUFFER, OTHER }

enum class ContentKind { TV, VOD, SERIES }

/** Ustawienia przełączników failovera (klucze `bk_failover_*` z KONSPEKT §9). */
data class FailoverSettings(
    val enabled: Boolean = true,
    val tv: Boolean = true,
    val vod: Boolean = true,
    val series: Boolean = true,
    val sensitivity: FailoverSensitivity = FailoverSensitivity.STANDARD,
    val tvDelaySeconds: Int = BkFailoverPolicy.DEFAULT_TV_DELAY_SECONDS,
)

/**
 * Port z `BkFailoverPolicy` (stałe: KONSPEKT §10.1 pkt 4, wersja 1.8.0.34).
 * Odtworzone ze specyfikacji: wartości liczbowe.
 * Wnioski ze spec (do weryfikacji golden master): domyślne wartości przełączników
 * oraz fallback opóźnienia TV na 25 s dla wartości spoza zbioru.
 */
object BkFailoverPolicy {
    const val DEFAULT_TV_DELAY_SECONDS = 25
    val TV_DELAY_OPTIONS_SECONDS: List<Int> = listOf(5, 10, 15, 25)

    fun minimumStallMs(s: FailoverSensitivity): Long =
        when (s) {
            FailoverSensitivity.FAST -> 1_500L
            FailoverSensitivity.STANDARD -> 2_500L
            FailoverSensitivity.CAUTIOUS -> 4_500L
        }

    fun cooldownMs(s: FailoverSensitivity): Long =
        when (s) {
            FailoverSensitivity.FAST -> 12_000L
            FailoverSensitivity.STANDARD -> 20_000L
            FailoverSensitivity.CAUTIOUS -> 30_000L
        }

    fun incidentThreshold(
        s: FailoverSensitivity,
        reason: IncidentReason,
    ): Int =
        when (reason) {
            IncidentReason.REBUFFER ->
                when (s) {
                    FailoverSensitivity.FAST -> 1
                    FailoverSensitivity.STANDARD -> 2
                    FailoverSensitivity.CAUTIOUS -> 3
                }
            IncidentReason.OTHER ->
                when (s) {
                    FailoverSensitivity.FAST -> 2
                    FailoverSensitivity.STANDARD -> 3
                    FailoverSensitivity.CAUTIOUS -> 4
                }
        }

    fun tvRecoveryRetries(s: FailoverSensitivity): Int =
        when (s) {
            FailoverSensitivity.FAST -> 0
            FailoverSensitivity.STANDARD -> 1
            FailoverSensitivity.CAUTIOUS -> 2
        }

    fun vodRecoveryRetries(s: FailoverSensitivity): Int =
        when (s) {
            FailoverSensitivity.FAST -> 1
            FailoverSensitivity.STANDARD -> 2
            FailoverSensitivity.CAUTIOUS -> 3
        }

    fun tvSwitchDelaySeconds(requested: Int): Int =
        if (requested in TV_DELAY_OPTIONS_SECONDS) requested else DEFAULT_TV_DELAY_SECONDS

    /** Przełącznik globalny ORAZ przełącznik dla danego typu treści. */
    fun enabled(
        settings: FailoverSettings,
        kind: ContentKind,
    ): Boolean =
        settings.enabled &&
            when (kind) {
                ContentKind.TV -> settings.tv
                ContentKind.VOD -> settings.vod
                ContentKind.SERIES -> settings.series
            }
}
