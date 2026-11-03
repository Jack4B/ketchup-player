package pl.ketchupplayer.tv.core.data.epg

import kotlinx.coroutines.flow.Flow
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.ProviderId

enum class AddSourceResult { ADDED, INVALID_NAME, INVALID_URL, DUPLICATE }

interface EpgSourcesRepository {
    /** Zawsze: epg.ovh, Teleman, potem własne źródła w kolejności dodania. */
    val sources: Flow<List<EpgSource>>

    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    )

    suspend fun addCustom(
        name: String,
        url: String,
        providerId: ProviderId? = null,
    ): AddSourceResult

    /** Usuwa tylko własne źródła; wbudowane można wyłączyć, nie usunąć. */
    suspend fun removeCustom(id: String)
}
