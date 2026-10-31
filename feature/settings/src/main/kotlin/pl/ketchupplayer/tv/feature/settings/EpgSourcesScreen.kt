package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.EpgSourceKind
import pl.ketchupplayer.tv.core.ui.touchClickable

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
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(KetchupTheme.colors.background)
                .padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.epg_sources_title), fontSize = 32.sp, color = KetchupTheme.colors.textPrimary)
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
    // Zwykła kolumna zamiast LazyColumn: lista jest krótka, a FocusRequester musi być podpięty przed requestFocus().
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEach { source ->
            val modifier = if (source == sources.first()) Modifier.focusRequester(first) else Modifier
            ListItem(
                selected = false,
                onClick = { onEvent(EpgSourcesEvent.Toggle(source.id, !source.enabled)) },
                modifier =
                    modifier
                        .fillMaxWidth()
                        .touchClickable { onEvent(EpgSourcesEvent.Toggle(source.id, !source.enabled)) },
                headlineContent = { Text(source.name) },
                supportingContent = { Text(hint(source)) },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(if (source.enabled) R.string.epg_state_on else R.string.epg_state_off))
                        Switch(checked = source.enabled, onCheckedChange = null)
                    }
                },
            )
        }
        Button(
            onClick = { onEvent(EpgSourcesEvent.StartAdd) },
            modifier = Modifier.touchClickable { onEvent(EpgSourcesEvent.StartAdd) },
        ) {
            Text(stringResource(R.string.epg_add_source))
        }
    }
}

@Composable
private fun hint(source: EpgSource): String =
    when (source.kind) {
        EpgSourceKind.OVH -> stringResource(R.string.epg_source_ovh_hint)
        EpgSourceKind.TELEMAN -> stringResource(R.string.epg_source_teleman_hint)
        EpgSourceKind.CUSTOM_XMLTV -> source.url ?: stringResource(R.string.epg_source_custom_hint)
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddSourceForm(
    form: AddForm,
    onEvent: (EpgSourcesEvent) -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(stringResource(R.string.epg_field_name), form.name, {
            onEvent(EpgSourcesEvent.NameChanged(it))
        }, Modifier.focusRequester(nameFocus))
        Field(stringResource(R.string.epg_field_url), form.url, { onEvent(EpgSourcesEvent.UrlChanged(it)) })
        form.error?.let { Text(stringResource(errorRes(it)), color = Color(0xFFFF6B6B)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onEvent(EpgSourcesEvent.Submit) },
                modifier = Modifier.touchClickable { onEvent(EpgSourcesEvent.Submit) },
            ) { Text(stringResource(R.string.epg_save)) }
            Button(
                onClick = { onEvent(EpgSourcesEvent.Cancel) },
                modifier = Modifier.touchClickable { onEvent(EpgSourcesEvent.Cancel) },
            ) { Text(stringResource(R.string.epg_cancel)) }
        }
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
    Column {
        Text(label, fontSize = 14.sp, color = KetchupTheme.colors.textSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .border(2.dp, KetchupTheme.colors.textSecondary, RoundedCornerShape(KetchupTheme.colors.cornerRadius))
                .padding(12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle =
                    androidx.compose.ui.text
                        .TextStyle(color = KetchupTheme.colors.textPrimary, fontSize = 20.sp),
                cursorBrush = SolidColor(KetchupTheme.colors.accent),
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}
