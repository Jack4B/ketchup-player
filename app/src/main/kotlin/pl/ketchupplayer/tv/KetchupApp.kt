package pl.ketchupplayer.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import pl.ketchupplayer.tv.core.network.NetworkClients
import timber.log.Timber

@HiltAndroidApp
class KetchupApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(RedactingTree())
    }

    /**
     * Wspólny ładowacz obrazów (logo kanałów, plakaty, tła): przez nasz klient HTTP (dopuszcza http, ten sam
     * nagłówek co listy), z cache dyskowym, żeby logo nie pobierały się przy każdym wejściu w Na żywo.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { NetworkClients.stream() })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_PERCENT).build() }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(DISK_BYTES)
                    .build()
            }.crossfade(CROSSFADE_MS)
            .build()

    private companion object {
        const val MEMORY_PERCENT = 0.15
        const val DISK_BYTES = 150L * 1024 * 1024
        const val CROSSFADE_MS = 120
    }
}

private fun java.io.File.toOkioPath() = okio.Path.Companion.run { this@toOkioPath.absolutePath.toPath() }
