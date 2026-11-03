package pl.ketchupplayer.tv.feature.settings

import pl.ketchupplayer.tv.core.policy.BkFailoverPolicy
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity
import pl.ketchupplayer.tv.core.policy.FailoverSettings

sealed interface PlaybackSettingsEvent {
    data object ToggleEnabled : PlaybackSettingsEvent

    /** Kolejna czułość z listy (FAST -> STANDARD -> CAUTIOUS -> FAST); OK na pilocie. */
    data object CycleSensitivity : PlaybackSettingsEvent

    /** Kolejne opóźnienie przełączenia TV (5/10/15/25 s). */
    data object CycleDelay : PlaybackSettingsEvent

    /** Wybór czułości z listy (okno z ptaszkiem). */
    data class SetSensitivity(
        val value: FailoverSensitivity,
    ) : PlaybackSettingsEvent

    /** Wybór opóźnienia przełączenia TV z listy (sekundy). */
    data class SetDelay(
        val seconds: Int,
    ) : PlaybackSettingsEvent
}

/** Czysta logika zmiany ustawienia po zdarzeniu; osobno od ViewModelu, żeby dało się ją testować. */
fun FailoverSettings.after(event: PlaybackSettingsEvent): FailoverSettings =
    when (event) {
        PlaybackSettingsEvent.ToggleEnabled -> copy(enabled = !enabled)
        PlaybackSettingsEvent.CycleSensitivity ->
            copy(
                sensitivity = FailoverSensitivity.entries[(sensitivity.ordinal + 1) % FailoverSensitivity.entries.size],
            )
        is PlaybackSettingsEvent.SetSensitivity -> copy(sensitivity = event.value)
        is PlaybackSettingsEvent.SetDelay -> copy(tvDelaySeconds = event.seconds)
        PlaybackSettingsEvent.CycleDelay -> {
            val options = BkFailoverPolicy.TV_DELAY_OPTIONS_SECONDS
            val next = options[(options.indexOf(tvDelaySeconds) + 1) % options.size]
            copy(tvDelaySeconds = next)
        }
    }
