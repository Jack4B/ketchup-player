package pl.ketchupplayer.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    MetadataSettingsScreen(state, viewModel::save, onBack)
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
            KetchupButton(stringResource(R.string.metadata_save), { onSave(draft) }, primary = true)
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
