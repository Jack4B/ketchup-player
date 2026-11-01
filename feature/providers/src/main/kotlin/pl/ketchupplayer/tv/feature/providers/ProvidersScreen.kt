package pl.ketchupplayer.tv.feature.providers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // Po zamknięciu formularza (zapis lub anulowanie) fokus wraca na „Dodaj dostawcę”, także w panelu Ustawień.
    var hadForm by remember { mutableStateOf(false) }
    if (state.form != null && !hadForm) hadForm = true
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.providers_title))
        if (state.busy) ImportProgress(state.status, state.downloadedBytes)
        state.message?.let {
            Text(stringResource(R.string.providers_saved_with_error, it), fontSize = 16.sp, color = c.textSecondary)
        }
        if (state.form != null) {
            AddForm(state.form, state.busy, onEvent, customAgents = state.customAgents)
        } else {
            ProviderList(state, onEvent, focusAdd = hadForm)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProviderList(
    state: ProvidersUiState,
    onEvent: (ProvidersEvent) -> Unit,
    focusAdd: Boolean,
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
        modifier =
            if (state.providers.isEmpty() || focusAdd) Modifier.requestFocusWhenPlaced(force = focusAdd) else Modifier,
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
internal fun AddForm(
    form: ProviderForm,
    busy: Boolean,
    onEvent: (ProvidersEvent) -> Unit,
    showActions: Boolean = true,
    customAgents: List<pl.ketchupplayer.tv.core.network.UserAgentProfile> = emptyList(),
) {
    var pickingAgent by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val xtream = form.kind == ProviderKind.XTREAM
    val submit: () -> Unit = { if (!busy) onEvent(ProvidersEvent.Submit) }
    // Po wysłaniu chowamy klawiaturę ekranową, żeby nie zasłaniała postępu importu.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(busy) { if (busy) keyboard?.hide() }
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
        Modifier.requestFocusWhenPlaced(force = true),
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
    // Wybór nagłówka przed pierwszym pobraniem: panele często odrzucają (404) nieznane aplikacje.
    KetchupButton(
        stringResource(
            R.string.providers_user_agent,
            pl.ketchupplayer.tv.core.network.UserAgentProfiles
                .byId(form.userAgentId)
                .label,
        ),
        { pickingAgent = true },
    )
    if (pickingAgent) {
        val auto = stringResource(R.string.providers_ua_auto_hint)
        pl.ketchupplayer.tv.core.designsystem.ChoiceDialog(
            title = stringResource(R.string.providers_ua_pick),
            choices =
                (pl.ketchupplayer.tv.core.network.UserAgentProfiles.ALL + customAgents).map {
                    pl.ketchupplayer.tv.core.designsystem
                        .Choice(it.id, it.label, it.value ?: auto)
                },
            selectedId = form.userAgentId,
            onSelect = {
                onEvent(ProvidersEvent.SelectUserAgent(it))
                pickingAgent = false
            },
            onDismiss = { pickingAgent = false },
        )
    }
    if (showActions) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KetchupButton(
                stringResource(R.string.providers_save),
                { if (!busy) onEvent(ProvidersEvent.Submit) },
                primary = true,
            )
            KetchupButton(stringResource(R.string.providers_cancel), { onEvent(ProvidersEvent.Cancel) })
        }
    }
}

internal val ERROR_COLOR = Color(0xFFFF6B6B)
