package pl.ketchupplayer.tv.core.data.epg

import kotlinx.coroutines.flow.first
import pl.ketchupplayer.tv.core.data.catalog.ProviderSecrets
import pl.ketchupplayer.tv.core.data.network.ProviderHeadersRepository
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.model.EpgSourceKind
import pl.ketchupplayer.tv.core.model.ProviderId
import javax.inject.Inject

/** Adres XMLTV do pobrania i User-Agent listy, z którą jest związany (null = domyślny klienta). */
data class EpgTarget(
    val url: String,
    val userAgent: String?,
)

/** Adresy XMLTV do importu: włączone źródła (bez Telemana, który nie jest plikiem XMLTV) oraz adresy dostawców. */
class EpgUrlProvider
    @Inject
    constructor(
        private val sources: EpgSourcesRepository,
        private val providers: ProviderDao,
        private val secrets: ProviderSecrets,
        private val headers: ProviderHeadersRepository? = null,
    ) {
        suspend fun urls(): List<String> = targets().map { it.url }

        /**
         * Adresy z User-Agent właściwym dla listy: źródło związane z dostawcą i adres EPG dostawcy idą z jego
         * User-Agent, pozostałe źródła z domyślnym. Ten sam adres występuje raz (wygrywa wpis z User-Agent listy).
         */
        suspend fun targets(): List<EpgTarget> {
            val configured =
                sources.sources
                    .first()
                    .filter { it.enabled && it.kind != EpgSourceKind.TELEMAN }
                    .mapNotNull { s -> s.url?.let { EpgTarget(it, s.providerId?.let { id -> agentOf(id.value) }) } }
            val fromProviders =
                providers.observeAll().first().mapNotNull { p ->
                    secrets.epg(ProviderId(p.id))?.let { EpgTarget(it, agentOf(p.id)) }
                }
            return (configured + fromProviders)
                .groupBy { it.url }
                .map { (_, same) -> same.firstOrNull { it.userAgent != null } ?: same.first() }
        }

        private suspend fun agentOf(providerId: String): String? = headers?.current(providerId)?.value
    }
