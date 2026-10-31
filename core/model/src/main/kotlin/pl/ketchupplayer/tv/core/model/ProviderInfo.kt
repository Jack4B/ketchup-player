package pl.ketchupplayer.tv.core.model

/** Dostawca do wyświetlenia w UI (bez sekretów; [urlHost] to sam host, nie pełny adres z danymi logowania). */
data class ProviderInfo(
    val id: ProviderId,
    val name: String,
    val type: ProviderType,
    val urlHost: String,
    val channelCount: Int,
    val lastRefreshMs: Long?,
    val lastError: String?,
)
