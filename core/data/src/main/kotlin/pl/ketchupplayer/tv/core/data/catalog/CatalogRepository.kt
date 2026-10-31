package pl.ketchupplayer.tv.core.data.catalog

import kotlinx.coroutines.flow.Flow
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo
import pl.ketchupplayer.tv.core.model.StreamSpec

enum class AddProviderError { INVALID_NAME, INVALID_URL }

/** Wynik dodania dostawcy: id albo błąd walidacji/pobrania. */
sealed interface AddProviderResult {
    data class Added(
        val id: ProviderId,
        val channelCount: Int,
    ) : AddProviderResult

    data class Invalid(
        val error: AddProviderError,
    ) : AddProviderResult

    /** Dostawca zapisany, ale pierwsze pobranie listy się nie udało (można ponowić z ekranu dostawców). */
    data class SavedWithError(
        val id: ProviderId,
        val message: String,
    ) : AddProviderResult
}

/** Katalog kanałów (offline-first: UI zawsze czyta z bazy). */
interface CatalogRepository {
    val providers: Flow<List<ProviderInfo>>

    fun liveChannels(): Flow<List<Channel>>

    fun liveGroups(): Flow<List<String>>

    suspend fun addM3u(
        name: String,
        url: String,
    ): AddProviderResult

    /** Pobiera listę ponownie; przy błędzie zostawia dotychczasowe kanały i zapisuje komunikat błędu. */
    suspend fun refresh(id: ProviderId): Result<Int>

    suspend fun remove(id: ProviderId)

    suspend fun channel(id: String): Channel?

    /** Rozwiązuje adres odtwarzania kanału przez jego dostawcę. */
    suspend fun resolveStream(channelId: String): Result<StreamSpec>
}
