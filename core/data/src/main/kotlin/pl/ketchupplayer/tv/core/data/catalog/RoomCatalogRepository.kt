package pl.ketchupplayer.tv.core.data.catalog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.ChannelDao
import pl.ketchupplayer.tv.core.database.ChannelFlagDao
import pl.ketchupplayer.tv.core.database.ChannelFlagEntity
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ContentType
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.network.TransferMeter
import pl.ketchupplayer.tv.core.policy.EpgSourcePolicy
import pl.ketchupplayer.tv.provider.api.Capability
import pl.ketchupplayer.tv.provider.xtream.XtreamCredentials
import java.net.URI
import java.util.UUID
import javax.inject.Inject

class RoomCatalogRepository
    @Inject
    constructor(
        private val providerDao: ProviderDao,
        private val channelDao: ChannelDao,
        private val flagDao: ChannelFlagDao,
        private val vodDao: pl.ketchupplayer.tv.core.database.VodDao,
        private val secrets: ProviderSecrets,
        private val factory: ProviderFactory,
        private val clock: Clock,
        private val meter: TransferMeter = TransferMeter(),
    ) : CatalogRepository {
        override val downloadedBytes: kotlinx.coroutines.flow.StateFlow<Long> = meter.bytes

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
            userAgentId: String?,
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
            rememberUserAgent(id, userAgentId)
            return added(id)
        }

        override suspend fun addXtream(
            name: String,
            server: String,
            username: String,
            password: String,
            userAgentId: String?,
        ): AddProviderResult {
            val cleanName = EpgSourcePolicy.validateName(name) ?: return invalid(AddProviderError.INVALID_NAME)
            val normalized = XtreamCredentials.normalizeServer(server) ?: return invalid(AddProviderError.INVALID_URL)
            if (username.isBlank() || password.isBlank()) return invalid(AddProviderError.INVALID_CREDENTIALS)
            val id = ProviderId(UUID.randomUUID().toString())
            secrets.putLogin(id, username.trim(), password)
            providerDao.upsert(
                ProviderEntity(id.value, cleanName, ProviderType.XTREAM.name, normalized, secretRef = secrets.ref(id)),
            )
            rememberUserAgent(id, userAgentId)
            return added(id)
        }

        override suspend fun probeConnection(
            id: ProviderId,
            userAgentId: String,
        ): String {
            val entity = providerDao.get(id.value) ?: return "nie ma takiego dostawcy"
            return factory.probe(
                entity,
                pl.ketchupplayer.tv.core.network.UserAgentProfiles
                    .byId(userAgentId),
            )
        }

        override suspend fun epgUrl(id: ProviderId): String? = secrets.epg(id)

        /** Wybrany w kreatorze User-Agent obowiązuje już od pierwszego pobrania listy. */
        private suspend fun rememberUserAgent(
            id: ProviderId,
            userAgentId: String?,
        ) {
            val profile =
                pl.ketchupplayer.tv.core.network.UserAgentProfiles
                    .byId(userAgentId)
            if (userAgentId != null && profile != pl.ketchupplayer.tv.core.network.UserAgentProfiles.AUTO) {
                factory.remember(id.value, profile)
            }
        }

        private fun invalid(error: AddProviderError) = AddProviderResult.Invalid(error)

        private suspend fun added(id: ProviderId): AddProviderResult =
            refresh(id).fold(
                onSuccess = { AddProviderResult.Added(id, it) },
                onFailure = { AddProviderResult.SavedWithError(id, it.message ?: "Błąd pobierania listy") },
            )

        private val status = kotlinx.coroutines.flow.MutableStateFlow<ImportStatus?>(null)
        override val importStatus: kotlinx.coroutines.flow.StateFlow<ImportStatus?> = status

        /**
         * Importy list idą po jednym: dostawcy IPTV zwykle dopuszczają 1 połączenie na konto, a równoległe pobrania
         * (start aplikacji + dodawanie listy + odświeżanie) kończyły się odmową albo blokadą.
         */
        private val importLock = kotlinx.coroutines.sync.Mutex()

        override suspend fun refresh(id: ProviderId): Result<Int> =
            importLock.withLock {
                meter.reset()
                status.value = ImportStatus(ImportPhase.DOWNLOADING)
                try {
                    doRefresh(id)
                } finally {
                    status.value = null
                }
            }

        private suspend fun doRefresh(id: ProviderId): Result<Int> {
            val entity =
                providerDao.get(id.value) ?: return Result.failure(IllegalStateException("Nie ma takiego dostawcy"))
            val provider =
                factory.create(entity)
                    ?: return Result.failure(IllegalStateException("Brak zapisanych danych dostawcy"))
            val result = loadChannelsSafely(provider)
            val now = clock.nowMs()
            return result.fold(
                onSuccess = { channels ->
                    status.value = ImportStatus(ImportPhase.SAVING_CHANNELS, channels.size)
                    channelDao.replaceAll(id.value, channels.map { it.toEntity() })
                    provider.epgUrl()?.let { secrets.putEpg(id, it) }
                    // Filmy i seriale są dodatkiem: ich błąd (także brak pamięci na dużym katalogu) nie psuje kanałów.
                    val vodNote = importVodSafely(provider, id)
                    providerDao.markRefreshed(id.value, now, vodNote, null)
                    Result.success(channels.size)
                },
                onFailure = { e ->
                    val friendly = friendlyImportError(e.message)
                    providerDao.markRefreshed(id.value, now, friendly, null)
                    Result.failure(IllegalStateException(friendly, e))
                },
            )
        }

        private suspend fun loadChannelsSafely(provider: pl.ketchupplayer.tv.provider.api.IptvProvider) =
            try {
                provider.loadChannels()
            } catch (e: OutOfMemoryError) {
                Result.failure(IllegalStateException("Za mało pamięci na tak dużą listę", e))
            }

        /** Zwraca notatkę dla użytkownika, gdy katalog filmów i seriali trzeba było pominąć; null = bez uwag. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun importVodSafely(
            provider: pl.ketchupplayer.tv.provider.api.IptvProvider,
            id: ProviderId,
        ): String? {
            if (Capability.VOD !in provider.capabilities) return null
            return try {
                provider.loadVod().onSuccess { vod ->
                    status.value = ImportStatus(ImportPhase.SAVING_VOD, vod.items.size + vod.episodes.size)
                    vodDao.replaceAll(
                        id.value,
                        vod.items.map { it.toVodEntity() },
                        vod.episodes.map { it.toEpisodeEntity() },
                    )
                    vodDao.baselineSeen()
                }
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                VOD_SKIPPED
            } catch (e: OutOfMemoryError) {
                VOD_SKIPPED_MEMORY
            }
        }

        override suspend fun remove(id: ProviderId) {
            secrets.clear(id)
            providerDao.delete(id.value)
        }

        override suspend fun channel(id: String): Channel? = channelDao.get(id)?.toModel()

        override fun favoriteIds(): Flow<Set<String>> =
            flagDao.observeAll().map { flags -> flags.filter { it.favorite }.map { it.channelId }.toSet() }

        override fun recentChannels(limit: Int): Flow<List<Channel>> =
            flagDao.observeRecentIds(limit).map { ids -> ids.mapNotNull { channelDao.get(it)?.toModel() } }

        override suspend fun markWatched(channelId: String) {
            val current = flagDao.get(channelId) ?: ChannelFlagEntity(channelId)
            flagDao.upsert(current.copy(lastWatchedMs = clock.nowMs()))
        }

        override suspend fun setFavorite(
            channelId: String,
            favorite: Boolean,
        ) {
            val current = flagDao.get(channelId) ?: ChannelFlagEntity(channelId)
            flagDao.upsert(current.copy(favorite = favorite))
        }

        override suspend fun equivalents(channelId: String): List<Channel> {
            val first = channelDao.get(channelId) ?: return emptyList()
            val others =
                channelDao.sameNamed(first.normName).filter {
                    it.id != first.id &&
                        it.providerId != first.providerId
                }
            return (listOf(first) + others.distinctBy { it.providerId }).map { it.toModel() }
        }

        override suspend fun resolveStream(channelId: String): Result<StreamSpec> {
            val channel =
                channelDao.get(channelId)?.toModel() ?: return Result.failure(IllegalStateException("Nie ma kanału"))
            val provider =
                providerDao.get(channel.providerId.value)
                    ?: return Result.failure(IllegalStateException("Nie ma dostawcy"))
            val impl = factory.create(provider) ?: return Result.failure(IllegalStateException("Brak danych dostawcy"))
            val ua = factory.profile(provider.id).value
            // Wybrany nagłówek obowiązuje też w odtwarzaczu, o ile kanał nie ma własnego (EXTVLCOPT).
            return impl.resolveStream(channel).map { spec ->
                if (spec.userAgent ==
                    null
                ) {
                    spec.copy(userAgent = ua)
                } else {
                    spec
                }
            }
        }

        /** Do bazy trafia tylko schemat, host i port — bez ścieżki i parametrów z danymi logowania. */
        private fun displayUrl(url: String): String =
            runCatching { URI(url) }.getOrNull()?.let { u ->
                val port = if (u.port > 0) ":${u.port}" else ""
                "${u.scheme}://${u.host}$port"
            } ?: "?"

        private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: "?"
    }

private fun pl.ketchupplayer.tv.core.model.VodItem.toVodEntity() =
    pl.ketchupplayer.tv.core.database.VodItemEntity(
        id,
        providerId.value,
        kind.name,
        title,
        normTitle,
        ref,
        poster,
        backdrop,
        year,
        rating,
        genre,
        plot,
        durationMin,
        category,
        addedMs,
    )

private fun pl.ketchupplayer.tv.core.model.Episode.toEpisodeEntity() =
    pl.ketchupplayer.tv.core.database.EpisodeEntity(
        id,
        seriesId,
        season,
        number,
        title,
        ref,
        plot,
        durationMin,
        poster,
        addedMs,
    )

private const val VOD_SKIPPED = "Filmy i seriale pominięte (błąd pobierania katalogu)"
private const val VOD_SKIPPED_MEMORY = "Filmy i seriale pominięte (za duży katalog na tym urządzeniu)"
