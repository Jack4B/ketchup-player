package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.Choice
import pl.ketchupplayer.tv.core.designsystem.ChoiceDialog
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.designsystem.TextInputField
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.core.model.RequestMethod
import pl.ketchupplayer.tv.core.network.UserAgentProfile
import pl.ketchupplayer.tv.core.network.UserAgentProfiles

@Composable
fun NetworkSettingsRoute(
    onBack: () -> Unit,
    viewModel: NetworkSettingsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val customs by viewModel.customs.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    NetworkSettingsScreen(
        rows,
        customs,
        viewModel::select,
        onAdd = viewModel::addCustom,
        onRemove = viewModel::removeCustom,
        onBack = onBack,
        probe = probe,
        onProbe = viewModel::probeAll,
        onSelectMethod = viewModel::selectMethod,
        onSelectCustom = viewModel::selectCustom,
    )
}

/**
 * Nagłówek połączenia dla każdej listy: OK otwiera listę wyboru z ptaszkiem (OTT, VLC, TiviMate, Wink, … i własne
 * wpisy). Własne User-Agenty dodajesz i usuwasz poniżej.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    rows: List<ProviderHeaderRow>,
    customs: List<UserAgentProfile>,
    onSelect: (ProviderHeaderRow, String) -> Unit,
    onAdd: (String) -> Boolean,
    onRemove: (UserAgentProfile) -> Unit,
    onBack: () -> Unit,
    probe: List<String> = emptyList(),
    onProbe: (ProviderHeaderRow) -> Unit = {},
    onSelectMethod: (ProviderHeaderRow, RequestMethod) -> Unit = { _, _ -> },
    onSelectCustom: (ProviderHeaderRow, String) -> Boolean = { _, _ -> false },
) {
    BackHandler(onBack = onBack)
    val c = KetchupTheme.colors
    var picking by remember { mutableStateOf<ProviderHeaderRow?>(null) }
    var pickingMethod by remember { mutableStateOf<ProviderHeaderRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Własny User-Agent dla jednej listy: wartość wpisujemy tutaj, bez wcześniejszego dodawania na liście poniżej.
    var editingCustom by remember { mutableStateOf<ProviderHeaderRow?>(null) }
    var customDraft by remember { mutableStateOf("") }
    var customInvalid by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    picking?.let { row ->
        ChoiceDialog(
            title = stringResource(R.string.network_pick_title, row.name),
            choices =
                (UserAgentProfiles.ALL + customs).map {
                    Choice(it.id, it.label, it.value ?: stringResource(R.string.network_auto_hint))
                } +
                    Choice(
                        UserAgentProfiles.CUSTOM_ENTRY.id,
                        UserAgentProfiles.CUSTOM_ENTRY.label,
                        stringResource(R.string.network_custom_list_hint),
                    ),
            selectedId =
                if (UserAgentProfiles.isCustom(
                        row.profile.id,
                    )
                ) {
                    UserAgentProfiles.CUSTOM_ENTRY.id
                } else {
                    row.profile.id
                },
            onSelect = {
                if (it == UserAgentProfiles.CUSTOM_ENTRY.id) {
                    editingCustom = row
                    customDraft = if (UserAgentProfiles.isCustom(row.profile.id)) row.profile.value.orEmpty() else ""
                    customInvalid = false
                } else {
                    onSelect(row, it)
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    pickingMethod?.let { row ->
        ChoiceDialog(
            title = stringResource(R.string.network_method_pick_title, row.name),
            choices =
                RequestMethod.entries.map {
                    val hint =
                        if (it ==
                            RequestMethod.POST
                        ) {
                            R.string.network_method_post_hint
                        } else {
                            R.string.network_method_get_hint
                        }
                    Choice(it.name, it.name, stringResource(hint))
                },
            selectedId = row.method.name,
            onSelect = {
                onSelectMethod(row, RequestMethod.parse(it))
                pickingMethod = null
            },
            onDismiss = { pickingMethod = null },
        )
    }
    ScreenScaffold(scrollable = false) {
        ScreenTitle(stringResource(R.string.network_title))
        Text(stringResource(R.string.network_hint), color = c.textSecondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (rows.isEmpty()) {
                item(key = "empty") { Text(stringResource(R.string.network_empty), color = c.textSecondary) }
            }
            editingCustom?.let { row ->
                item(key = "custom-edit") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.network_pick_title, row.name), color = c.textSecondary)
                        TextInputField(
                            label = stringResource(R.string.network_custom_field),
                            value = customDraft,
                            onChange = {
                                customDraft = it
                                customInvalid = false
                            },
                            modifier = Modifier.requestFocusWhenPlaced(force = true),
                            onDone = {
                                if (onSelectCustom(row, customDraft)) {
                                    editingCustom = null
                                    customDraft = ""
                                } else {
                                    customInvalid = true
                                }
                            },
                        )
                        if (customInvalid) {
                            Text(
                                stringResource(R.string.network_custom_invalid),
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }
            itemsIndexed(rows, key = { _, r -> r.providerId }) { i, row ->
                val own = UserAgentProfiles.isCustom(row.profile.id)
                SettingsListItem(
                    title = row.name,
                    subtitle =
                        if (own) {
                            stringResource(R.string.network_custom_hidden)
                        } else {
                            row.profile.value ?: stringResource(R.string.network_auto_hint)
                        },
                    onClick = { picking = row },
                    modifier = if (i == 0 && editingCustom == null) Modifier.requestFocusWhenPlaced() else Modifier,
                    trailing = {
                        Text(
                            if (own) stringResource(R.string.network_custom_label) else row.profile.label,
                            color = c.textPrimary,
                            maxLines = 1,
                        )
                    },
                )
            }
            items(rows, key = { "method-" + it.providerId }) { row ->
                SettingsListItem(
                    title = stringResource(R.string.network_method_title, row.name),
                    subtitle = stringResource(R.string.network_method_hint),
                    onClick = { pickingMethod = row },
                    trailing = { Text(row.method.name, color = c.textPrimary, maxLines = 1) },
                )
            }
            items(rows, key = { "probe-" + it.providerId }) { row ->
                SettingsListItem(
                    title = stringResource(R.string.network_probe, row.name),
                    subtitle = stringResource(R.string.network_probe_hint),
                    onClick = { onProbe(row) },
                )
            }
            if (probe.isNotEmpty()) {
                item(key = "probe-results") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 8.dp)) {
                        probe.forEach { Text(it, color = c.textPrimary) }
                    }
                }
            }
            item(key = "customs-title") {
                Text(
                    stringResource(R.string.network_custom_title),
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(customs, key = { it.id }) { ua ->
                SettingsListItem(
                    title = ua.label,
                    subtitle = stringResource(R.string.network_custom_remove),
                    onClick = { onRemove(ua) },
                )
            }
            item(key = "add") {
                if (adding) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInputField(
                            label = stringResource(R.string.network_custom_field),
                            value = draft,
                            onChange = {
                                draft = it
                                invalid = false
                            },
                            onDone = {
                                if (onAdd(draft)) {
                                    draft = ""
                                    adding = false
                                } else {
                                    invalid = true
                                }
                            },
                        )
                        if (invalid) Text(stringResource(R.string.network_custom_invalid), color = c.textSecondary)
                    }
                } else {
                    KetchupButton(stringResource(R.string.network_custom_add), { adding = true })
                }
            }
        }
    }
}
