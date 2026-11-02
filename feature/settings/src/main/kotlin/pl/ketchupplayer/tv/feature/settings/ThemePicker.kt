package pl.ketchupplayer.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.PaletteTokens
import pl.ketchupplayer.tv.core.designsystem.Palettes
import pl.ketchupplayer.tv.core.designsystem.ThemeId

/**
 * Wybór motywu: siatka kart równej szerokości (2 w rzędzie na TV, 4 na telefonie), każda z miniaturą ekranu w kolorach
 * motywu. Karty zawsze mieszczą się w panelu (wcześniej cztery po 200 dp wypadały poza wąski panel Ustawień).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemePicker(
    selected: ThemeId,
    customHue: Int,
    onSelect: (ThemeId) -> Unit,
    firstFocus: FocusRequester,
) {
    val phone = KetchupTheme.device == DeviceClass.PHONE
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
        maxItemsInEachRow = if (phone) THEMES_PER_ROW_PHONE else THEMES_PER_ROW_TV,
    ) {
        ThemeId.entries.forEachIndexed { i, theme ->
            FocusItem(
                onClick = { onSelect(theme) },
                selected = theme == selected,
                modifier = Modifier.weight(1f),
                focusRequester = if (i == 0) firstFocus else null,
            ) {
                ThemeCard(theme, theme == selected, customHue)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeCard(
    theme: ThemeId,
    selected: Boolean,
    hue: Int,
) {
    val c = KetchupTheme.colors
    Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemePreview(Palettes.tokens(theme, hue), c.accent)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(themeName(theme)),
                fontSize = 18.sp,
                color = c.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Box(Modifier.size(20.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", fontSize = 13.sp, color = c.onAccent)
                }
            }
        }
    }
}

/** Miniatura ekranu: tło motywu, szyna po lewej, panel treści i pasek akcentu. */
@Composable
private fun ThemePreview(
    t: PaletteTokens,
    accent: Color,
) {
    val radius = RoundedCornerShape(t.cornerRadiusDp.dp.coerceAtMost(PREVIEW_MAX_RADIUS))
    Row(
        Modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .background(Color(t.background), radius)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(10.dp).fillMaxHeight().background(Color(t.surface3), RoundedCornerShape(5.dp)))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth(PREVIEW_BAR).height(8.dp).background(accent, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(t.surface2), RoundedCornerShape(6.dp)))
        }
    }
}

internal fun themeName(t: ThemeId): Int =
    when (t) {
        ThemeId.MORSKI -> R.string.appearance_theme_morski
        ThemeId.CZERN -> R.string.appearance_theme_czern
        ThemeId.GRAFIT -> R.string.appearance_theme_grafit
        ThemeId.WLASNY -> R.string.appearance_theme_wlasny
    }

private val CARD_GAP = 12.dp
private val CARD_PADDING = 10.dp
private val PREVIEW_HEIGHT = 64.dp
private val PREVIEW_MAX_RADIUS = 12.dp
private const val PREVIEW_BAR = 0.45f
private const val THEMES_PER_ROW_TV = 2
private const val THEMES_PER_ROW_PHONE = 4
