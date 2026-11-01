package pl.ketchupplayer.tv.provider.xtream

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

/** Dostawca Xtream Codes. Lista kanałów live; VOD i seriale dojdą z ekranami Filmy/Seriale. */
class XtreamProvider(
    override val id: ProviderId,
    private val credentials: XtreamCredentials,
    private val fetcher: TextFetcher,
) : IptvProvider {
    override val type: ProviderType = ProviderType.XTREAM
    override val capabilities: Set<Capability> =
        setOf(Capability.LIVE, Capability.VOD, Capability.SERIES, Capability.EPG_URL, Capability.ACCOUNT_INFO)

    override suspend fun loadChannels(): Result<List<Channel>> =
        authenticate().fold(onSuccess = { fetchLive() }, onFailure = { Result.failure(it) })

    private suspend fun authenticate(): Result<Unit> {
        val body = fetch(credentials.apiUrl()).getOrElse { return Result.failure(it) }
        val account = XtreamParser.account(body)
        return when {
            account == null -> Result.failure(ProviderError.BadResponse("Nieprawidłowa odpowiedź serwera"))
            !account.authorized -> Result.failure(ProviderError.Unauthorized("Nieprawidłowy login lub hasło"))
            !account.active -> Result.failure(ProviderError.Unauthorized("Konto nieaktywne lub wygasłe"))
            else -> Result.success(Unit)
        }
    }

    private suspend fun fetchLive(): Result<List<Channel>> {
        val categoriesBody = fetch(credentials.apiUrl("get_live_categories")).getOrElse { return Result.failure(it) }
        val streamsBody = fetch(credentials.apiUrl("get_live_streams")).getOrElse { return Result.failure(it) }
        val channels = XtreamParser.liveChannels(id, streamsBody, XtreamParser.categories(categoriesBody).orEmpty())
        return when {
            channels == null -> Result.failure(ProviderError.BadResponse("Nieprawidłowa lista kanałów"))
            channels.isEmpty() -> Result.failure(ProviderError.BadResponse("Konto nie ma żadnych kanałów"))
            else -> Result.success(channels)
        }
    }

    override suspend fun resolveStream(channel: Channel): Result<StreamSpec> {
        val (streamId, ext) =
            channel.streamUrl?.let(XtreamParser::parseLiveRef)
                ?: return Result.failure(ProviderError.BadResponse("Nieznane odwołanie do strumienia"))
        return Result.success(StreamSpec(credentials.liveUrl(streamId, ext)))
    }

    override suspend fun loadVod(): Result<VodCatalog> {
        val movieCats =
            fetch(
                credentials.apiUrl("get_vod_categories"),
            ).getOrNull()?.let(XtreamParser::categories).orEmpty()
        val seriesCats =
            fetch(credentials.apiUrl("get_series_categories")).getOrNull()?.let(XtreamParser::categories).orEmpty()
        // Katalogi mają dziesiątki MB: czytane strumieniowo, element po elemencie (bez całego tekstu i drzewa JSON).
        val movies =
            fetcher
                .fetchStream(credentials.apiUrl("get_vod_streams")) { XtreamVodParser.movies(id, it, movieCats) }
                .getOrElse { return Result.failure(it.toProviderError()) }
        val series =
            fetcher
                .fetchStream(credentials.apiUrl("get_series")) { XtreamVodParser.series(id, it, seriesCats) }
                .getOrElse { return Result.failure(it.toProviderError()) }
        return Result.success(VodCatalog(movies + series))
    }

    override suspend fun loadEpisodes(series: VodItem): Result<List<Episode>> {
        val seriesId = series.ref.removePrefix("xtream:series:")
        val body =
            fetch(credentials.apiUrl("get_series_info", mapOf("series_id" to seriesId))).getOrElse {
                return Result.failure(it)
            }
        return XtreamVodParser.episodes(series, body)?.let { Result.success(it) }
            ?: Result.failure(ProviderError.BadResponse("Nieprawidłowa lista odcinków"))
    }

    override suspend fun resolveVod(ref: String): Result<StreamSpec> {
        val (kind, vodId, ext) =
            XtreamVodParser.parseVodRef(ref) ?: return Result.failure(ProviderError.BadResponse("Nieznane odwołanie"))
        return Result.success(StreamSpec(credentials.vodUrl(kind, vodId, ext)))
    }

    override suspend fun epgUrl(): String = credentials.xmltvUrl()

    private suspend fun fetch(url: String): Result<String> =
        fetcher.fetch(url).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it.toProviderError()) },
        )

    private fun Throwable.toProviderError(): Throwable =
        when (this) {
            is NetworkException.Http ->
                if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) {
                    ProviderError.Unauthorized("Serwer odmówił dostępu (HTTP $code)")
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
