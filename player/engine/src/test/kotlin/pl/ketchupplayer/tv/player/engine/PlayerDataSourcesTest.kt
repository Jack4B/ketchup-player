package pl.ketchupplayer.tv.player.engine

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.network.NetworkClients

/**
 * Nagłówki odtwarzacza przez PRAWDZIWĄ fabrykę źródeł danych ([PlayerDataSources], tę samą, której używa ExoPlayer:
 * `OkHttpDataSource` Media3 na kliencie `NetworkClients.player()`), obserwowane na lokalnym serwerze testowym.
 * Dane fikcyjne.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerDataSourcesTest {
    private val panel = MockWebServer().apply { start() }
    private val streams = MockWebServer().apply { start() }

    @After
    fun stop() {
        panel.shutdown()
        streams.shutdown()
    }

    private fun observed(
        label: String,
        r: RecordedRequest,
    ) = println("OBSERVED $label: " + List(r.headers.size) { r.headers.name(it) + ": " + r.headers.value(it) })

    private fun open(
        url: HttpUrl,
        spec: StreamSpec = StreamSpec(url.toString()),
        dataSpec: DataSpec = DataSpec(Uri.parse(url.toString())),
    ): DataSource {
        val source = PlayerDataSources.factory(NetworkClients.player(), spec).createDataSource()
        source.open(dataSpec)
        source.read(ByteArray(16), 0, 16)
        source.close()
        return source
    }

    @Test
    fun `bezposredni strumien live ts: UA VLC, Accept, Accept-Language i poczatkowy Range`() {
        panel.enqueue(MockResponse().setBody("ts-data"))
        val url = panel.url("/live/user123/pass456/101.ts")
        open(url)
        val r = panel.takeRequest()
        observed("live .ts", r)
        assertEquals("VLC/3.4.3 LibVLC/3.4.31", r.getHeader("User-Agent"))
        assertEquals("*/*", r.getHeader("Accept"))
        assertEquals("en_US", r.getHeader("Accept-Language"))
        assertEquals("bytes=0-", r.getHeader("Range"))
        assertEquals(1, r.headers.values("Range").size)
    }

    @Test
    fun `HLS i segmenty nie dostaja poczatkowego Range`() {
        panel.enqueue(MockResponse().setBody("#EXTM3U"))
        panel.enqueue(MockResponse().setBody("seg"))
        val playlist = panel.url("/live/user123/pass456/101.m3u8")
        val source =
            PlayerDataSources
                .factory(
                    NetworkClients.player(),
                    StreamSpec(playlist.toString()),
                ).createDataSource()
        source.open(DataSpec(Uri.parse(playlist.toString())))
        source.close()
        source.open(DataSpec(Uri.parse(panel.url("/hls/segment_001.ts").toString())))
        source.close()
        val first = panel.takeRequest()
        val segment = panel.takeRequest()
        observed("HLS lista", first)
        observed("HLS segment", segment)
        assertNull(first.getHeader("Range"))
        assertNull("segment .ts to nie strumień z odtwarzanego adresu", segment.getHeader("Range"))
    }

    @Test
    fun `przewijanie i zakresy wyliczone przez Media3 zostaja bez zmian`() {
        val url = panel.url("/live/user123/pass456/101.ts")
        panel.enqueue(MockResponse().setBody("x"))
        panel.enqueue(MockResponse().setBody("x"))
        val spec = StreamSpec(url.toString())
        val uri = Uri.parse(url.toString())
        PlayerDataSources.factory(NetworkClients.player(), spec).createDataSource().apply {
            open(DataSpec(uri, 1_000, C_UNSET))
            close()
            open(DataSpec(uri, 0, 500))
            close()
        }
        val seek = panel.takeRequest()
        val bounded = panel.takeRequest()
        observed("przewijanie", seek)
        assertEquals("bytes=1000-", seek.getHeader("Range"))
        assertEquals("bytes=0-499", bounded.getHeader("Range"))
    }

    @Test
    fun `User-Agent listy zastepuje VLC i nie jest dublowany`() {
        panel.enqueue(MockResponse().setBody("ts"))
        val url = panel.url("/live/user123/pass456/101.ts")
        open(url, StreamSpec(url.toString(), userAgent = "TiviMate/5.0.0 (Android 12)"))
        val r = panel.takeRequest()
        assertEquals(listOf("TiviMate/5.0.0 (Android 12)"), r.headers.values("User-Agent"))
    }

    @Test
    fun `302 na inny host zachowuje User-Agent i Range, bez cudzych naglowkow`() {
        streams.enqueue(MockResponse().setBody("ts"))
        val target =
            streams
                .url("/stream/101.ts")
                .newBuilder()
                .host("127.0.0.1")
                .build()
        panel.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target.toString()))
        val url = panel.url("/live/user123/pass456/101.ts")
        open(url, StreamSpec(url.toString(), userAgent = "AgentListy/1"))
        val first = panel.takeRequest()
        val second = streams.takeRequest()
        observed("302 pierwszy host", first)
        observed("302 drugi host", second)
        listOf(first, second).forEach {
            assertEquals("AgentListy/1", it.getHeader("User-Agent"))
            assertEquals("bytes=0-", it.getHeader("Range"))
            assertEquals("en_US", it.getHeader("Accept-Language"))
        }
        assertEquals("/stream/101.ts", second.path)
        assertNull(second.getHeader("Authorization"))
    }

    private companion object {
        const val C_UNSET = -1L
    }
}
