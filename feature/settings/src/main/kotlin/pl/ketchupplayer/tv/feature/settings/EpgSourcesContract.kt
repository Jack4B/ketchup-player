package pl.ketchupplayer.tv.feature.settings

import pl.ketchupplayer.tv.core.model.EpgSource

enum class FormError { NAME, URL, DUPLICATE }

data class AddForm(
    val name: String = "",
    val url: String = "",
    val error: FormError? = null,
)

data class EpgSourcesUiState(
    val sources: List<EpgSource> = emptyList(),
    val form: AddForm? = null,
    val refreshing: Boolean = false,
    /** Wynik ostatniego odświeżenia: liczba audycji i kanałów albo błąd; null = jeszcze nie odświeżano. */
    val refreshStatus: String? = null,
    val autoRefresh: Boolean = true,
)

sealed interface EpgSourcesEvent {
    data class Toggle(
        val id: String,
        val enabled: Boolean,
    ) : EpgSourcesEvent

    data class Remove(
        val id: String,
    ) : EpgSourcesEvent

    data object StartAdd : EpgSourcesEvent

    data object RefreshNow : EpgSourcesEvent

    data object ToggleAuto : EpgSourcesEvent

    data class NameChanged(
        val value: String,
    ) : EpgSourcesEvent

    data class UrlChanged(
        val value: String,
    ) : EpgSourcesEvent

    data object Submit : EpgSourcesEvent

    data object Cancel : EpgSourcesEvent
}
