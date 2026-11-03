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

    override fun find(
        title: String,
        year: Int?,
        series: Boolean,
    ): FilmRef? = entries[title]

    fun reset() {
        entries = emptyMap()
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [FilmIndexModule::class])
object FakeFilmIndexModule {
    @Provides
    @Singleton
    fun filmIndex(): FilmIndex = FakeFilmIndex
}
