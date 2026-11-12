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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pl.ketchupplayer.tv.core.data.logos.LogoIndex
import pl.ketchupplayer.tv.core.data.network.LegacyUserAgentMigration
import pl.ketchupplayer.tv.core.data.personalization.PersonalizationRepository
import pl.ketchupplayer.tv.core.network.NetworkClients
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class KetchupApp :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var legacyUserAgents: dagger.Lazy<LegacyUserAgentMigration>

    @Inject
    lateinit var personalization: dagger.Lazy<PersonalizationRepository>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Timber.plant(RedactingTree())
        // Wybór User-Agent z dawnego DataStore przenosimy zanim ruszy pierwszy import (krótkie, bez sieci).
        runCatching { runBlocking(Dispatchers.IO) { legacyUserAgents.get().migrate() } }
            .onFailure { Timber.w("Migracja wyboru User-Agent nie powiodła się: %s", it.javaClass.simpleName) }
        // Wbudowana baza logo stacji (iptv-org, ok. 25 KB); błąd odczytu nie może zatrzymać aplikacji.
        runCatching {
            val tsv = assets.open("logos/iptv-org.tsv").bufferedReader().use { it.readText() }
            LogoIndex.install(LogoIndex.parse(tsv))
        }.onFailure { Timber.w(it, "Brak wbudowanej bazy logo") }
        // Personalizacja: domknięcie sesji osieroconych po zabiciu procesu, retencja i świeże pasy. W tle; bez zgody
        // użytkownika nie liczy niczego nowego.
        appScope.launch {
            runCatching { personalization.get().onAppStart() }
                .onFailure { Timber.w("Porządki personalizacji nie powiodły się: %s", it.javaClass.simpleName) }
        }
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
