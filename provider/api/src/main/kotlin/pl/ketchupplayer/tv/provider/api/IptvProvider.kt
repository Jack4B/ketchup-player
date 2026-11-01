package pl.ketchupplayer.tv.provider.api

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.model.VodCatalog
import pl.ketchupplayer.tv.core.model.VodItem

enum class Capability { LIVE, VOD, SERIES, CATCHUP, EPG_URL, ACCOUNT_INFO }

/** Błąd dostawcy bez wycieku sekretów w komunikacie (URL-e są redagowane wyżej, w `RedactingTree`). */
sealed class ProviderError(
    message: String,
) : Exception(message) {
    class Network(
        message: String,
    ) : ProviderError(message)

    class BadResponse(
        message: String,
    ) : ProviderError(message)

    class Unauthorized(
        message: String,
    ) : ProviderError(message)
}

/**
 * Kontrakt dostawcy (KONSPEKT §3.3). Implementacje (M3U, Xtream, Stalker) nie znają Room ani UI.
 * Wszystkie metody są `suspend` i nie blokują wątku głównego.
 */
interface IptvProvider {
    val id: ProviderId
    val type: ProviderType
    val capabilities: Set<Capability>

    /** Pobiera listę kanałów/pozycji (cała zawartość listy). */
    suspend fun loadChannels(): Result<List<Channel>>

    /** Rozwiązuje adres odtwarzania (Stalker: create_link; M3U/Xtream: zwykle bez zmian). */
    suspend fun resolveStream(channel: Channel): Result<StreamSpec>

    /** Adres XMLTV dostawcy, jeśli lista go podaje (np. `url-tvg` w M3U). */
    suspend fun epgUrl(): String?

    /** Katalog filmów i seriali (tylko dostawcy z [Capability.VOD] lub [Capability.SERIES]). */
    suspend fun loadVod(): Result<VodCatalog> = Result.failure(ProviderError.BadResponse("Brak filmów i seriali"))

    /** Odcinki serialu (pobierane przy otwarciu szczegółów). */
    suspend fun loadEpisodes(series: VodItem): Result<List<Episode>> =
        Result.failure(ProviderError.BadResponse("Brak odcinków"))

    /** Adres odtwarzania filmu albo odcinka z jego odwołania. */
    suspend fun resolveVod(ref: String): Result<StreamSpec> = Result.failure(ProviderError.BadResponse("Brak adresu"))
}
