package pl.ketchupplayer.tv.core.data.appearance

import kotlinx.coroutines.flow.Flow
import pl.ketchupplayer.tv.core.designsystem.AccentChoice
import pl.ketchupplayer.tv.core.designsystem.Palettes
import pl.ketchupplayer.tv.core.designsystem.ThemeId

data class Appearance(
    val theme: ThemeId = ThemeId.MORSKI,
    /** null = domyślny akcent motywu (zmiana motywu podpowiada jego akcent, o ile użytkownik nie wybrał własnego). */
    val accent: AccentChoice? = null,
    /** Rozmycie grafiki w tle (Android 12+); domyślnie wyłączone, bo na słabych boxach kosztuje klatki. */
    val glassBlur: Boolean = false,
    /** Czy rozmycie już ustalono (pomiarem płynności albo ręcznie); do tego czasu aplikacja sama je dobierze. */
    val glassBlurDecided: Boolean = false,
    /** Odcień tła motywu „Własny” (0–360). */
    val customHue: Int = Palettes.DEFAULT_HUE,
) {
    val effectiveAccent: AccentChoice get() = accent ?: AccentChoice.Preset(theme.defaultAccent)
}

interface AppearanceRepository {
    val appearance: Flow<Appearance>

    suspend fun setTheme(theme: ThemeId)

    suspend fun setAccent(accent: AccentChoice?)

    suspend fun setGlassBlur(enabled: Boolean)

    suspend fun setCustomHue(hue: Int)
}
