package pl.ketchupplayer.tv.core.data.metadata

import pl.ketchupplayer.tv.core.model.VodItem

/** Uzupełnianie pozycji danymi z zewnętrznych źródeł: tylko puste pola, dane dostawcy listy zostają. */
internal object MetadataMerge {
    fun fill(
        item: VodItem,
        meta: MetaResult,
    ): VodItem =
        item.copy(
            plot = keep(item.plot) ?: meta.overview,
            cast = keep(item.cast) ?: meta.cast,
            genre = keep(item.genre) ?: meta.genre,
            durationMin = item.durationMin?.takeIf { it > 0 } ?: meta.runtimeMin,
            poster = keep(item.poster) ?: meta.poster,
            backdrop = keep(item.backdrop) ?: meta.backdrop,
            rating = item.rating?.takeIf { it > 0.0 } ?: meta.ratings.best,
        )

    private fun keep(value: String?): String? = value?.takeIf { it.isNotBlank() }
}
