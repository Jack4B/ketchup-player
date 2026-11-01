package pl.ketchupplayer.tv.feature.providers

import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo

enum class ProviderKind { M3U, XTREAM }

data class ProviderForm(
    val kind: ProviderKind = ProviderKind.M3U,
    val name: String = "",
    /** Adres listy M3U albo serwera Xtream. */
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val nameError: Boolean = false,
    val urlError: Boolean = false,
    val credentialsError: Boolean = false,
    /** Wybrany nagłówek połączenia (id z puli); „auto” = domyślny OTT Navigator. */
    val userAgentId: String = "auto",
)

data class ProvidersUiState(
    val providers: List<ProviderInfo> = emptyList(),
    val expandedId: ProviderId? = null,
    val form: ProviderForm? = null,
    val lastForm: ProviderForm? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val status: pl.ketchupplayer.tv.core.data.catalog.ImportStatus? = null,
    val downloadedBytes: Long = 0,
    /** Własne User-Agenty użytkownika (do listy wyboru w formularzu). */
    val customAgents: List<pl.ketchupplayer.tv.core.network.UserAgentProfile> = emptyList(),
)

sealed interface ProvidersEvent {
    data object StartAdd : ProvidersEvent

    data object Cancel : ProvidersEvent

    /** Powrót do formularza po nieudanym dodaniu: zostają wpisane dane i komunikat błędu. */
    data object RetryAdd : ProvidersEvent

    data class NameChanged(
        val value: String,
    ) : ProvidersEvent

    data class UrlChanged(
        val value: String,
    ) : ProvidersEvent

    data class UsernameChanged(
        val value: String,
    ) : ProvidersEvent

    data class PasswordChanged(
        val value: String,
    ) : ProvidersEvent

    data class SelectKind(
        val kind: ProviderKind,
    ) : ProvidersEvent

    data object Submit : ProvidersEvent

    /** Wybór User-Agenta z listy w formularzu (ptaszek przy zaznaczonym). */
    data class SelectUserAgent(
        val id: String,
    ) : ProvidersEvent

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
