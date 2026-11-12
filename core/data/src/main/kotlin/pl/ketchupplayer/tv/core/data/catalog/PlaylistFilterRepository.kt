package pl.ketchupplayer.tv.core.data.catalog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.model.ProviderId
import javax.inject.Inject

/** Ekran, na którym użytkownik wybiera listę (wybór jest zapamiętywany osobno dla każdego). */
enum class PlaylistScreen { TV, MOVIES, SERIES }

/** Lista do wyboru: id i nazwa nadana przez użytkownika. */
data class PlaylistOption(
    val id: ProviderId,
    val name: String,
)

/** Stan wyboru listy na ekranie: dostępne listy i wybrana (null = „Wszystkie listy”). */
data class PlaylistChoice(
    val options: List<PlaylistOption> = emptyList(),
    val selected: ProviderId? = null,
) {
    val selectedName: String? get() = options.firstOrNull { it.id == selected }?.name

    /** Wybór ma sens dopiero przy dwóch listach. */
    val available: Boolean get() = options.size > 1
}

/**
 * Wybór listy na ekranach Na żywo, Filmy i Seriale: „Wszystkie” albo jedna konkretna. Zapamiętany wybór listy, której
 * już nie ma (usunięta), wraca do „Wszystkie”, więc ekran nigdy nie zostaje pusty z powodu starego ustawienia.
 */
interface PlaylistFilterRepository {
    fun choice(screen: PlaylistScreen): Flow<PlaylistChoice>

    suspend fun select(
        screen: PlaylistScreen,
        id: ProviderId?,
    )
}

class DataStorePlaylistFilterRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
        private val providers: ProviderDao,
    ) : PlaylistFilterRepository {
        override fun choice(screen: PlaylistScreen): Flow<PlaylistChoice> =
            combine(store.data, providers.observeAll()) { prefs, list ->
                val options = list.map { PlaylistOption(ProviderId(it.id), it.name) }
                val stored = prefs[key(screen)]
                PlaylistChoice(options, options.firstOrNull { it.id.value == stored }?.id)
            }.distinctUntilChanged()

        override suspend fun select(
            screen: PlaylistScreen,
            id: ProviderId?,
        ) {
            store.edit { if (id == null) it.remove(key(screen)) else it[key(screen)] = id.value }
        }

        private fun key(screen: PlaylistScreen) = stringPreferencesKey("playlist_filter_${screen.name.lowercase()}")
    }
