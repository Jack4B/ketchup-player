package pl.ketchupplayer.tv.core.data.personalization

import pl.ketchupplayer.tv.core.common.Clock
import pl.ketchupplayer.tv.core.database.PersonalizationDao
import pl.ketchupplayer.tv.core.database.PersonalizationMetaEntity
import pl.ketchupplayer.tv.core.database.VodFeatureSource
import pl.ketchupplayer.tv.core.model.personalization.ItemFeature
import pl.ketchupplayer.tv.core.policy.personalization.ItemMetadata
import pl.ketchupplayer.tv.core.policy.personalization.TmdbMatch
import pl.ketchupplayer.tv.core.policy.personalization.VodFeatures
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indeks cech pozycji VOD (`vod_feature_index`): gatunki, obsada i reżyser wyliczone z metadanych katalogu i TMDB.
 * Przebudowa całości tylko po zmianie katalogu; między nimi dopisywane są pozycje ze świeżo pobranymi metadanymi.
 * Pozycja bez metadanych nie ma wierszy w indeksie (i nie może być rekomendowana „z niczego”).
 */
@Singleton
class FeatureIndexer
    @Inject
    constructor(
        private val dao: PersonalizationDao,
        private val clock: Clock,
    ) {
        /** Doprowadza indeks do stanu katalogu. Zwraca liczbę przeliczonych pozycji. */
        suspend fun sync(): Int {
            val now = clock.nowMs()
            val stamp = "$VERSION:${dao.catalogStamp()}:${dao.vodItemCount()}"
            val done =
                if (dao.meta(GLOBAL, KEY_STAMP) != stamp) {
                    rebuild().also { dao.putMeta(PersonalizationMetaEntity(GLOBAL, KEY_STAMP, stamp, now)) }
                } else {
                    val since = dao.meta(GLOBAL, KEY_SYNC)?.toLongOrNull() ?: 0L
                    store(dao.featureSourcesChangedSince(since))
                }
            dao.putMeta(PersonalizationMetaEntity(GLOBAL, KEY_SYNC, now.toString(), now))
            return done
        }

        private suspend fun rebuild(): Int {
            dao.clearVodFeatures()
            var offset = 0
            var total = 0
            while (true) {
                val page = dao.featureSources(PAGE, offset)
                if (page.isEmpty()) break
                total += store(page, replace = false)
                offset += page.size
            }
            return total
        }

        private suspend fun store(
            sources: List<VodFeatureSource>,
            replace: Boolean = true,
        ): Int {
            sources.chunked(CHUNK).forEach { chunk ->
                if (replace) dao.deleteVodFeatures(chunk.map { it.id })
                val rows = chunk.flatMap { s -> features(s).map { it.toEntity(s.id, VERSION) } }
                if (rows.isNotEmpty()) dao.putVodFeatures(rows)
            }
            return sources.size
        }

        /** Cechy jednej pozycji prosto z aktualnych metadanych (indeks mógł jeszcze nie zobaczyć świeżego opisu). */
        suspend fun featuresOf(itemId: String): List<ItemFeature> = dao.featureSource(itemId)?.let(::features).orEmpty()

        private fun features(s: VodFeatureSource): List<ItemFeature> =
            VodFeatures.extract(
                ItemMetadata(
                    providerGenre = s.genre,
                    providerCast = s.cast,
                    providerCategory = s.category,
                    tmdbGenre = s.tmdbGenre,
                    tmdbCast = s.tmdbCast,
                    tmdbDirector = s.director,
                    tmdbMatch = TmdbMatch.entries.firstOrNull { it.name == s.tmdbMatch },
                ),
            )

        private companion object {
            const val VERSION = 1
            const val PAGE = 2_000
            const val CHUNK = 400
            const val GLOBAL = "_index"
            const val KEY_STAMP = "catalog_stamp"
            const val KEY_SYNC = "synced_at"
        }
    }
