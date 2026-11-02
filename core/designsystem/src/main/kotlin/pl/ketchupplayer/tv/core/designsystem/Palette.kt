package pl.ketchupplayer.tv.core.designsystem

/** Motywy v5 (makieta „Ketchup Player v4 makiety”): stonowane, jak TiviMate. Akcent wybierany osobno ([AccentId]). */
enum class ThemeId(
    val defaultAccent: AccentId,
) {
    MORSKI(AccentId.TURKUS),
    CZERN(AccentId.BIEL),
    GRAFIT(AccentId.TURKUS),

    /** Motyw użytkownika: ciemne tło w odcieniu wybranym suwakiem (0–360), reszta jak w pozostałych. */
    WLASNY(AccentId.TURKUS),
}

/** Kolor akcentu (NOTATKI-v3 §3). Bez zieleni (zarezerwowana dla nowych odcinków) i bez czerwieni (błędy). */
enum class AccentId(
    val argb: Long,
) {
    TURKUS(0xFF35C2B4),
    BIEL(0xFFEDEDED),
    BURSZTYN(0xFFE3A857),
    ZOLTY(0xFFE8C547),
    BLEKIT(0xFF6AA6F2),
    FIOLET(0xFFB09AF2),
}

/**
 * Tokeny motywu jako surowe ARGB (Long), żeby dało się je testować bez Compose.
 * [surface2] = panel/komórka EPG, [surface3] = focus.
 */
data class PaletteTokens(
    val background: Long,
    val surface2: Long,
    val surface3: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    val outline: Long,
    val scrim: Long,
    val channelTile: Long,
    val channelTileText: Long,
    val cornerRadiusDp: Int,
)

object Palettes {
    /** Zieleń zarezerwowana wyłącznie dla wskaźnika nowych odcinków (KONSPEKT §7.2, NOTATKI-v3 §3). */
    const val NEW_EPISODE_GREEN: Long = 0xFF3DBE4B

    /** Tekst na przycisku w kolorze akcentu (NOTATKI-v3 §3). */
    const val ON_ACCENT: Long = 0xFF0B0B0B

    /** Odcień tła motywu „Własny”, gdy użytkownik jeszcze go nie ustawił (turkus, jak Morski). */
    const val DEFAULT_HUE = 195

    /** Tokeny motywu „Własny” dla odcienia [hue]: ciemne, mało nasycone powierzchnie, jasny tekst (AA w każdym). */
    private fun custom(hue: Int): PaletteTokens {
        fun hsl(
            s: Double,
            l: Double,
        ) = AccentFit.fromHsl(hue.coerceIn(0, MAX_HUE).toDouble(), s, l)
        return PaletteTokens(
            background = hsl(0.22, 0.06),
            surface2 = hsl(0.20, 0.11),
            surface3 = hsl(0.18, 0.16),
            textPrimary = hsl(0.15, 0.94),
            textSecondary = hsl(0.12, 0.72),
            outline = hsl(0.16, 0.24),
            scrim = hsl(0.25, 0.03),
            channelTile = hsl(0.20, 0.92),
            channelTileText = hsl(0.22, 0.06),
            cornerRadiusDp = 20,
        )
    }

    private const val MAX_HUE = 360

    fun tokens(
        theme: ThemeId,
        customHue: Int = DEFAULT_HUE,
    ): PaletteTokens =
        when (theme) {
            ThemeId.WLASNY -> custom(customHue)
            ThemeId.MORSKI ->
                PaletteTokens(
                    background = 0xFF0B1112,
                    surface2 = 0xFF162023,
                    surface3 = 0xFF1E2B2F,
                    textPrimary = 0xFFE8EFF0,
                    textSecondary = 0xFFA3B6BA,
                    outline = 0xFF2B3B40,
                    scrim = 0xFF060B0C,
                    channelTile = 0xFFE6EEEF,
                    channelTileText = 0xFF0B1112,
                    cornerRadiusDp = 20,
                )
            ThemeId.CZERN ->
                PaletteTokens(
                    background = 0xFF000000,
                    surface2 = 0xFF111111,
                    surface3 = 0xFF262626,
                    textPrimary = 0xFFF2F2F2,
                    textSecondary = 0xFF9E9E9E,
                    outline = 0xFF262626,
                    scrim = 0xFF000000,
                    channelTile = 0xFF1C1C1C,
                    channelTileText = 0xFFF2F2F2,
                    cornerRadiusDp = 6,
                )
            ThemeId.GRAFIT ->
                PaletteTokens(
                    background = 0xFF121315,
                    surface2 = 0xFF1A1C1F,
                    surface3 = 0xFF24272B,
                    textPrimary = 0xFFECEEF0,
                    textSecondary = 0xFFA9AFB6,
                    outline = 0xFF33373D,
                    scrim = 0xFF0A0B0C,
                    channelTile = 0xFFE9ECEF,
                    channelTileText = 0xFF121315,
                    cornerRadiusDp = 18,
                )
        }
}
