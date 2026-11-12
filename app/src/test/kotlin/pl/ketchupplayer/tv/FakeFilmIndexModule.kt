package pl.ketchupplayer.tv

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import pl.ketchupplayer.tv.core.data.metadata.FilmIndex
import pl.ketchupplayer.tv.core.data.metadata.FilmIndexModule
import pl.ketchupplayer.tv.core.data.metadata.FilmRef
import javax.inject.Singleton

/** Indeks filmów w scenariuszach: pusty, chyba że test wstawi pozycję (hermetycznie, bez prawdziwego pliku z APK). */
object FakeFilmIndex : FilmIndex {
    @Volatile
    var entries: Map<String, FilmRef> = emptyMap()

    /** Prawdziwy plik indeksu z repozytorium (zamiast wpisów): scenariusze sprawdzają całą ścieżkę z assetem. */
    @Volatile
    var real: FilmIndex? = null

    override fun find(
        title: String,
        year: Int?,
        series: Boolean,
    ): FilmRef? = entries[title] ?: real?.find(title, year, series)

    fun reset() {
        entries = emptyMap()
        real = null
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [FilmIndexModule::class])
object FakeFilmIndexModule {
    @Provides
    @Singleton
    fun filmIndex(): FilmIndex = FakeFilmIndex
}
