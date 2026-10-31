package pl.ketchupplayer.tv.core.data.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.ChannelDao
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ContentType
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.policy.EpgSourcePolicy
import pl.ketchupplayer.tv.provider.xtream.XtreamCredentials
import java.net.URI
import java.util.UUID
import javax.inject.Inject

class RoomCatalogRepository
    @Inject
    constructor(
        private val providerDao: ProviderDao,
        private val channelDao: ChannelDao,
        private val secrets: ProviderSecrets,
        private val factory: ProviderFactory,
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
            val cleanName = EpgSourcePolicy.validateName(name) ?: return invalid(AddProviderError.INVALID_NAME)
            val valid =
                EpgSourcePolicy.validateUrl(url) as? EpgSourcePolicy.UrlCheck.Valid
                    ?: return invalid(AddProviderError.INVALID_URL)
            val id = ProviderId(UUID.randomUUID().toString())
            // Pełny adres bywa z danymi logowania (get.php?username=..&password=..): tylko w SecretStore.
            secrets.putUrl(id, valid.normalized)
            providerDao.upsert(
                ProviderEntity(
                    id.value,
                    cleanName,
                    ProviderType.M3U.name,
                    displayUrl(valid.normalized),
                    secretRef = secrets.ref(id),
                ),
            )
            return added(id)
        }

        override suspend fun addXtream(
            name: String,
            server: String,
            username: String,
            password: String,
        ): AddProviderResult {
            val cleanName = EpgSourcePolicy.validateName(name) ?: return invalid(AddProviderError.INVALID_NAME)
            val normalized = XtreamCredentials.normalizeServer(server) ?: return invalid(AddProviderError.INVALID_URL)
            if (username.isBlank() || password.isBlank()) return invalid(AddProviderError.INVALID_CREDENTIALS)
            val id = ProviderId(UUID.randomUUID().toString())
            secrets.putLogin(id, username.trim(), password)
            providerDao.upsert(
                ProviderEntity(id.value, cleanName, ProviderType.XTREAM.name, normalized, secretRef = secrets.ref(id)),
            )
            return added(id)
        }

        override suspend fun epgUrl(id: ProviderId): String? = secrets.epg(id)

        private fun invalid(error: AddProviderError) = AddProviderResult.Invalid(error)

        private suspend fun added(id: ProviderId): AddProviderResult =
            refresh(id).fold(
                onSuccess = { AddProviderResult.Added(id, it) },
                onFailure = { AddProviderResult.SavedWithError(id, it.message ?: "Błąd pobierania listy") },
            )

        override suspend fun refresh(id: ProviderId): Result<Int> {
            val entity =
                providerDao.get(id.value) ?: return Result.failure(IllegalStateException("Nie ma takiego dostawcy"))
            val provider =
                factory.create(entity)
                    ?: return Result.failure(IllegalStateException("Brak zapisanych danych dostawcy"))
            val result = provider.loadChannels()
            val now = clock.nowMs()
            return result.fold(
                onSuccess = { channels ->
                    channelDao.replaceAll(id.value, channels.map { it.toEntity() })
                    provider.epgUrl()?.let { secrets.putEpg(id, it) }
                    providerDao.markRefreshed(id.value, now, null, null)
                    Result.success(channels.size)
                },
                onFailure = { e ->
                    providerDao.markRefreshed(id.value, now, e.message ?: "Błąd", null)
                    Result.failure(e)
                },
            )
        }

        override suspend fun remove(id: ProviderId) {
            secrets.clear(id)
            providerDao.delete(id.value)
        }

        override suspend fun channel(id: String): Channel? = channelDao.get(id)?.toModel()

        override suspend fun resolveStream(channelId: String): Result<StreamSpec> {
            val channel =
                channelDao.get(channelId)?.toModel() ?: return Result.failure(IllegalStateException("Nie ma kanału"))
            val provider =
                providerDao.get(channel.providerId.value)
                    ?: return Result.failure(IllegalStateException("Nie ma dostawcy"))
            val impl = factory.create(provider) ?: return Result.failure(IllegalStateException("Brak danych dostawcy"))
            return impl.resolveStream(channel)
        }

        /** Do bazy trafia tylko schemat, host i port — bez ścieżki i parametrów z danymi logowania. */
        private fun displayUrl(url: String): String =
            runCatching { URI(url) }.getOrNull()?.let { u ->
                val port = if (u.port > 0) ":${u.port}" else ""
                "${u.scheme}://${u.host}$port"
            } ?: "?"

        private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: "?"
    }
