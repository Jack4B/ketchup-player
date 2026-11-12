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
    /** Listy do wyboru i wybrana (null = wszystkie); tylko w Filmach i Serialach. */
    val playlist: pl.ketchupplayer.tv.core.data.catalog.PlaylistChoice =
        pl.ketchupplayer.tv.core.data.catalog
            .PlaylistChoice(),
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

    /** Wybór listy (null = wszystkie listy). */
    data class SelectPlaylist(
        val id: pl.ketchupplayer.tv.core.model.ProviderId?,
    ) : VodBrowseEvent

    /** Karta weszła na ekran: dociągamy jej oceny z zewnętrznych źródeł (kolejka po kilka naraz). */
    data class Visible(
        val itemId: String,
    ) : VodBrowseEvent
}
