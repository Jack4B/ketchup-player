package pl.ketchupplayer.tv.feature.vod

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import pl.ketchupplayer.tv.core.data.groups.GroupKind
import pl.ketchupplayer.tv.core.data.groups.GroupOption
import pl.ketchupplayer.tv.core.data.groups.GroupsRepository

/** Prosta implementacja w pamięci: nic nie ukryte, dopóki test czegoś nie ukryje. */
internal class FakeGroups(
    initiallyHidden: Set<String> = emptySet(),
) : GroupsRepository {
    val hiddenNames = MutableStateFlow(initiallyHidden)

    override fun hidden(kind: GroupKind): Flow<Set<String>> = hiddenNames

    override fun options(kind: GroupKind): Flow<List<GroupOption>> = hiddenNames.map { emptyList() }

    override suspend fun setVisible(
        kind: GroupKind,
        name: String,
        visible: Boolean,
    ) {
        hiddenNames.value = if (visible) hiddenNames.value - name else hiddenNames.value + name
    }

    override suspend fun setAllVisible(
        kind: GroupKind,
        visible: Boolean,
    ) {
        if (visible) hiddenNames.value = emptySet()
    }
}
