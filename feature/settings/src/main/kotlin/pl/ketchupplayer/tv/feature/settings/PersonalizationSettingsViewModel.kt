package pl.ketchupplayer.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationDiagnostics
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationPrefs
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationRepository
import pl.ketchupplayer.tv.core.data.personalization.TeamChoice
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Komunikat po akcji na ekranie personalizacji. */
sealed interface PersonalMessage {
    data class Exported(
        val path: String,
    ) : PersonalMessage

    data object ExportFailed : PersonalMessage

    data object Cleared : PersonalMessage
}

data class PersonalizationUiState(
    val prefs: PersonalizationPrefs = PersonalizationPrefs(),
    val teams: List<TeamChoice> = emptyList(),
    val diagnostics: PersonalizationDiagnostics = PersonalizationDiagnostics(),
    val message: PersonalMessage? = null,
)

@HiltViewModel
class PersonalizationSettingsViewModel
    @Inject
    constructor(
        private val repository: PersonalizationRepository,
    ) : ViewModel() {
        private val diagnostics = MutableStateFlow(PersonalizationDiagnostics())
        private val message = MutableStateFlow<PersonalMessage?>(null)

        val state: StateFlow<PersonalizationUiState> =
            combine(repository.prefs, repository.teamChoices(), diagnostics, message) { prefs, teams, diag, msg ->
                PersonalizationUiState(prefs, teams, diag, msg)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PersonalizationUiState())

        init {
            reload()
        }

        private fun reload() {
            viewModelScope.launch { diagnostics.value = repository.diagnostics() }
        }

        private fun act(block: suspend () -> Unit) {
            viewModelScope.launch {
                block()
                diagnostics.value = repository.diagnostics()
            }
        }

        fun setEnabled(enabled: Boolean) = act { repository.setEnabled(enabled) }

        fun setIncognito(incognito: Boolean) = act { repository.setIncognito(incognito) }

        fun setRetention(days: Int) = act { repository.setRetentionDays(days) }

        fun follow(teamId: String) = act { repository.follow(teamId) }

        fun unfollow(teamId: String) = act { repository.unfollow(teamId) }

        fun addTeam(name: String) = act { repository.addTeam(name) }

        fun clear() =
            act {
                repository.clear()
                message.value = PersonalMessage.Cleared
            }

        /** Zapisuje kopię danych do [directory] (pamięć aplikacji); plik nie zawiera adresów ani danych dostępu. */
        fun export(directory: File) =
            act {
                val json = repository.export()
                message.value =
                    withContext(Dispatchers.IO) {
                        try {
                            directory.mkdirs()
                            val file = File(directory, EXPORT_FILE)
                            file.writeText(json)
                            PersonalMessage.Exported(file.absolutePath)
                        } catch (ignored: IOException) {
                            PersonalMessage.ExportFailed
                        }
                    }
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val EXPORT_FILE = "ketchup-personalizacja.json"
        }
    }
