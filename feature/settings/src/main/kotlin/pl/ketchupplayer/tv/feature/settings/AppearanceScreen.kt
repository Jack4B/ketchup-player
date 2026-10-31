package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.AccentChoice
import pl.ketchupplayer.tv.core.designsystem.AccentId
import pl.ketchupplayer.tv.core.designsystem.AccentWarning
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.StepSlider
import pl.ketchupplayer.tv.core.designsystem.ThemeId

@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppearanceScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(
    state: AppearanceUiState,
    onEvent: (AppearanceEvent) -> Unit,
    onBack: () -> Unit,
) {
    val c = KetchupTheme.colors
    // BACK: najpierw zamyka edytor własnego koloru, potem wychodzi.
    BackHandler { if (state.editor != null) onEvent(AppearanceEvent.CloseCustom) else onBack() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.appearance_title))
        Text(stringResource(R.string.appearance_theme), fontSize = 20.sp, color = c.textSecondary)
        ThemeCards(state, onEvent, first)
        Text(stringResource(R.string.appearance_accent), fontSize = 20.sp, color = c.textSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentId.entries.forEach { id ->
                val selected = (state.accent as? AccentChoice.Preset)?.id == id
                FocusItem(onClick = { onEvent(AppearanceEvent.SelectPreset(id)) }, selected = selected) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(22.dp).background(Color(id.argb), CircleShape))
                        Text(stringResource(accentName(id)), fontSize = 18.sp, color = c.textPrimary)
                    }
                }
            }
            FocusItem(
                onClick = { onEvent(AppearanceEvent.OpenCustom) },
                selected = state.accent is AccentChoice.Custom,
            ) {
                Text(
                    stringResource(R.string.accent_custom),
                    fontSize = 18.sp,
                    color = c.textPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        state.editor?.let { CustomEditorPanel(it, onEvent) }
    }
}

/** Karty motywów w jednym rzędzie: TV 200 dp każda, telefon równe szerokości (pion i poziom). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeCards(
    state: AppearanceUiState,
    onEvent: (AppearanceEvent) -> Unit,
    first: FocusRequester,
) {
    val phone = KetchupTheme.device == DeviceClass.PHONE
    Row(horizontalArrangement = Arrangement.spacedBy(if (phone) 8.dp else 12.dp)) {
        ThemeId.entries.forEachIndexed { i, theme ->
            FocusItem(
                onClick = { onEvent(AppearanceEvent.SelectTheme(theme)) },
                selected = theme == state.theme,
                modifier = if (phone) Modifier.weight(1f) else Modifier.width(200.dp),
                focusRequester = if (i == 0) first else null,
            ) {
                ThemeCard(theme, theme == state.theme)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeCard(
    theme: ThemeId,
    selected: Boolean,
) {
    val c = KetchupTheme.colors
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Miniatura: tło + panel motywu, żeby karta pokazywała jego charakter.
        val t =
            pl.ketchupplayer.tv.core.designsystem.Palettes
                .tokens(theme)
        Box(
            Modifier.fillMaxWidth().height(56.dp).background(
                Color(t.background),
                CircleShape.let {
                    RoundedCornerShape(t.cornerRadiusDp.dp.coerceAtMost(14.dp))
                },
            ),
        ) {
            Box(
                Modifier
                    .padding(
                        10.dp,
                    ).fillMaxWidth(
                        0.6f,
                    ).height(
                        18.dp,
                    ).background(
                        Color(t.surface2),
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(t.cornerRadiusDp.dp.coerceAtMost(9.dp)),
                    ),
            )
        }
        Text(
            stringResource(themeName(theme)) + if (selected) "  ✓" else "",
            fontSize = 18.sp,
            color = c.textPrimary,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CustomEditorPanel(
    e: CustomEditor,
    onEvent: (AppearanceEvent) -> Unit,
) {
    val c = KetchupTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(c.cornerRadius)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HexField(e, onEvent)
        LabeledSlider(R.string.custom_hue, "slider_hue", e.hue / HUE_MAX) {
            onEvent(AppearanceEvent.HueChanged(it * HUE_MAX))
        }
        LabeledSlider(R.string.custom_saturation, "slider_saturation", e.saturation) {
            onEvent(AppearanceEvent.SaturationChanged(it))
        }
        LabeledSlider(R.string.custom_lightness, "slider_lightness", e.lightness) {
            onEvent(AppearanceEvent.LightnessChanged(it))
        }
        EditorNotes(e)
        FocusItem(onClick = { onEvent(AppearanceEvent.CloseCustom) }, modifier = Modifier.width(160.dp)) {
            Text(
                stringResource(R.string.custom_close),
                color = c.textPrimary,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HexField(
    e: CustomEditor,
    onEvent: (AppearanceEvent) -> Unit,
) {
    val c = KetchupTheme.colors
    Text(stringResource(R.string.custom_hex), fontSize = 16.sp, color = c.textSecondary)
    Box(Modifier.width(260.dp).background(c.background, RoundedCornerShape(8.dp)).padding(10.dp)) {
        BasicTextField(
            value = e.hex,
            onValueChange = { onEvent(AppearanceEvent.HexChanged(it)) },
            singleLine = true,
            textStyle = TextStyle(color = c.textPrimary, fontSize = 20.sp),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (e.hexError) Text(stringResource(R.string.custom_hex_error), color = Color(0xFFFF6B6B), fontSize = 16.sp)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EditorNotes(e: CustomEditor) {
    val c = KetchupTheme.colors
    if (e.adjusted) Text(stringResource(R.string.custom_adjusted), color = c.textSecondary, fontSize = 16.sp)
    e.warnings.forEach {
        val res =
            if (it ==
                AccentWarning.LOOKS_LIKE_NEW_EPISODE_GREEN
            ) {
                R.string.custom_warn_green
            } else {
                R.string.custom_warn_red
            }
        Text(stringResource(res), color = c.textSecondary, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LabeledSlider(
    label: Int,
    tag: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), fontSize = 16.sp, color = KetchupTheme.colors.textSecondary)
        StepSlider(value, onChange, modifier = Modifier.testTag(tag))
    }
}

private const val HUE_MAX = 360f

private fun themeName(t: ThemeId) =
    when (t) {
        ThemeId.GRANAT -> R.string.appearance_theme_granat
        ThemeId.CZERN -> R.string.appearance_theme_czern
        ThemeId.BORDO -> R.string.appearance_theme_bordo
    }

private fun accentName(a: AccentId) =
    when (a) {
        AccentId.TURKUS -> R.string.accent_turkus
        AccentId.BIEL -> R.string.accent_biel
        AccentId.BURSZTYN -> R.string.accent_bursztyn
        AccentId.ZOLTY -> R.string.accent_zolty
        AccentId.BLEKIT -> R.string.accent_blekit
        AccentId.FIOLET -> R.string.accent_fiolet
    }
