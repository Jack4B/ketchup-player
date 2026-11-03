package pl.ketchupplayer.tv.core.data.epg

import kotlinx.coroutines.flow.first
import pl.ketchupplayer.tv.core.data.catalog.ProviderSecrets
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.model.EpgSourceKind
import pl.ketchupplayer.tv.core.model.ProviderId
import javax.inject.Inject

/** Adresy XMLTV do importu: włączone źródła (bez Telemana, który nie jest plikiem XMLTV) oraz adresy dostawców. */
class EpgUrlProvider
    @Inject
    constructor(
        private val sources: EpgSourcesRepository,
        private val providers: ProviderDao,
        private val secrets: ProviderSecrets,
    ) {
        suspend fun urls(): List<String> {
            val configured =
                sources.sources
                    .first()
                    .filter { it.enabled && it.kind != EpgSourceKind.TELEMAN }
                    .mapNotNull { it.url }
            val fromProviders = providers.observeAll().first().mapNotNull { secrets.epg(ProviderId(it.id)) }
            return (configured + fromProviders).distinct()
        }
    }
