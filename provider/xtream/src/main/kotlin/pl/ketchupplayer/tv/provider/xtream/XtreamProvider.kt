package pl.ketchupplayer.tv.provider.xtream

import pl.ketchupplayer.tv.core.model.AccountInfo
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.RequestMethod
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.model.VodCatalog
import pl.ketchupplayer.tv.core.model.VodInfo
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind
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
    private val method: RequestMethod = RequestMethod.GET,
) : IptvProvider {
    override val type: ProviderType = ProviderType.XTREAM
    override val capabilities: Set<Capability> =
        setOf(Capability.LIVE, Capability.VOD, Capability.SERIES, Capability.EPG_URL, Capability.ACCOUNT_INFO)

    override suspend fun loadChannels(): Result<List<Channel>> =
        authenticate().fold(onSuccess = { fetchLive() }, onFailure = { Result.failure(it) })

    override suspend fun accountInfo(): Result<AccountInfo> {
        val body = fetch(api()).getOrElse { return Result.failure(it) }
        val a =
            XtreamParser.account(body) ?: return Result.failure(ProviderError.BadResponse("Nieprawidłowa odpowiedź"))
        return Result.success(AccountInfo(a.active, a.expiresEpochSec, a.activeConnections, a.maxConnections, a.trial))
    }

    private suspend fun authenticate(): Result<Unit> {
        val body = fetch(api()).getOrElse { return Result.failure(it) }
        val account = XtreamParser.account(body)
        return when {
            account == null -> Result.failure(ProviderError.BadResponse("Nieprawidłowa odpowiedź serwera"))
            !account.authorized -> Result.failure(ProviderError.Unauthorized("Nieprawidłowy login lub hasło"))
            !account.active -> Result.failure(ProviderError.Unauthorized("Konto nieaktywne lub wygasłe"))
            else -> Result.success(Unit)
        }
    }

    private suspend fun fetchLive(): Result<List<Channel>> {
        val categoriesBody = fetch(api("get_live_categories")).getOrElse { return Result.failure(it) }
        val categories = XtreamParser.categories(categoriesBody).orEmpty()
        // Kilkanaście MB JSON: czytane strumieniowo, element po elemencie.
        val channels =
            fetchStream(api("get_live_streams")) { XtreamParser.liveChannels(id, it, categories) }
                .getOrElse { return Result.failure(it.toProviderError()) }
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
        // Kolejność jak u IPTV Extreme: kategorie filmów, filmy, kategorie seriali, seriale.
        val movieCats =
            fetch(api("get_vod_categories")).getOrNull()?.let(XtreamParser::categories).orEmpty()
        // Katalogi mają dziesiątki MB: czytane strumieniowo, element po elemencie (bez całego tekstu i drzewa JSON).
        val movies =
            fetchStream(api("get_vod_streams")) { XtreamVodParser.movies(id, it, movieCats) }
                .getOrElse { return Result.failure(it.toProviderError()) }
        val seriesCats =
            fetch(api("get_series_categories")).getOrNull()?.let(XtreamParser::categories).orEmpty()
        val series =
            fetchStream(api("get_series")) { XtreamVodParser.series(id, it, seriesCats) }
                .getOrElse { return Result.failure(it.toProviderError()) }
        return Result.success(VodCatalog(movies + series))
    }

    override suspend fun loadEpisodes(series: VodItem): Result<List<Episode>> {
        val seriesId = series.ref.removePrefix("xtream:series:")
        val body =
            fetch(api("get_series_info", mapOf("series_id" to seriesId))).getOrElse {
                return Result.failure(it)
            }
        return XtreamVodParser.episodes(series, body)?.let { Result.success(it) }
            ?: Result.failure(ProviderError.BadResponse("Nieprawidłowa lista odcinków"))
    }

    override suspend fun loadVodInfo(item: VodItem): Result<VodInfo> {
        val url =
            if (item.kind == VodKind.SERIES) {
                api("get_series_info", mapOf("series_id" to item.ref.removePrefix("xtream:series:")))
            } else {
                val vodId =
                    XtreamVodParser.parseVodRef(item.ref)?.second
                        ?: return Result.failure(ProviderError.BadResponse("Nieznane odwołanie"))
                api("get_vod_info", mapOf("vod_id" to vodId))
            }
        val body = fetch(url).getOrElse { return Result.failure(it) }
        return XtreamVodParser.info(body)?.let { Result.success(it) }
            ?: Result.failure(ProviderError.BadResponse("Brak opisu u dostawcy"))
    }

    override suspend fun resolveVod(ref: String): Result<StreamSpec> {
        val (kind, vodId, ext) =
            XtreamVodParser.parseVodRef(ref) ?: return Result.failure(ProviderError.BadResponse("Nieznane odwołanie"))
        return Result.success(StreamSpec(credentials.vodUrl(kind, vodId, ext)))
    }

    override suspend fun epgUrl(): String = credentials.xmltvUrl()

    private suspend fun <T> fetchStream(
        request: ApiRequest,
        block: (java.io.InputStream) -> T,
    ): Result<T> =
        request.body?.let { fetcher.fetchStreamWithBody(request.url, it, block) }
            ?: fetcher.fetchStream(request.url, block)

    private fun api(
        action: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): ApiRequest = credentials.apiRequest(method, action, extra)

    private suspend fun fetch(request: ApiRequest): Result<String> =
        (request.body?.let { fetcher.fetchWithBody(request.url, it) } ?: fetcher.fetch(request.url)).fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it.toProviderError()) },
        )

    private fun Throwable.toProviderError(): Throwable =
        when (this) {
            is NetworkException.Http ->
                if (code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN) {
                    ProviderError.Unauthorized(message ?: "Serwer odmówił dostępu (HTTP $code)")
                } else {
                    ProviderError.Network(message ?: "HTTP $code")
                }
            is NetworkException -> ProviderError.Network(message ?: "Błąd sieci")
            else -> this
        }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
