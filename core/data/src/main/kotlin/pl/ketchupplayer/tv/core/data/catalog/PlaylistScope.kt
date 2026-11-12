package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.data.groups.HiddenGroups
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ProviderId

/**
 * Zawęża katalog do wybranej listy i wiąże grupy z listą, z której pochodzą. Bez tego dwie listy z grupą „Sport”
 * zlewały się w jedną grupę, a ich kanały przeplatały się po numerach (1, 1, 2, 2…).
 *
 * - wybrana lista: zostają tylko jej pozycje;
 * - „Wszystkie”: pozycje idą listami (w kolejności list), a w każdej liście zostaje jej własna kolejność;
 * - grupa o nazwie występującej w kilku listach dostaje dopisek z nazwą listy („Sport · Lista B”), więc jest osobną
 *   pozycją w panelu grup; nazwy unikalne zostają bez zmian;
 * - ukryte grupy są sprawdzane dla pary „lista + nazwa”.
 */
object PlaylistScope {
    fun channels(
        all: List<Channel>,
        choice: PlaylistChoice,
        hidden: HiddenGroups,
    ): List<Channel> {
        val visible =
            all.filter { c ->
                (choice.selected == null || c.providerId == choice.selected) &&
                    !hidden.hides(c.providerId.value, c.group)
            }
        val ordered = byPlaylist(visible, choice) { it.providerId }
        val repeated = repeatedNames(ordered, { it.group }, { it.providerId })
        if (repeated.isEmpty()) return ordered
        val names = choice.options.associate { it.id to it.name }
        return ordered.map { c ->
            if (c.group in repeated) c.copy(group = label(c.group.orEmpty(), names[c.providerId])) else c
        }
    }

    fun vod(
        all: List<VodEntry>,
        choice: PlaylistChoice,
        hidden: HiddenGroups,
    ): List<VodEntry> {
        val visible =
            all.filter { e ->
                val item = e.item
                (choice.selected == null || item.providerId == choice.selected) &&
                    !hidden.hides(item.providerId.value, item.category)
            }
        val repeated = repeatedNames(visible, { it.item.category }, { it.item.providerId })
        if (repeated.isEmpty()) return visible
        val names = choice.options.associate { it.id to it.name }
        return visible.map { e ->
            val category = e.item.category
            if (category in repeated) {
                e.copy(item = e.item.copy(category = label(category.orEmpty(), names[e.item.providerId])))
            } else {
                e
            }
        }
    }

    /** Etykieta grupy, której nazwa powtarza się w kilku listach. */
    fun label(
        group: String,
        playlistName: String?,
    ): String = if (playlistName.isNullOrBlank()) group else "$group · $playlistName"

    /** Przy kilku listach pozycje idą listami; sortowanie jest stabilne, więc w liście zostaje jej kolejność. */
    private fun <T> byPlaylist(
        items: List<T>,
        choice: PlaylistChoice,
        provider: (T) -> ProviderId,
    ): List<T> {
        if (choice.selected != null || choice.options.size < 2) return items
        val order = choice.options.withIndex().associate { (i, o) -> o.id to i }
        return items.sortedBy { order[provider(it)] ?: Int.MAX_VALUE }
    }

    private fun <T> repeatedNames(
        items: List<T>,
        name: (T) -> String?,
        provider: (T) -> ProviderId,
    ): Set<String> {
        val owners = HashMap<String, ProviderId>()
        val repeated = HashSet<String>()
        for (item in items) {
            val n = name(item) ?: continue
            val first = owners.getOrPut(n) { provider(item) }
            if (first != provider(item)) repeated += n
        }
        return repeated
    }
}
