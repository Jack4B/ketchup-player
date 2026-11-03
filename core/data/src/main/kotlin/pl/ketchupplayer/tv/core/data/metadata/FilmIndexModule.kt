package pl.ketchupplayer.tv.core.data.metadata

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** Wbudowany indeks filmów (asset `film_index.db`): kopiowany raz do pamięci aplikacji i otwierany tylko do odczytu. */
@Module
@InstallIn(SingletonComponent::class)
object FilmIndexModule {
    @Provides
    @Singleton
    fun filmIndex(
        @ApplicationContext context: Context,
    ): FilmIndex = LazyFilmIndex { openAsset(context) }
}

private const val ASSET = "film_index.db"

/** Otwiera indeks dopiero przy pierwszym zapytaniu; brak assetu albo błąd odczytu to [NoFilmIndex]. */
private class LazyFilmIndex(
    private val open: () -> FilmIndex?,
) : FilmIndex {
    private val delegate: FilmIndex by lazy { runCatching { open() }.getOrNull() ?: NoFilmIndex }

    override fun find(
        title: String,
        year: Int?,
        series: Boolean,
    ): FilmRef? = delegate.find(title, year, series)
}

private fun openAsset(context: Context): FilmIndex? {
    val target = File(context.filesDir, "film_index_${assetSize(context)}.db")
    if (!target.exists()) {
        val tmp = File(context.filesDir, "film_index.tmp")
        context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        // Stare kopie (inna wersja assetu) są zbędne.
        context.filesDir.listFiles { f -> f.name.startsWith("film_index_") }?.forEach { it.delete() }
        tmp.renameTo(target)
    }
    return SqliteFilmIndex(SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY))
}

/** Rozmiar assetu jako znacznik wersji kopii: nowy plik w aplikacji daje nową kopię, bez osobnego numeru wersji. */
private fun assetSize(context: Context): Long = context.assets.openFd(ASSET).use { it.length }
