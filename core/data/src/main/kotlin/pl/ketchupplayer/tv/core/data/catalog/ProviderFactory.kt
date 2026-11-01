package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.data.network.ProviderHeadersRepository
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.core.network.UserAgentProfile
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import pl.ketchupplayer.tv.provider.api.IptvProvider
import pl.ketchupplayer.tv.provider.m3u.M3uProvider
import pl.ketchupplayer.tv.provider.xtream.XtreamCredentials
import pl.ketchupplayer.tv.provider.xtream.XtreamProvider
import javax.inject.Inject

/** Składa dostawcę z wpisu w bazie i sekretów; zwraca null, gdy brakuje zapisanych danych. */
class ProviderFactory
    @Inject
    constructor(
        private val secrets: ProviderSecrets,
        private val fetcher: TextFetcher,
        private val headers: ProviderHeadersRepository? = null,
    ) {
        /** Wybrany lub zapamiętany nagłówek listy; AUTO = domyślny klienta. */
        suspend fun profile(providerId: String): UserAgentProfile =
            headers?.current(providerId) ?: UserAgentProfiles.AUTO

        suspend fun remember(
            providerId: String,
            profile: UserAgentProfile,
        ) {
            headers?.set(providerId, profile)
        }

        suspend fun create(
            entity: ProviderEntity,
            override: UserAgentProfile? = null,
        ): IptvProvider? {
            val id = ProviderId(entity.id)
            val ua = (override ?: profile(entity.id)).value
            val fetcher = ua?.let { this.fetcher.withUserAgent(it) } ?: this.fetcher
            return when (ProviderType.valueOf(entity.type)) {
                ProviderType.M3U -> secrets.url(id)?.let { M3uProvider(id, it, fetcher) }
                ProviderType.XTREAM ->
                    secrets.login(id)?.let { (user, pass) ->
                        XtreamProvider(id, XtreamCredentials(entity.url, user, pass), fetcher)
                    }
                ProviderType.STALKER -> error("Stalker nie jest jeszcze obsługiwany")
            }
        }
    }
