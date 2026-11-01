package pl.ketchupplayer.tv.provider.m3u

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.model.VodCatalog
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.network.NetworkException
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.provider.api.Capability
import pl.ketchupplayer.tv.provider.api.IptvProvider
import pl.ketchupplayer.tv.provider.api.ProviderError

/** Dostawca z listy M3U pod adresem [url]. Adres EPG (`url-tvg`) jest zapamiętywany po pierwszym pobraniu. */
class M3uProvider(
    override val id: ProviderId,
    private val url: String,
    private val fetcher: TextFetcher,
) : IptvProvider {
    override val type: ProviderType = ProviderType.M3U
    override val capabilities: Set<Capability> =
        setOf(Capability.LIVE, Capability.VOD, Capability.SERIES, Capability.EPG_URL)

    @Volatile
    private var lastEpgUrl: String? = null

    @Volatile
    private var lastVod: VodCatalog? = null

    override suspend fun loadChannels(): Result<List<Channel>> =
        fetcher
            .fetchLines(url) { lines ->
                // Filmy i seriale zbieramy w tym samym przebiegu (lista bywa 30 MB: jedno pobranie).
                val collector = M3uVodCollector(id)
                val playlist = M3uParser.parse(id, lines, liveOnly = true, vod = collector)
                lastVod = if (collector.isEmpty) null else collector.build()
                playlist
            }.fold(
                onSuccess = { playlist ->
                    lastEpgUrl = playlist.epgUrl
                    if (playlist.channels.isEmpty()) {
                        Result.failure(
                            ProviderError.BadResponse(
                                if (playlist.omittedVod > 0) {
                                    "Lista zawiera tylko filmy i seriale (obsługa w kolejnej wersji)"
                                } else {
                                    "Lista nie zawiera żadnych kanałów"
                                },
                            ),
                        )
                    } else {
                        Result.success(playlist.channels)
                    }
                },
                onFailure = { Result.failure(it.toProviderError()) },
            )

    override suspend fun loadVod(): Result<VodCatalog> =
        lastVod?.let { Result.success(it) }
            ?: Result.failure(ProviderError.BadResponse("Lista nie zawiera filmów ani seriali"))

    /** Odcinki z M3U przychodzą razem z katalogiem (patrz [loadVod]); pobierania na żądanie nie ma. */
    override suspend fun loadEpisodes(series: VodItem): Result<List<Episode>> =
        Result.failure(ProviderError.BadResponse("Odcinki są częścią listy"))

    override suspend fun resolveVod(ref: String): Result<StreamSpec> = Result.success(StreamSpec(ref))

    override suspend fun resolveStream(channel: Channel): Result<StreamSpec> =
        channel.streamUrl?.let { Result.success(StreamSpec(it, channel.userAgent)) }
            ?: Result.failure(ProviderError.BadResponse("Kanał bez adresu"))

    override suspend fun epgUrl(): String? = lastEpgUrl

    private fun Throwable.toProviderError(): Throwable =
        when (this) {
            is NetworkException.Http ->
                if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) {
                    ProviderError.Unauthorized("Dostawca odmówił dostępu (HTTP $code)")
                } else {
                    ProviderError.Network("HTTP $code")
                }
            is NetworkException -> ProviderError.Network(message ?: "Błąd sieci")
            else -> this
        }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
