package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.EpgSourceKind

/** Punkt wejścia: spina ViewModel (Hilt) z bezstanowym ekranem. */
@Composable
fun EpgSourcesRoute(
    onBack: () -> Unit,
    viewModel: EpgSourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EpgSourcesScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgSourcesScreen(
    state: EpgSourcesUiState,
    onEvent: (EpgSourcesEvent) -> Unit,
    onBack: () -> Unit,
) {
    // BACK: najpierw zamyka formularz, dopiero potem wychodzi z ekranu.
    BackHandler { if (state.form != null) onEvent(EpgSourcesEvent.Cancel) else onBack() }
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.epg_sources_title))
        if (state.form == null) {
            SourceList(state.sources, onEvent)
        } else {
            AddSourceForm(state.form, onEvent)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceList(
    sources: List<EpgSource>,
    onEvent: (EpgSourcesEvent) -> Unit,
) {
    val first = remember { FocusRequester() }
    // Lista z ViewModelu startuje pusta; fokus dopiero gdy pierwszy wiersz (z requesterem) istnieje.
    LaunchedEffect(sources.firstOrNull()?.id) { if (sources.isNotEmpty()) first.requestFocus() }
    sources.forEachIndexed { i, source ->
        SettingsListItem(
            title = source.name,
            subtitle = hint(source),
            onClick = { onEvent(EpgSourcesEvent.Toggle(source.id, !source.enabled)) },
            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(if (source.enabled) R.string.epg_state_on else R.string.epg_state_off),
                        color = KetchupTheme.colors.textPrimary,
                    )
                    Switch(checked = source.enabled, onCheckedChange = null)
                }
            },
        )
    }
    KetchupButton(stringResource(R.string.epg_add_source), { onEvent(EpgSourcesEvent.StartAdd) })
}

@Composable
private fun hint(source: EpgSource): String =
    when (source.kind) {
        EpgSourceKind.OVH -> stringResource(R.string.epg_source_ovh_hint)
        EpgSourceKind.TELEMAN -> stringResource(R.string.epg_source_teleman_hint)
        EpgSourceKind.CUSTOM_XMLTV -> source.url ?: stringResource(R.string.epg_source_custom_hint)
    }

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddSourceForm(
    form: AddForm,
    onEvent: (EpgSourcesEvent) -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
    Field(
        stringResource(R.string.epg_field_name),
        form.name,
        { onEvent(EpgSourcesEvent.NameChanged(it)) },
        Modifier.focusRequester(nameFocus),
    )
    Field(stringResource(R.string.epg_field_url), form.url, { onEvent(EpgSourcesEvent.UrlChanged(it)) })
    form.error?.let { Text(stringResource(errorRes(it)), color = Color(0xFFFF6B6B)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KetchupButton(stringResource(R.string.epg_save), { onEvent(EpgSourcesEvent.Submit) }, primary = true)
        KetchupButton(stringResource(R.string.epg_cancel), { onEvent(EpgSourcesEvent.Cancel) })
    }
}

private fun errorRes(error: FormError): Int =
    when (error) {
        FormError.NAME -> R.string.epg_error_name
        FormError.URL -> R.string.epg_error_url
        FormError.DUPLICATE -> R.string.epg_error_duplicate
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KetchupTheme.colors
    androidx.compose.foundation.layout.Column {
        Text(label, fontSize = 14.sp, color = c.textSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .border(2.dp, c.textSecondary, RoundedCornerShape(c.cornerRadius.coerceAtMost(12.dp)))
                .padding(12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = c.textPrimary, fontSize = 20.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}
