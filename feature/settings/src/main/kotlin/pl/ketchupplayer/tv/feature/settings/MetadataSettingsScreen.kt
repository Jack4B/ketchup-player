package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.data.metadata.ApiKeys
import pl.ketchupplayer.tv.core.data.metadata.KeyCheck
import pl.ketchupplayer.tv.core.data.metadata.KeyStatus
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ScreenScaffold
import pl.ketchupplayer.tv.core.designsystem.ScreenTitle
import pl.ketchupplayer.tv.core.designsystem.TextInputField

@Composable
fun MetadataSettingsRoute(
    onBack: () -> Unit,
    viewModel: MetadataSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MetadataSettingsScreen(state, viewModel::save, onBack, viewModel::check)
}

/**
 * Oceny (IMDb, Rotten Tomatoes, Metacritic, Trakt, TMDB) i polskie opisy: własne, darmowe klucze API. IMDb i Rotten
 * Tomatoes nie mają własnych publicznych API, więc ich oceny idą przez OMDb i MDBList.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MetadataSettingsScreen(
    state: MetadataUiState,
    onSave: (ApiKeys) -> Unit,
    onBack: () -> Unit,
    onCheck: (ApiKeys) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val c = KetchupTheme.colors
    var draft by remember { mutableStateOf(ApiKeys()) }
    // Pola wypełniają się po wczytaniu zapisanych kluczy (ViewModel ładuje je asynchronicznie).
    LaunchedEffect(state.keys) { draft = state.keys }
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.metadata_title))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.metadata_intro), fontSize = 18.sp, color = c.textPrimary)
            KeyField(R.string.metadata_tmdb_field, R.string.metadata_tmdb_hint, draft.tmdb) {
                draft =
                    draft.copy(tmdb = it)
            }
            KeyField(R.string.metadata_omdb_field, R.string.metadata_omdb_hint, draft.omdb) {
                draft =
                    draft.copy(omdb = it)
            }
            KeyField(R.string.metadata_mdblist_field, R.string.metadata_mdblist_hint, draft.mdblist) {
                draft = draft.copy(mdblist = it)
            }
            KeyField(R.string.metadata_trakt_field, R.string.metadata_trakt_hint, draft.trakt) {
                draft = draft.copy(trakt = it)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KetchupButton(stringResource(R.string.metadata_save), { onSave(draft) }, primary = true)
                KetchupButton(stringResource(R.string.metadata_check), { onCheck(draft) })
            }
            if (state.checking) {
                Text(
                    stringResource(R.string.metadata_checking),
                    fontSize = 16.sp,
                    color = c.textSecondary,
                )
            }
            state.checks?.forEach { check -> KeyCheckLine(check) }
            val status =
                when {
                    state.saved -> R.string.metadata_status_saved
                    state.builtIn -> R.string.metadata_status_builtin
                    !state.keys.any -> R.string.metadata_status_none
                    else -> R.string.metadata_status_set
                }
            Text(stringResource(status), fontSize = 16.sp, color = c.textSecondary)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyField(
    label: Int,
    hint: Int,
    value: String,
    onChange: (String) -> Unit,
) {
    Text(stringResource(hint), fontSize = 15.sp, color = KetchupTheme.colors.textSecondary)
    TextInputField(stringResource(label), value, onChange, Modifier)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyCheckLine(check: KeyCheck) {
    val c = KetchupTheme.colors
    val text =
        when (check.status) {
            KeyStatus.NO_KEY -> stringResource(R.string.metadata_check_no_key, check.source)
            KeyStatus.OK -> stringResource(R.string.metadata_check_ok, check.source)
            KeyStatus.BAD_KEY -> stringResource(R.string.metadata_check_bad, check.source, check.detail)
            KeyStatus.LIMIT -> stringResource(R.string.metadata_check_limit, check.source)
            KeyStatus.ERROR -> stringResource(R.string.metadata_check_error, check.source, check.detail)
        }
    val color = if (check.status == KeyStatus.OK) c.textPrimary else c.textSecondary
    Text(text, fontSize = 16.sp, color = color)
}
