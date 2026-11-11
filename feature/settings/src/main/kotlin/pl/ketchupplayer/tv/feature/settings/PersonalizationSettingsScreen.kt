@file:Suppress("MatchingDeclarationName")

package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.data.personalization.DataStorePersonalizationSettings
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationDiagnostics
import pl.ketchupplayer.tv.core.data.personalization.TeamChoice
import pl.ketchupplayer.tv.core.designsystem.Choice
import pl.ketchupplayer.tv.core.designsystem.ChoiceDialog
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.SettingsListItem
import pl.ketchupplayer.tv.core.designsystem.TextInputField
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced
import pl.ketchupplayer.tv.core.model.personalization.FollowState
import pl.ketchupplayer.tv.core.policy.personalization.WarsawTime
import java.io.File

/** Akcje ekranu personalizacji (osobny typ, żeby ekran dało się testować bez ViewModelu). */
class PersonalizationActions(
    val onEnabled: (Boolean) -> Unit = {},
    val onIncognito: (Boolean) -> Unit = {},
    val onRetention: (Int) -> Unit = {},
    val onFollow: (String) -> Unit = {},
    val onUnfollow: (String) -> Unit = {},
    val onAddTeam: (String) -> Unit = {},
    val onExport: () -> Unit = {},
    val onClear: () -> Unit = {},
)

@Composable
fun PersonalizationSettingsRoute(
    onBack: () -> Unit,
    viewModel: PersonalizationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val actions =
        remember(viewModel) {
            PersonalizationActions(
                onEnabled = viewModel::setEnabled,
                onIncognito = viewModel::setIncognito,
                onRetention = viewModel::setRetention,
                onFollow = viewModel::follow,
                onUnfollow = viewModel::unfollow,
                onAddTeam = viewModel::addTeam,
                onExport = { viewModel.export(File(context.getExternalFilesDir(null) ?: context.filesDir, "export")) },
                onClear = viewModel::clear,
            )
        }
    PersonalizationSettingsScreen(state, actions, onBack)
}

private enum class PersonalDialog { RETENTION, TEAM, CLEAR }

/**
 * Ustawienia personalizacji: jawna zgoda (domyślnie wyłączona), tryb „nie ucz się”, retencja, obserwowane drużyny,
 * kopia danych i ich usunięcie. Bez włączonej zgody widać tylko przełącznik i opis.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PersonalizationSettingsScreen(
    state: PersonalizationUiState,
    actions: PersonalizationActions,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = KetchupTheme.colors
    var dialog by remember { mutableStateOf<PersonalDialog?>(null) }
    val enabled = state.prefs.enabled
    PersonalDialogs(dialog, state, actions) { dialog = null }
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.personal_title))
        Text(stringResource(R.string.personal_intro), fontSize = 16.sp, color = c.textSecondary)
        SettingsListItem(
            title = stringResource(R.string.personal_enable),
            subtitle = stringResource(if (enabled) R.string.personal_enable_on else R.string.personal_enable_off),
            onClick = { actions.onEnabled(!enabled) },
            modifier = Modifier.requestFocusWhenPlaced().testTag(TAG_PERSONAL_ENABLE),
            trailing = { Switch(checked = enabled, onCheckedChange = null) },
        )
        if (enabled) {
            SettingsListItem(
                title = stringResource(R.string.personal_incognito),
                subtitle = stringResource(R.string.personal_incognito_hint),
                onClick = { actions.onIncognito(!state.prefs.incognito) },
                modifier = Modifier.testTag(TAG_PERSONAL_INCOGNITO),
                trailing = { Switch(checked = state.prefs.incognito, onCheckedChange = null) },
            )
            SettingsListItem(
                title = stringResource(R.string.personal_retention),
                subtitle = stringResource(R.string.personal_retention_hint),
                onClick = { dialog = PersonalDialog.RETENTION },
                trailing = {
                    Text(stringResource(R.string.personal_days, state.prefs.retentionDays), color = c.textPrimary)
                },
            )
            TeamsSection(state.teams, actions) { dialog = PersonalDialog.TEAM }
            SettingsListItem(
                title = stringResource(R.string.personal_export),
                subtitle = stringResource(R.string.personal_export_hint),
                onClick = actions.onExport,
            )
        }
        SettingsListItem(
            title = stringResource(R.string.personal_clear),
            subtitle = stringResource(R.string.personal_clear_hint),
            onClick = { dialog = PersonalDialog.CLEAR },
            modifier = Modifier.testTag(TAG_PERSONAL_CLEAR),
        )
        state.message?.let { Text(messageText(it), fontSize = 16.sp, color = c.textPrimary) }
        if (enabled) Diagnostics(state.diagnostics)
    }
}

@Composable
private fun messageText(message: PersonalMessage): String =
    when (message) {
        is PersonalMessage.Exported -> stringResource(R.string.personal_export_done, message.path)
        PersonalMessage.ExportFailed -> stringResource(R.string.personal_export_failed)
        PersonalMessage.Cleared -> stringResource(R.string.personal_cleared)
    }

@Composable
private fun PersonalDialogs(
    dialog: PersonalDialog?,
    state: PersonalizationUiState,
    actions: PersonalizationActions,
    onClose: () -> Unit,
) {
    when (dialog) {
        PersonalDialog.RETENTION ->
            ChoiceDialog(
                title = stringResource(R.string.personal_retention),
                choices =
                    DataStorePersonalizationSettings.RETENTION_CHOICES.map {
                        Choice(it.toString(), stringResource(R.string.personal_days, it))
                    },
                selectedId = state.prefs.retentionDays.toString(),
                onSelect = {
                    actions.onRetention(it.toInt())
                    onClose()
                },
                onDismiss = onClose,
            )
        PersonalDialog.TEAM ->
            ChoiceDialog(
                title = stringResource(R.string.personal_team_pick),
                choices =
                    state.teams
                        .filter { it.state != FollowState.FOLLOWED }
                        .sortedBy { it.team.name }
                        .map { Choice(it.team.id, it.team.name) },
                selectedId = "",
                onSelect = {
                    actions.onFollow(it)
                    onClose()
                },
                onDismiss = onClose,
            )
        PersonalDialog.CLEAR ->
            ChoiceDialog(
                title = stringResource(R.string.personal_clear_confirm),
                choices =
                    listOf(
                        Choice(CLEAR_NO, stringResource(R.string.personal_clear_no)),
                        Choice(CLEAR_YES, stringResource(R.string.personal_clear_yes)),
                    ),
                selectedId = "",
                onSelect = {
                    if (it == CLEAR_YES) actions.onClear()
                    onClose()
                },
                onDismiss = onClose,
            )
        null -> Unit
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TeamsSection(
    teams: List<TeamChoice>,
    actions: PersonalizationActions,
    onPick: () -> Unit,
) {
    val c = KetchupTheme.colors
    var custom by rememberSaveable { mutableStateOf("") }
    val chosen = teams.filter { it.state != null }.sortedBy { it.team.name }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.personal_teams_header),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
        )
        if (chosen.isEmpty()) {
            Text(stringResource(R.string.personal_teams_empty), fontSize = 16.sp, color = c.textSecondary)
        }
        chosen.forEach { choice ->
            val followed = choice.state == FollowState.FOLLOWED
            SettingsListItem(
                title = choice.team.name,
                subtitle = stringResource(teamStateLabel(choice.state)),
                onClick = { if (followed) actions.onUnfollow(choice.team.id) else actions.onFollow(choice.team.id) },
            )
        }
        SettingsListItem(
            title = stringResource(R.string.personal_team_add),
            subtitle = stringResource(R.string.personal_team_add_hint),
            onClick = onPick,
            modifier = Modifier.testTag(TAG_PERSONAL_ADD_TEAM),
        )
        val addCustom = {
            if (custom.isNotBlank()) actions.onAddTeam(custom.trim())
            custom = ""
        }
        TextInputField(stringResource(R.string.personal_team_custom), custom, { custom = it }, onDone = addCustom)
        KetchupButton(
            stringResource(R.string.personal_team_custom_add),
            addCustom,
            Modifier.testTag(TAG_PERSONAL_ADD_CUSTOM),
        )
    }
}

private fun teamStateLabel(state: FollowState?): Int =
    when (state) {
        FollowState.FOLLOWED -> R.string.personal_team_followed
        FollowState.REJECTED -> R.string.personal_team_rejected
        else -> R.string.personal_team_suggested
    }

/** Liczby zebranych danych (bez tytułów i identyfikatorów): widać, czy personalizacja w ogóle ma z czego wnioskować. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Diagnostics(d: PersonalizationDiagnostics) {
    val c = KetchupTheme.colors
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.personal_stats_header),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = c.textPrimary,
        )
        Text(
            stringResource(R.string.personal_stats_sessions, d.sessions, d.positive, d.neutral, d.failures),
            fontSize = 16.sp,
            color = c.textSecondary,
        )
        Text(
            stringResource(R.string.personal_stats_features, d.features, d.confidentFeatures, d.followedTeams),
            fontSize = 16.sp,
            color = c.textSecondary,
        )
        val built = d.snapshotBuiltAtMs
        Text(
            if (built == null) {
                stringResource(R.string.personal_stats_never)
            } else {
                stringResource(
                    R.string.personal_stats_built,
                    WarsawTime.dayMonth(built) + ", " + WarsawTime.hhmm(built),
                )
            },
            fontSize = 16.sp,
            color = c.textSecondary,
        )
    }
}

internal const val TAG_PERSONAL_ENABLE = "personal_enable"
internal const val TAG_PERSONAL_INCOGNITO = "personal_incognito"
internal const val TAG_PERSONAL_ADD_TEAM = "personal_add_team"
internal const val TAG_PERSONAL_CLEAR = "personal_clear"
internal const val TAG_PERSONAL_ADD_CUSTOM = "personal_add_custom"
private const val CLEAR_YES = "yes"
private const val CLEAR_NO = "no"
