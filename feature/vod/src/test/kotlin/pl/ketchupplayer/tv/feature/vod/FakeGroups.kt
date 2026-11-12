package pl.ketchupplayer.tv.feature.vod

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.data.catalog.PlaylistChoice
import pl.ketchupplayer.tv.core.data.catalog.PlaylistFilterRepository
import pl.ketchupplayer.tv.core.data.catalog.PlaylistScreen
import pl.ketchupplayer.tv.core.data.groups.GroupKind
import pl.ketchupplayer.tv.core.data.groups.GroupOption
import pl.ketchupplayer.tv.core.data.groups.GroupsRepository
import pl.ketchupplayer.tv.core.data.groups.HiddenGroups
import pl.ketchupplayer.tv.core.model.ProviderId

/**
 * Prosta implementacja w pamięci: nic nie ukryte, dopóki test czegoś nie ukryje. Nazwy podane bez listy działają
 * jak wpisy starego formatu (dla każdej listy), więc dotychczasowe testy nie muszą znać id listy.
 */
internal class FakeGroups(
    initiallyHidden: Set<String> = emptySet(),
) : GroupsRepository {
    val hiddenNames = MutableStateFlow(initiallyHidden)

    override fun hidden(kind: GroupKind): Flow<HiddenGroups> = hiddenNames.map { HiddenGroups(it) }

    override fun options(kind: GroupKind): Flow<List<GroupOption>> = hiddenNames.map { emptyList() }

    override suspend fun setVisible(
        kind: GroupKind,
        providerId: String,
        name: String,
        visible: Boolean,
    ) {
        val key = if (providerId.isEmpty()) name else HiddenGroups.key(providerId, name)
        hiddenNames.value = if (visible) hiddenNames.value - key - name else hiddenNames.value + key
    }

    override suspend fun setAllVisible(
        kind: GroupKind,
        visible: Boolean,
    ) {
        if (visible) hiddenNames.value = emptySet()
    }
}

/** Wybór listy w pamięci (na ekran). */
internal class FakePlaylists(
    options: List<Pair<String, String>> = emptyList(),
) : PlaylistFilterRepository {
    private val all =
        options.map {
            pl.ketchupplayer.tv.core.data.catalog
                .PlaylistOption(ProviderId(it.first), it.second)
        }
    val selected = MutableStateFlow<Map<PlaylistScreen, ProviderId?>>(emptyMap())

    override fun choice(screen: PlaylistScreen): Flow<PlaylistChoice> =
        selected.map { sel -> PlaylistChoice(all, sel[screen]?.takeIf { id -> all.any { it.id == id } }) }

    override suspend fun select(
        screen: PlaylistScreen,
        id: ProviderId?,
    ) {
        selected.value = selected.value + (screen to id)
    }
}
