package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.model.AccountInfo
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.model.VodCatalog
import pl.ketchupplayer.tv.core.model.VodInfo
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.provider.api.Capability
import pl.ketchupplayer.tv.provider.api.IptvProvider

/**
 * Lista „M3U” z adresu `get.php?username=…&password=…` to w rzeczywistości konto Xtream. Importujemy ją przez
 * `player_api.php` (GET, kategorie, potem strumienie, element po elemencie), tak jak robią to IPTV Extreme, OwnTV
 * i StreamVault. Jeden ogromny `get.php` panel za Cloudflare potrafi odrzucić (504, 520), więc zostaje tylko trybem
 * zapasowym: gdy import przez API się nie uda, pobieramy listę M3U jak dotąd.
 *
 * Katalog filmów i seriali idzie tą samą drogą, którą udał się import kanałów. Odwołania do strumieni rozpoznajemy po
 * przedrostku `xtream:`; reszta należy do M3U.
 */
internal class XtreamFirstProvider(
    private val xtream: IptvProvider,
    private val m3u: IptvProvider,
) : IptvProvider {
    @Volatile private var viaXtream = true

    override val id: ProviderId = m3u.id
    override val type: ProviderType = ProviderType.M3U
    override val capabilities: Set<Capability> = m3u.capabilities + xtream.capabilities

    override suspend fun loadChannels(): Result<List<Channel>> {
        val api = xtream.loadChannels()
        if (api.isSuccess) {
            viaXtream = true
            return api
        }
        viaXtream = false
        val fallback = m3u.loadChannels()
        // Gdy zawiodły obie drogi, użytkownik widzi powód z listy M3U (to ona była dotąd domyślna).
        return fallback
    }

    override suspend fun resolveStream(channel: Channel): Result<StreamSpec> =
        if (channel.streamUrl.orEmpty().startsWith(XTREAM_PREFIX)) {
            xtream.resolveStream(channel)
        } else {
            m3u.resolveStream(channel)
        }

    override suspend fun epgUrl(): String? = xtream.epgUrl() ?: m3u.epgUrl()

    override suspend fun loadVod(): Result<VodCatalog> = (if (viaXtream) xtream else m3u).loadVod()

    override suspend fun loadEpisodes(series: VodItem): Result<List<Episode>> =
        (if (series.ref.startsWith(XTREAM_PREFIX)) xtream else m3u).loadEpisodes(series)

    override suspend fun resolveVod(ref: String): Result<StreamSpec> =
        (if (ref.startsWith(XTREAM_PREFIX)) xtream else m3u).resolveVod(ref)

    override suspend fun loadVodInfo(item: VodItem): Result<VodInfo> =
        (if (item.ref.startsWith(XTREAM_PREFIX)) xtream else m3u).loadVodInfo(item)

    override suspend fun accountInfo(): Result<AccountInfo> = xtream.accountInfo()

    private companion object {
        const val XTREAM_PREFIX = "xtream:"
    }
}
