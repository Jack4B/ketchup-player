package pl.ketchupplayer.tv.core.designsystem

/** Motywy z makiet v3 (docs/mockups/NOTATKI-v3.md §2). Akcent jest wybierany osobno ([AccentId]). */
enum class ThemeId(
    val defaultAccent: AccentId,
) {
    GRANAT(AccentId.TURKUS),
    CZERN(AccentId.BIEL),
    BORDO(AccentId.BURSZTYN),
}

/** Kolor akcentu (NOTATKI-v3 §3). Bez zieleni (zarezerwowana dla nowych odcinków) i bez czerwieni (błędy). */
enum class AccentId(
    val argb: Long,
) {
    TURKUS(0xFF33B5C4),
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

    fun tokens(theme: ThemeId): PaletteTokens =
        when (theme) {
            ThemeId.GRANAT ->
                PaletteTokens(
                    background = 0xFF0A1A2F,
                    surface2 = 0xFF163152,
                    surface3 = 0xFF21466F,
                    textPrimary = 0xFFEAF2FA,
                    textSecondary = 0xFFA6BCD6,
                    outline = 0xFF24446B,
                    scrim = 0xFF061224,
                    channelTile = 0xFFE8EEF6,
                    channelTileText = 0xFF0A1A2F,
                    cornerRadiusDp = 18,
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
            ThemeId.BORDO ->
                PaletteTokens(
                    background = 0xFF2A1417,
                    surface2 = 0xFF552E33,
                    surface3 = 0xFF6A3A41,
                    textPrimary = 0xFFF6ECE6,
                    textSecondary = 0xFFDDC6BE,
                    outline = 0xFF45232A,
                    scrim = 0xFF1A0A0C,
                    channelTile = 0xFFF1E3D3,
                    channelTileText = 0xFF3A1C20,
                    cornerRadiusDp = 26,
                )
        }
}
