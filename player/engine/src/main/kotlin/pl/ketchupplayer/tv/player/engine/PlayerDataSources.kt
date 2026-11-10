package pl.ketchupplayer.tv.player.engine

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import pl.ketchupplayer.tv.core.model.StreamSpec

/**
 * Fabryka źródeł danych odtwarzacza (OkHttp + Media3), jedna dla każdego odtwarzania. Tu, a nie w kontrolerze, żeby
 * test przechodził przez to samo źródło danych, którego używa ExoPlayer.
 *
 * Nagłówki: User-Agent listy (gdy wybrany) albo VLC, `Accept` i `Accept-Language` dokłada klient
 * (`NetworkClients.player()`), a `Range`, `Accept-Encoding: identity` i `Icy-MetaData` ustawia Media3.
 * Dodatkowo bezpośredni strumień live `.ts` dostaje na pierwsze żądanie `Range: bytes=0-`, jak u VLC w przechwyceniu.
 * Przewijanie, wznawianie i segmenty HLS/DASH zostają przy zakresach wyliczanych przez Media3.
 */
@UnstableApi
internal object PlayerDataSources {
    private const val RANGE = "Range"
    private const val FROM_START = "bytes=0-"

    fun factory(
        client: OkHttpClient,
        spec: StreamSpec,
    ): DataSource.Factory {
        val http = OkHttpDataSource.Factory(client).apply { spec.userAgent?.let(::setUserAgent) }
        return ResolvingDataSource.Factory(http) { dataSpec -> withInitialRange(dataSpec, spec.url) }
    }

    /**
     * `Range: bytes=0-` tylko dla pierwszego żądania adresu odtwarzanego strumienia `.ts` (pozycja 0, bez długości,
     * bez własnego zakresu). Wszystko inne (HLS, segmenty, przewijanie, ponowne otwarcia od pozycji) bez zmian.
     */
    fun withInitialRange(
        dataSpec: DataSpec,
        streamUrl: String,
    ): DataSpec {
        val first = dataSpec.position == 0L && dataSpec.length == C.LENGTH_UNSET.toLong()
        val eligible = first && dataSpec.uri.toString() == streamUrl && isDirectTs(streamUrl)
        if (!eligible || dataSpec.httpRequestHeaders.containsKey(RANGE)) return dataSpec
        return dataSpec.withAdditionalHeaders(mapOf(RANGE to FROM_START))
    }

    private fun isDirectTs(url: String): Boolean = url.substringBefore('?').substringBefore('#').endsWith(".ts")
}
