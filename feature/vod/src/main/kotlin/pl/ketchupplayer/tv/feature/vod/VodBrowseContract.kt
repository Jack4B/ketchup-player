package pl.ketchupplayer.tv.feature.vod

import pl.ketchupplayer.tv.core.data.vod.VodEntry

/** Źródło siatki: filmy, seriale, Moja lista albo wyszukiwanie we wszystkim. */
enum class VodMode { MOVIES, SERIES, MYLIST, SEARCH }

enum class VodSort { NEWEST, AZ, RATING, YEAR }

/** Kategoria wirtualna „Ostatnio dodane”; rzeczywiste kategorie dostawcy to ich nazwy. */
const val CATEGORY_RECENT = "\u0000recent"

data class VodBrowseUiState(
    val mode: VodMode = VodMode.MOVIES,
    val loading: Boolean = true,
    /** Pozycje po filtrach (kategoria, szukanie) i sortowaniu. */
    val entries: List<VodEntry> = emptyList(),
    val categories: List<Pair<String, Int>> = emptyList(),
    val total: Int = 0,
    /** null = „Wszystkie”, [CATEGORY_RECENT] albo nazwa kategorii. */
    val category: String? = null,
    val query: String = "",
    val sort: VodSort = VodSort.NEWEST,
)

sealed interface VodBrowseEvent {
    data class SelectCategory(
        val category: String?,
    ) : VodBrowseEvent

    data class Search(
        val query: String,
    ) : VodBrowseEvent

    data class Sort(
        val sort: VodSort,
    ) : VodBrowseEvent
}
