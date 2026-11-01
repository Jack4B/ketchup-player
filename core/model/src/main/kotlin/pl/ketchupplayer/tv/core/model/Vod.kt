package pl.ketchupplayer.tv.core.model

enum class VodKind { MOVIE, SERIES }

/** Film albo serial z katalogu dostawcy. [ref] to odwołanie do strumienia (filmy) albo do serialu (seriale). */
data class VodItem(
    val id: String,
    val providerId: ProviderId,
    val kind: VodKind,
    val title: String,
    val ref: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val genre: String? = null,
    val plot: String? = null,
    val durationMin: Int? = null,
    val category: String? = null,
    val addedMs: Long? = null,
) {
    val normTitle: String get() = Channel.normalizeName(title)
}

data class Episode(
    val id: String,
    val seriesId: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ref: String,
    val plot: String? = null,
    val durationMin: Int? = null,
    val poster: String? = null,
    val addedMs: Long? = null,
)

/** Wynik pobrania katalogu VOD jednego dostawcy. */
data class VodCatalog(
    val items: List<VodItem>,
    /** Odcinki dostarczone od razu (M3U); Xtream pobiera je na żądanie, więc tu pusto. */
    val episodes: List<Episode> = emptyList(),
)
