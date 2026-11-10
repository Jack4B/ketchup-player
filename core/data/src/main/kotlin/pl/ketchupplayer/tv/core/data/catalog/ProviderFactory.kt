package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.data.network.ProviderHeadersRepository
import pl.ketchupplayer.tv.core.database.ProviderEntity
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.RequestMethod
import pl.ketchupplayer.tv.core.network.TextFetcher
import pl.ketchupplayer.tv.core.network.UserAgentProfile
import pl.ketchupplayer.tv.core.network.UserAgentProfiles
import pl.ketchupplayer.tv.provider.api.IptvProvider
import pl.ketchupplayer.tv.provider.m3u.M3uProvider
import pl.ketchupplayer.tv.provider.m3u.M3uRequest
import pl.ketchupplayer.tv.provider.xtream.ApiRequest
import pl.ketchupplayer.tv.provider.xtream.XtreamCredentials
import pl.ketchupplayer.tv.provider.xtream.XtreamProvider
import javax.inject.Inject

private const val PROBE_LINES = 2

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

        /**
         * Pojedyncze lekkie zapytanie do dostawcy z wybranym nagłówkiem (test połączenia): zwraca krótki, bezpieczny
         * opis wyniku. Nie pobiera całej listy i nie zapisuje niczego w bazie.
         */
        suspend fun probe(
            entity: ProviderEntity,
            profile: UserAgentProfile,
        ): String {
            val id = ProviderId(entity.id)
            val f = profile.value?.let { fetcher.withUserAgent(it) } ?: fetcher
            val method = RequestMethod.parse(entity.requestMethod)
            val result =
                when (ProviderType.valueOf(entity.type)) {
                    ProviderType.M3U -> secrets.url(id)?.let { probeM3u(f, it, method) }
                    ProviderType.XTREAM ->
                        secrets.login(id)?.let { (user, pass) ->
                            probeApi(
                                f,
                                XtreamCredentials(entity.url, user, pass).apiRequest(method, "get_live_categories"),
                            )
                        }
                    ProviderType.STALKER -> null
                } ?: return "brak danych dostawcy"
            return result.fold(
                onSuccess = { lines -> "OK · " + probeShape(lines.joinToString(" ")) },
                onFailure = { safeErrorText(it, "błąd sieci") },
            )
        }

        /** Konto z adresu get.php sprawdzamy przez `player_api.php` (jak import), nie pobieramy całej listy. */
        private suspend fun probeM3u(
            f: TextFetcher,
            url: String,
            method: RequestMethod,
        ): Result<List<String>> {
            XtreamCredentials.fromPlaylistUrl(url)?.let { return probeApi(f, it.apiRequest(method, null)) }
            if (method != RequestMethod.POST) return f.fetchLines(url) { lines -> lines.take(PROBE_LINES).toList() }
            val req = M3uRequest.post(url)
            return f.fetchLinesWithBody(req.url, requireNotNull(req.body)) { it.take(PROBE_LINES).toList() }
        }

        private suspend fun probeApi(
            f: TextFetcher,
            req: ApiRequest,
        ): Result<List<String>> =
            req.body?.let { body -> f.fetchLinesWithBody(req.url, body) { it.take(1).toList() } }
                ?: f.fetchLines(req.url) { lines -> lines.take(1).toList() }

        suspend fun create(
            entity: ProviderEntity,
            override: UserAgentProfile? = null,
        ): IptvProvider? {
            val id = ProviderId(entity.id)
            val ua = (override ?: profile(entity.id)).value
            val method = RequestMethod.parse(entity.requestMethod)
            val fetcher = ua?.let { this.fetcher.withUserAgent(it) } ?: this.fetcher
            return when (ProviderType.valueOf(entity.type)) {
                ProviderType.M3U ->
                    secrets.url(id)?.let { url ->
                        val m3u = M3uProvider(id, url, fetcher, method)
                        // get.php z loginem to konto Xtream: najpierw player_api.php, M3U zostaje trybem zapasowym.
                        XtreamCredentials.fromPlaylistUrl(url)?.let { creds ->
                            XtreamFirstProvider(XtreamProvider(id, creds, fetcher, method), m3u)
                        } ?: m3u
                    }
                ProviderType.XTREAM ->
                    secrets.login(id)?.let { (user, pass) ->
                        XtreamProvider(id, XtreamCredentials(entity.url, user, pass), fetcher, method)
                    }
                ProviderType.STALKER -> error("Stalker nie jest jeszcze obsługiwany")
            }
        }
    }

/**
 * Opis kształtu odpowiedzi próbnej (M3U / JSON / inne) zamiast przepisywania jej treści: treść serwera jest nieufna i
 * może zawierać dane dostępu w formie, której `Redactor` nie rozpozna (issue #12).
 */
internal fun probeShape(text: String): String {
    val t = text.trimStart()
    return when {
        t.startsWith("#EXTM3U", ignoreCase = true) -> "lista M3U (#EXTM3U)"
        t.startsWith("{") || t.startsWith("[") -> "odpowiedź JSON"
        t.isEmpty() -> "pusta odpowiedź"
        else -> "odpowiedź w innym formacie"
    }
}
