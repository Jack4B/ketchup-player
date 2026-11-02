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

/** Oceny (IMDb, TMDB, Rotten Tomatoes) i polskie opisy: własne, darmowe klucze API w polach poniżej. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MetadataSettingsScreen(
    state: MetadataUiState,
    onSave: (tmdb: String, omdb: String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val c = KetchupTheme.colors
    var tmdb by remember { mutableStateOf("") }
    var omdb by remember { mutableStateOf("") }
    // Pola wypełniają się po wczytaniu zapisanych kluczy (ViewModel ładuje je asynchronicznie).
    LaunchedEffect(state.tmdb, state.omdb) {
        tmdb = state.tmdb
        omdb = state.omdb
    }
    ScreenScaffold {
        ScreenTitle(stringResource(R.string.metadata_title))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.metadata_intro), fontSize = 18.sp, color = c.textPrimary)
            TextInputField(stringResource(R.string.metadata_tmdb_field), tmdb, { tmdb = it }, Modifier)
            Text(stringResource(R.string.metadata_tmdb_hint), fontSize = 15.sp, color = c.textSecondary)
            TextInputField(
                stringResource(R.string.metadata_omdb_field),
                omdb,
                { omdb = it },
                Modifier,
                onDone = { onSave(tmdb, omdb) },
            )
            Text(stringResource(R.string.metadata_omdb_hint), fontSize = 15.sp, color = c.textSecondary)
            KetchupButton(stringResource(R.string.metadata_save), { onSave(tmdb, omdb) }, primary = true)
            val status =
                when {
                    state.saved -> R.string.metadata_status_saved
                    state.builtIn -> R.string.metadata_status_builtin
                    state.tmdb.isBlank() && state.omdb.isBlank() -> R.string.metadata_status_none
                    else -> R.string.metadata_status_set
                }
            Text(stringResource(status), fontSize = 16.sp, color = c.textSecondary)
        }
    }
}
