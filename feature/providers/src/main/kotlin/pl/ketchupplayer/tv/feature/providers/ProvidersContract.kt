package pl.ketchupplayer.tv.feature.providers

import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo

data class ProviderForm(
    val name: String = "",
    val url: String = "",
    val nameError: Boolean = false,
    val urlError: Boolean = false,
)

data class ProvidersUiState(
    val providers: List<ProviderInfo> = emptyList(),
    val expandedId: ProviderId? = null,
    val form: ProviderForm? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

sealed interface ProvidersEvent {
    data object StartAdd : ProvidersEvent

    data object Cancel : ProvidersEvent

    data class NameChanged(
        val value: String,
    ) : ProvidersEvent

    data class UrlChanged(
        val value: String,
    ) : ProvidersEvent

    data object Submit : ProvidersEvent

    data class Toggle(
        val id: ProviderId,
    ) : ProvidersEvent

    data class Refresh(
        val id: ProviderId,
    ) : ProvidersEvent

    data class Remove(
        val id: ProviderId,
    ) : ProvidersEvent
}
