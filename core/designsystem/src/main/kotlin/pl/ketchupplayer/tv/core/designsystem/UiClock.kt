package pl.ketchupplayer.tv.core.designsystem

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Źródło czasu zegarów w UI (szyna, odtwarzacz).
 *
 * Produkcyjnie [System]: bieżący czas i strefa urządzenia, czytane przy każdym odczycie, więc zegar normalnie się
 * odświeża. Zrzuty i testy podstawiają [Frozen] (stały czas i jawna strefa), żeby wzorce nie zależały od godziny
 * uruchomienia. Osobne od `core.common.Clock`: tamten steruje logiką danych (EPG, odświeżanie list).
 */
class UiClock(
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
) {
    /** Czas w formacie HH:mm w strefie źródła. */
    fun hhmm(): String = hhmmAfter(0)

    /** Czas po upływie [deltaMs] od teraz (np. koniec filmu), HH:mm w strefie źródła. */
    fun hhmmAfter(deltaMs: Long): String =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(now().plusMillis(deltaMs).atZone(zone()))

    /** Bieżący czas źródła w milisekundach epoki (do wyświetlania wieku danych, nie do odmierzania czasu). */
    fun nowMs(): Long = now().toEpochMilli()

    companion object {
        /** Czas i strefa urządzenia. */
        val System = UiClock({ Instant.now() }, { ZoneId.systemDefault() })

        /** Stała chwila w jawnej strefie. */
        fun fixed(
            instant: Instant,
            zone: ZoneId,
        ) = UiClock({ instant }, { zone })

        /** Stały czas do zrzutów Roborazzi i testów: 2026-01-15 12:34 w Europe/Warsaw. */
        val Frozen = fixed(Instant.parse("2026-01-15T11:34:00Z"), ZoneId.of("Europe/Warsaw"))
    }
}
