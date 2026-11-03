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
    val target = File(context.filesDir, "film_index_${assetVersion(context)}.db")
    if (!target.exists()) {
        val tmp = File(context.filesDir, "film_index.tmp")
        context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        // Stare kopie (inna wersja assetu) są zbędne.
        context.filesDir.listFiles { f -> f.name.startsWith("film_index_") }?.forEach { it.delete() }
        tmp.renameTo(target)
    }
    return SqliteFilmIndex(SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY))
}

/**
 * Znacznik wersji kopii: czas ostatniej instalacji aplikacji (nowy APK = nowa kopia assetu). Nie używamy
 * `openFd().length`, bo skompresowany asset (a `.db` jest domyślnie kompresowany) nie daje się otworzyć jako fd.
 */
private fun assetVersion(context: Context): Long =
    context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
