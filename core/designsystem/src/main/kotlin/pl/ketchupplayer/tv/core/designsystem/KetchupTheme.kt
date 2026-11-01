package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@Immutable
data class KetchupColors(
    val theme: ThemeId,
    val background: Color,
    val surface2: Color,
    val surface3: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val scrim: Color,
    val channelTile: Color,
    val channelTileText: Color,
    val accent: Color,
    val onAccent: Color,
    val newEpisode: Color,
    val cornerRadius: Dp,
)

private fun PaletteTokens.toColors(
    theme: ThemeId,
    accentArgb: Long,
    onAccentArgb: Long,
) = KetchupColors(
    theme = theme,
    background = Color(background),
    surface2 = Color(surface2),
    surface3 = Color(surface3),
    textPrimary = Color(textPrimary),
    textSecondary = Color(textSecondary),
    outline = Color(outline),
    scrim = Color(scrim),
    channelTile = Color(channelTile),
    channelTileText = Color(channelTileText),
    accent = Color(accentArgb),
    onAccent = Color(onAccentArgb),
    newEpisode = Color(Palettes.NEW_EPISODE_GREEN),
    cornerRadius = cornerRadiusDp.dp,
)

private val LocalDeviceClass = staticCompositionLocalOf { DeviceClass.TV }

private val LocalKetchupColors =
    staticCompositionLocalOf<KetchupColors> {
        error("KetchupTheme nie jest ustawiony")
    }

object KetchupTheme {
    val device: DeviceClass
        @Composable
        @ReadOnlyComposable
        get() = LocalDeviceClass.current

    val colors: KetchupColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKetchupColors.current
}

/** Motyw aplikacji: jeden z 3 motywów + osobny kolor akcentu (makiety v3). Ring focusu = akcent. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KetchupTheme(
    theme: ThemeId = ThemeId.GRANAT,
    accent: AccentChoice = AccentChoice.Preset(theme.defaultAccent),
    device: DeviceClass = rememberDeviceClass(),
    content: @Composable () -> Unit,
) {
    val colors =
        remember(theme, accent) {
            when (accent) {
                is AccentChoice.Preset -> Palettes.tokens(theme).toColors(theme, accent.id.argb, Palettes.ON_ACCENT)
                is AccentChoice.Custom ->
                    AccentFit.fit(theme, accent.argb).let {
                        Palettes.tokens(theme).toColors(theme, it.argb, it.onAccent)
                    }
            }
        }
    val scheme =
        remember(colors) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.background,
                onBackground = colors.textPrimary,
                surface = colors.surface2,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surface2,
                onSurfaceVariant = colors.textSecondary,
                inverseSurface = colors.surface3,
                inverseOnSurface = colors.textPrimary,
                border = colors.accent,
                borderVariant = colors.outline,
                scrim = colors.scrim,
            )
        }
    CompositionLocalProvider(LocalKetchupColors provides colors, LocalDeviceClass provides device) {
        MaterialTheme(colorScheme = scheme) {
            // Bazowy styl motywu ma stałą wysokość linii dla swojego rozmiaru czcionki: przy `fontSize` ustawianym
            // w ekranach zawinięte napisy nachodziłyby na siebie. Bez niej linia skaluje się z czcionką.
            CompositionLocalProvider(LocalTextStyle provides TextStyle.Default, content = content)
        }
    }
}
