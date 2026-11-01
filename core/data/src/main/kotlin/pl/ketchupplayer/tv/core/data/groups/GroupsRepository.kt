package pl.ketchupplayer.tv.core.data.groups

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.database.ChannelDao
import pl.ketchupplayer.tv.core.database.VodDao
import javax.inject.Inject

/** Rodzaj listy, w której użytkownik ukrywa grupy. */
enum class GroupKind { TV, MOVIES, SERIES }

/** Grupa albo kategoria z liczbą pozycji i stanem „widoczna”. */
data class GroupOption(
    val name: String,
    val count: Int,
    val visible: Boolean,
)

/**
 * Wybór grup: użytkownik z listy z wieloma krajami zostawia tylko to, co ogląda (np. polskie).
 * Ukryte są zapamiętane po nazwie, więc przeżywają odświeżenie listy dostawcy.
 */
interface GroupsRepository {
    /** Ukryte nazwy dla rodzaju; pusty zbiór = wszystko widoczne. */
    fun hidden(kind: GroupKind): Flow<Set<String>>

    /** Wszystkie grupy rodzaju z liczbami i stanem widoczności. */
    fun options(kind: GroupKind): Flow<List<GroupOption>>

    suspend fun setVisible(
        kind: GroupKind,
        name: String,
        visible: Boolean,
    )

    /** Pokazuje albo ukrywa wszystkie grupy rodzaju naraz (przycisk „Wszystkie”/„Żadne”). */
    suspend fun setAllVisible(
        kind: GroupKind,
        visible: Boolean,
    )
}

class DataStoreGroupsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
        private val channels: ChannelDao,
        private val vod: VodDao,
    ) : GroupsRepository {
        override fun hidden(kind: GroupKind): Flow<Set<String>> = store.data.map { it[key(kind)].orEmpty() }

        override fun options(kind: GroupKind): Flow<List<GroupOption>> {
            val counts =
                when (kind) {
                    GroupKind.TV -> channels.observeGroupCounts("LIVE")
                    GroupKind.MOVIES -> vod.observeCategoryCounts("MOVIE")
                    GroupKind.SERIES -> vod.observeCategoryCounts("SERIES")
                }
            return kotlinx.coroutines.flow.combine(counts, hidden(kind)) { list, hid ->
                list.map { GroupOption(it.name, it.cnt, it.name !in hid) }
            }
        }

        override suspend fun setVisible(
            kind: GroupKind,
            name: String,
            visible: Boolean,
        ) {
            store.edit { p ->
                val cur = p[key(kind)].orEmpty()
                p[key(kind)] = if (visible) cur - name else cur + name
            }
        }

        override suspend fun setAllVisible(
            kind: GroupKind,
            visible: Boolean,
        ) {
            val all = options(kind).first().map { it.name }.toSet()
            store.edit { it[key(kind)] = if (visible) emptySet() else all }
        }

        private fun key(kind: GroupKind) = stringSetPreferencesKey("hidden_groups_${kind.name.lowercase()}")
    }
