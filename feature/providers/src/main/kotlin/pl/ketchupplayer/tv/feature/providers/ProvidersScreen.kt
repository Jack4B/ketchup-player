package pl.ketchupplayer.tv.feature.providers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.designsystem.TextInputField
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.core.model.ProviderInfo

@Composable
fun ProvidersRoute(
    onBack: () -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProvidersScreen(state, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProvidersScreen(
    state: ProvidersUiState,
    onEvent: (ProvidersEvent) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { if (state.form != null) onEvent(ProvidersEvent.Cancel) else onBack() }
    val c = KetchupTheme.colors
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.providers_title))
        if (state.busy) Text(stringResource(R.string.providers_loading), fontSize = 18.sp, color = c.textSecondary)
        state.message?.let {
            Text(stringResource(R.string.providers_saved_with_error, it), fontSize = 16.sp, color = c.textSecondary)
        }
        if (state.form != null) {
            AddForm(state.form, state.busy, onEvent)
        } else {
            ProviderList(state, onEvent)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProviderList(
    state: ProvidersUiState,
    onEvent: (ProvidersEvent) -> Unit,
) {
    val c = KetchupTheme.colors
    if (state.providers.isEmpty()) {
        Text(stringResource(R.string.providers_empty), fontSize = 20.sp, color = c.textSecondary)
    }
    state.providers.forEachIndexed { i, p ->
        SettingsListItem(
            title = p.name,
            subtitle = summary(p),
            onClick = { onEvent(ProvidersEvent.Toggle(p.id)) },
            modifier = if (i == 0) Modifier.requestFocusWhenPlaced() else Modifier,
        )
        if (state.expandedId == p.id) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KetchupButton(
                    stringResource(R.string.providers_refresh),
                    { onEvent(ProvidersEvent.Refresh(p.id)) },
                    primary = true,
                )
                KetchupButton(stringResource(R.string.providers_remove), { onEvent(ProvidersEvent.Remove(p.id)) })
            }
        }
    }
    KetchupButton(
        stringResource(R.string.providers_add),
        { onEvent(ProvidersEvent.StartAdd) },
        modifier = if (state.providers.isEmpty()) Modifier.requestFocusWhenPlaced() else Modifier,
    )
}

@Composable
private fun summary(p: ProviderInfo): String {
    val error = p.lastError
    return if (error != null) {
        stringResource(R.string.providers_summary_error, p.urlHost, error)
    } else {
        stringResource(
            R.string.providers_summary,
            p.urlHost,
            stringResource(R.string.providers_channels, p.channelCount),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddForm(
    form: ProviderForm,
    busy: Boolean,
    onEvent: (ProvidersEvent) -> Unit,
) {
    val xtream = form.kind == ProviderKind.XTREAM
    val submit: () -> Unit = { if (!busy) onEvent(ProvidersEvent.Submit) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KetchupButton(
            stringResource(R.string.providers_kind_m3u),
            { onEvent(ProvidersEvent.SelectKind(ProviderKind.M3U)) },
            primary = !xtream,
        )
        KetchupButton(
            stringResource(R.string.providers_kind_xtream),
            { onEvent(ProvidersEvent.SelectKind(ProviderKind.XTREAM)) },
            primary = xtream,
        )
    }
    TextInputField(
        stringResource(R.string.providers_field_name),
        form.name,
        { onEvent(ProvidersEvent.NameChanged(it)) },
        Modifier.requestFocusWhenPlaced(),
    )
    if (form.nameError) Text(stringResource(R.string.providers_error_name), color = ERROR_COLOR, fontSize = 16.sp)
    TextInputField(
        stringResource(if (xtream) R.string.providers_field_server else R.string.providers_field_url),
        form.url,
        { onEvent(ProvidersEvent.UrlChanged(it)) },
        onDone = if (xtream) null else submit,
    )
    if (form.urlError) Text(stringResource(R.string.providers_error_url), color = ERROR_COLOR, fontSize = 16.sp)
    if (xtream) {
        TextInputField(stringResource(R.string.providers_field_username), form.username, {
            onEvent(ProvidersEvent.UsernameChanged(it))
        })
        TextInputField(
            stringResource(R.string.providers_field_password),
            form.password,
            { onEvent(ProvidersEvent.PasswordChanged(it)) },
            password = true,
            onDone = submit,
        )
        if (form.credentialsError) {
            Text(
                stringResource(R.string.providers_error_credentials),
                color = ERROR_COLOR,
                fontSize = 16.sp,
            )
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KetchupButton(
            stringResource(R.string.providers_save),
            { if (!busy) onEvent(ProvidersEvent.Submit) },
            primary = true,
        )
        KetchupButton(stringResource(R.string.providers_cancel), { onEvent(ProvidersEvent.Cancel) })
    }
}

private val ERROR_COLOR = Color(0xFFFF6B6B)
