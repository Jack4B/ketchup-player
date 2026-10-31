package pl.ketchupplayer.tv.core.data.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.ChannelDao
import pl.ketchupplayer.tv.core.database.ChannelEntity
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ContentType
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.core.policy.EpgSourcePolicy
import pl.ketchupplayer.tv.provider.api.IptvProvider
import pl.ketchupplayer.tv.provider.m3u.M3uProvider
import java.net.URI
import java.util.UUID
import javax.inject.Inject

class RoomCatalogRepository
    @Inject
    constructor(
        private val providerDao: ProviderDao,
        private val channelDao: ChannelDao,
        private val fetcher: TextFetcher,
        private val clock: Clock,
    ) : CatalogRepository {
        override val providers: Flow<List<ProviderInfo>> =
            providerDao.observeAll().map { list ->
                list.map { e ->
                    ProviderInfo(
                        id = ProviderId(e.id),
                        name = e.name,
                        type = ProviderType.valueOf(e.type),
                        urlHost = hostOf(e.url),
                        channelCount = channelDao.count(e.id),
                        lastRefreshMs = e.lastRefreshMs,
                        lastError = e.lastError,
                    )
                }
            }

        override fun liveChannels(): Flow<List<Channel>> =
            channelDao.observeByType(ContentType.LIVE.name).map { list -> list.map { it.toModel() } }

        override fun liveGroups(): Flow<List<String>> = channelDao.observeGroups(ContentType.LIVE.name)

        override suspend fun addM3u(
            name: String,
            url: String,
        ): AddProviderResult {
            val cleanName =
                EpgSourcePolicy.validateName(name) ?: return AddProviderResult.Invalid(AddProviderError.INVALID_NAME)
            val valid =
                EpgSourcePolicy.validateUrl(url) as? EpgSourcePolicy.UrlCheck.Valid
                    ?: return AddProviderResult.Invalid(AddProviderError.INVALID_URL)
            val id = ProviderId(UUID.randomUUID().toString())
            providerDao.upsert(ProviderEntity(id.value, cleanName, ProviderType.M3U.name, valid.normalized))
            return refresh(id).fold(
                onSuccess = { AddProviderResult.Added(id, it) },
                onFailure = { AddProviderResult.SavedWithError(id, it.message ?: "Błąd pobierania listy") },
            )
        }

        override suspend fun refresh(id: ProviderId): Result<Int> {
            val entity =
                providerDao.get(id.value) ?: return Result.failure(IllegalStateException("Nie ma takiego dostawcy"))
            val provider = createProvider(entity)
            val result = provider.loadChannels()
            val now = clock.nowMs()
            return result.fold(
                onSuccess = { channels ->
                    channelDao.replaceAll(id.value, channels.map { it.toEntity() })
                    providerDao.markRefreshed(id.value, now, null, provider.epgUrl())
                    Result.success(channels.size)
                },
                onFailure = { e ->
                    providerDao.markRefreshed(id.value, now, e.message ?: "Błąd", null)
                    Result.failure(e)
                },
            )
        }

        override suspend fun remove(id: ProviderId) = providerDao.delete(id.value)

        override suspend fun channel(id: String): Channel? = channelDao.get(id)?.toModel()

        override suspend fun resolveStream(channelId: String): Result<StreamSpec> {
            val channel =
                channelDao.get(channelId)?.toModel() ?: return Result.failure(IllegalStateException("Nie ma kanału"))
            val provider =
                providerDao.get(channel.providerId.value)
                    ?: return Result.failure(IllegalStateException("Nie ma dostawcy"))
            return createProvider(provider).resolveStream(channel)
        }

        private fun createProvider(e: ProviderEntity): IptvProvider =
            when (ProviderType.valueOf(e.type)) {
                ProviderType.M3U -> M3uProvider(ProviderId(e.id), e.url, fetcher)
                else -> error("Typ ${e.type} nie jest jeszcze obsługiwany")
            }

        private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: "?"
    }

internal fun Channel.toEntity() =
    ChannelEntity(id, providerId.value, name, normName, number, epgId, logo, group, type.name, streamUrl, userAgent)

internal fun ChannelEntity.toModel() =
    Channel(
        id,
        ProviderId(providerId),
        name,
        streamUrl,
        number,
        epgId,
        logo,
        groupName,
        ContentType.valueOf(type),
        userAgent,
    )
