package pl.ketchupplayer.tv.core.data.groups

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.database.ChannelDao
import pl.ketchupplayer.tv.core.database.ProviderDao
import pl.ketchupplayer.tv.core.database.ProviderNameCount
import pl.ketchupplayer.tv.core.database.VodDao
import javax.inject.Inject

/** Rodzaj listy, w której użytkownik ukrywa grupy. */
enum class GroupKind { TV, MOVIES, SERIES }

/**
 * Grupa albo kategoria jednej listy z liczbą pozycji i stanem „widoczna”. Ta sama nazwa u dwóch dostawców to dwie
 * osobne pozycje ([key] je rozróżnia); [providerName] jest ustawione tylko wtedy, gdy nazwa się powtarza.
 */
data class GroupOption(
    val name: String,
    val count: Int,
    val visible: Boolean,
    val providerId: String = "",
    val providerName: String? = null,
) {
    val key: String get() = HiddenGroups.key(providerId, name)
}

/**
 * Ukryte grupy. Wpis nowego formatu to para „lista + nazwa”; wpis starego formatu (sama nazwa, sprzed wiązania grup
 * z listą) nadal obowiązuje dla każdej listy, żeby wybory użytkownika nie przepadły po aktualizacji.
 */
class HiddenGroups(
    private val entries: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val size: Int get() = entries.size

    fun hides(
        providerId: String,
        name: String?,
    ): Boolean = name != null && (name in entries || key(providerId, name) in entries)

    override fun equals(other: Any?): Boolean = other is HiddenGroups && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    companion object {
        private const val SEPARATOR = '\u001F'

        fun key(
            providerId: String,
            name: String,
        ): String = providerId + SEPARATOR + name

        /** Czy wpis jest w starym formacie (sama nazwa, bez listy). */
        fun isLegacy(entry: String): Boolean = SEPARATOR !in entry
    }
}

/**
 * Wybór grup: użytkownik z listy z wieloma krajami zostawia tylko to, co ogląda (np. polskie). Ukryte są zapamiętane
 * po liście i nazwie, więc przeżywają odświeżenie listy dostawcy, a ukrycie „Sport” w jednej liście nie chowa
 * „Sport” w drugiej.
 */
interface GroupsRepository {
    /** Ukryte grupy rodzaju; puste = wszystko widoczne. */
    fun hidden(kind: GroupKind): Flow<HiddenGroups>

    /** Wszystkie grupy rodzaju (osobno dla każdej listy) z liczbami i stanem widoczności. */
    fun options(kind: GroupKind): Flow<List<GroupOption>>

    suspend fun setVisible(
        kind: GroupKind,
        providerId: String,
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
        private val providers: ProviderDao,
    ) : GroupsRepository {
        override fun hidden(kind: GroupKind): Flow<HiddenGroups> =
            store.data.map { HiddenGroups(it[key(kind)].orEmpty()) }

        private fun counts(kind: GroupKind): Flow<List<ProviderNameCount>> =
            when (kind) {
                GroupKind.TV -> channels.observeProviderGroupCounts("LIVE")
                GroupKind.MOVIES -> vod.observeProviderCategoryCounts("MOVIE")
                GroupKind.SERIES -> vod.observeProviderCategoryCounts("SERIES")
            }

        override fun options(kind: GroupKind): Flow<List<GroupOption>> =
            combine(counts(kind), hidden(kind), providers.observeAll()) { list, hid, all ->
                val names = all.associate { it.id to it.name }
                val order = all.withIndex().associate { (i, p) -> p.id to i }
                val repeated =
                    list
                        .groupBy { it.name }
                        .filterValues { it.size > 1 }
                        .keys
                list
                    .sortedWith(compareBy({ it.name.lowercase() }, { order[it.providerId] ?: Int.MAX_VALUE }))
                    .map {
                        GroupOption(
                            name = it.name,
                            count = it.cnt,
                            visible = !hid.hides(it.providerId, it.name),
                            providerId = it.providerId,
                            providerName = names[it.providerId].takeIf { _ -> it.name in repeated },
                        )
                    }
            }

        override suspend fun setVisible(
            kind: GroupKind,
            providerId: String,
            name: String,
            visible: Boolean,
        ) {
            val owners = counts(kind).first().filter { it.name == name }.map { it.providerId }
            store.edit { p ->
                val cur = p[key(kind)].orEmpty()
                val entry = HiddenGroups.key(providerId, name)
                p[key(kind)] =
                    if (!visible) {
                        cur + entry
                    } else if (name in cur) {
                        // Stary wpis (sama nazwa) chował tę grupę we wszystkich listach: pokazujemy ją tylko w tej
                        // jednej, w pozostałych zostaje ukryta (już jako wpisy „lista + nazwa”).
                        cur - name - entry + owners.filter { it != providerId }.map { HiddenGroups.key(it, name) }
                    } else {
                        cur - entry
                    }
            }
        }

        override suspend fun setAllVisible(
            kind: GroupKind,
            visible: Boolean,
        ) {
            val all = counts(kind).first().map { HiddenGroups.key(it.providerId, it.name) }.toSet()
            store.edit { it[key(kind)] = if (visible) emptySet() else all }
        }

        private fun key(kind: GroupKind) = stringSetPreferencesKey("hidden_groups_${kind.name.lowercase()}")
    }
