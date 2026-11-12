package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.ItemFeature
import pl.ketchupplayer.tv.core.model.personalization.Provenance
import kotlin.math.sqrt

/** Skąd TMDB wziął dopasowanie: po znanym identyfikatorze (pewne) albo po wyszukiwaniu tytułu. */
enum class TmdbMatch { ID, SEARCH_WITH_YEAR, SEARCH }

/** Metadane jednej pozycji katalogu, z których wolno budować cechy. Puste pole = brak cechy, nigdy zgadywanie. */
data class ItemMetadata(
    val providerGenre: String? = null,
    val providerCast: String? = null,
    /** Kategoria z listy dostawcy (np. „PL | Kryminał”); liczy się tylko, gdy zawiera znaną nazwę gatunku. */
    val providerCategory: String? = null,
    val tmdbGenre: String? = null,
    val tmdbCast: String? = null,
    val tmdbDirector: String? = null,
    val tmdbMatch: TmdbMatch? = null,
)

/**
 * Cechy pozycji katalogu wyłącznie z metadanych: pól dostawcy i TMDB. Tytuł nie jest źródłem gatunku ani reżysera.
 * Pewność: pola dostawcy 0.6, TMDB po identyfikatorze 0.9, po wyszukiwaniu z rokiem 0.6; wyszukiwanie bez roku jest
 * poniżej progu i nie daje cech.
 */
object VodFeatures {
    const val PROVIDER_CONFIDENCE = 0.6
    const val CATEGORY_CONFIDENCE = 0.5
    const val TMDB_ID_CONFIDENCE = 0.9
    const val TMDB_SEARCH_CONFIDENCE = 0.6

    fun key(
        namespace: FeatureNamespace,
        key: String,
    ): String = namespace.name + ":" + key

    fun fullKey(f: ItemFeature): String = key(f.namespace, f.key)

    fun tmdbConfidence(match: TmdbMatch?): Double? =
        when (match) {
            TmdbMatch.ID -> TMDB_ID_CONFIDENCE
            TmdbMatch.SEARCH_WITH_YEAR -> TMDB_SEARCH_CONFIDENCE
            TmdbMatch.SEARCH, null -> null
        }

    fun extract(
        meta: ItemMetadata,
        p: PersonalizationParams = PersonalizationParams.DEFAULT,
    ): List<ItemFeature> {
        val tmdb = tmdbConfidence(meta.tmdbMatch)
        val out = LinkedHashMap<String, ItemFeature>()

        fun add(f: ItemFeature) {
            val k = fullKey(f)
            val old = out[k]
            if (old == null || f.confidence > old.confidence) out[k] = f
        }
        genres(meta.providerGenre, Provenance.PROVIDER, PROVIDER_CONFIDENCE).forEach(::add)
        // Kategoria dostawcy bywa jedynym opisem pozycji. Bierzemy z niej wyłącznie znane nazwy gatunków
        // („PL | Kryminał” tak, „Netflix 4K” nie) i tylko gdy pole gatunku jest puste.
        if (out.isEmpty()) categoryGenres(meta.providerCategory).forEach(::add)
        people(meta.providerCast, FeatureNamespace.CAST, Provenance.PROVIDER, PROVIDER_CONFIDENCE, p.castTopN)
            .forEach(::add)
        if (tmdb != null) {
            genres(meta.tmdbGenre, Provenance.TMDB, tmdb).forEach(::add)
            people(meta.tmdbCast, FeatureNamespace.CAST, Provenance.TMDB, tmdb, p.castTopN).forEach(::add)
            people(meta.tmdbDirector, FeatureNamespace.DIRECTOR, Provenance.TMDB, tmdb, MAX_DIRECTORS).forEach(::add)
        }
        return out.values.toList()
    }

    private fun genres(
        raw: String?,
        provenance: Provenance,
        confidence: Double,
    ): List<ItemFeature> {
        val labels = split(raw).mapNotNull { GenreDictionary.resolve(it) }.distinctBy { it.key }
        if (labels.isEmpty()) return emptyList()
        val share = 1.0 / sqrt(labels.size.toDouble())
        return labels.map { ItemFeature(FeatureNamespace.GENRE, it.key, it.label, share, provenance, confidence) }
    }

    private fun categoryGenres(raw: String?): List<ItemFeature> {
        val labels =
            raw
                .orEmpty()
                .split('|', '/', ':', ',', ';', '-', '•')
                .mapNotNull { GenreDictionary.known(it) }
                .distinctBy { it.key }
        if (labels.isEmpty()) return emptyList()
        val share = 1.0 / sqrt(labels.size.toDouble())
        return labels.map {
            ItemFeature(FeatureNamespace.GENRE, it.key, it.label, share, Provenance.PROVIDER, CATEGORY_CONFIDENCE)
        }
    }

    private fun people(
        raw: String?,
        namespace: FeatureNamespace,
        provenance: Provenance,
        confidence: Double,
        limit: Int,
    ): List<ItemFeature> {
        val names =
            split(raw)
                .map { it to Channel.normalizeName(it) }
                .filter { (_, norm) -> norm.length >= MIN_NAME }
                .distinctBy { it.second }
                .take(limit)
        if (names.isEmpty()) return emptyList()
        val share = if (namespace == FeatureNamespace.DIRECTOR) 1.0 else 1.0 / sqrt(names.size.toDouble())
        return names.map { (label, norm) -> ItemFeature(namespace, norm, label, share, provenance, confidence) }
    }

    private fun split(raw: String?): List<String> =
        raw
            .orEmpty()
            .split(',', '/', '|', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private const val MIN_NAME = 3
    private const val MAX_DIRECTORS = 2
}

/** Etykieta gatunku sprowadzona do wspólnego klucza („Akcja”, „Action” i „akcji” to jedno). */
data class GenreLabel(
    val key: String,
    val label: String,
)

/**
 * Słownik gatunków PL/EN. Nieznana etykieta zostaje sobą (klucz ze znormalizowanej nazwy dostawcy): niczego nie
 * dopisujemy i nie łączymy na oko, np. „Melodramat” nie staje się „Dramatem”.
 */
object GenreDictionary {
    fun resolve(raw: String): GenreLabel? {
        val norm = Channel.normalizeName(raw)
        if (norm.length < MIN_LABEL || norm.all { it.isDigit() || it == ' ' }) return null
        val key =
            SYNONYMS[norm] ?: return GenreLabel(norm.replace(' ', '_'), raw.trim().replaceFirstChar { it.titlecase() })
        return GenreLabel(key, LABELS.getValue(key))
    }

    /** Tylko gatunek ze słownika (dla kategorii dostawcy): nieznana etykieta daje null zamiast nowego „gatunku”. */
    fun known(raw: String): GenreLabel? {
        val norm = Channel.normalizeName(raw)
        val bare = CATEGORY_PREFIXES.fold(norm) { acc, prefix -> acc.removePrefix(prefix) }.trim()
        val key = SYNONYMS[norm] ?: SYNONYMS[bare] ?: return null
        return GenreLabel(key, LABELS.getValue(key))
    }

    private val CATEGORY_PREFIXES = listOf("filmy ", "film ", "seriale ", "serial ", "vod ", "kino ")
    private const val MIN_LABEL = 3

    private val LABELS =
        mapOf(
            "action" to "Akcja",
            "adventure" to "Przygodowy",
            "animation" to "Animacja",
            "comedy" to "Komedia",
            "crime" to "Kryminał",
            "documentary" to "Dokumentalny",
            "drama" to "Dramat",
            "family" to "Familijny",
            "fantasy" to "Fantasy",
            "history" to "Historyczny",
            "horror" to "Horror",
            "music" to "Muzyczny",
            "mystery" to "Tajemnica",
            "romance" to "Romans",
            "scifi" to "Science fiction",
            "thriller" to "Thriller",
            "war" to "Wojenny",
            "western" to "Western",
            "biography" to "Biograficzny",
            "sport" to "Sportowy",
            "kids" to "Dla dzieci",
            "reality" to "Reality show",
        )

    private val SYNONYMS: Map<String, String> =
        buildMap {
            fun all(
                key: String,
                vararg names: String,
            ) {
                names.forEach { name -> this[name] = key }
            }
            all("action", "action", "akcja", "akcji", "film akcji", "sensacyjny", "sensacja", "action adventure")
            all("adventure", "adventure", "przygodowy", "przygoda", "przygodowe")
            all("animation", "animation", "animacja", "animowany", "animowane", "anime")
            all("comedy", "comedy", "komedia", "komedie", "komediowy")
            all("crime", "crime", "kryminal", "kryminalny", "kryminaly")
            all("documentary", "documentary", "dokument", "dokumentalny", "dokumentalne")
            all("drama", "drama", "dramat", "dramaty", "obyczajowy")
            all("family", "family", "familijny", "familijne", "rodzinny")
            all("fantasy", "fantasy")
            all("history", "history", "historyczny", "historyczne", "historia")
            all("horror", "horror", "horrory")
            all("music", "music", "muzyczny", "muzyka", "musical")
            all("mystery", "mystery", "tajemnica")
            all("romance", "romance", "romans", "romantyczny", "romanse")
            all("scifi", "science fiction", "sci fi", "scifi", "sci fi fantasy", "fantastyka naukowa")
            all("thriller", "thriller", "thrillery", "dreszczowiec")
            all("war", "war", "wojenny", "wojenne", "war politics")
            all("western", "western", "westerny")
            all("biography", "biography", "biograficzny", "biografia")
            all("sport", "sport", "sportowy")
            all("kids", "kids", "dla dzieci", "dzieci", "bajki", "bajka")
            all("reality", "reality", "reality show")
        }
}
