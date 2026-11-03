package pl.ketchupplayer.tv.core.designsystem

/**
 * Jedna skala czasu animacji w całej aplikacji (plan 1.6): fokus reaguje szybko, panele wsuwają się płynnie,
 * skok fokusu nigdy nie jest animowany. Nowe animacje biorą czasy stąd, nie z własnych stałych.
 */
object Motion {
    /** Skala i obramowanie fokusu. */
    const val FOCUS_MS = 120

    /** Szyna nawigacji i małe elementy (chipy, pigułki). */
    const val RAIL_MS = 160

    /** Wysuwane panele (kategorie, grupy, ustawienia z prawej). */
    const val PANEL_MS = 220

    /** Pojawianie się i znikanie nakładek (OSD, karty informacji). */
    const val FADE_MS = 150
}
