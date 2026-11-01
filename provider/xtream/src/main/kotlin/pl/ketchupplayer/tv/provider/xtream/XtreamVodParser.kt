package pl.ketchupplayer.tv.provider.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeToSequence
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind
import pl.ketchupplayer.tv.provider.xtream.XtreamParser.parse
import pl.ketchupplayer.tv.provider.xtream.XtreamParser.str

/** Parser filmów, seriali i odcinków Xtream (osobno od kanałów live). */
@Suppress("TooManyFunctions") // wariant tekstowy (testy) i strumieniowy (produkcja) tych samych parserów
object XtreamVodParser {
    private val streamJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Elementy tablicy JSON czytane leniwie ze strumienia (katalogi mają dziesiątki MB; bez drzewa w pamięci). */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun objects(input: java.io.InputStream): Sequence<JsonObject> =
        streamJson.decodeToSequence(
            input,
            JsonObject.serializer(),
            kotlinx.serialization.json.DecodeSequenceMode.ARRAY_WRAPPED,
        )

    fun movies(
        providerId: ProviderId,
        input: java.io.InputStream,
        categories: Map<String, String>,
    ): List<VodItem> = objects(input).mapNotNull { movie(providerId, it, categories) }.toList()

    fun series(
        providerId: ProviderId,
        input: java.io.InputStream,
        categories: Map<String, String>,
    ): List<VodItem> = objects(input).mapNotNull { seriesItem(providerId, it, categories) }.toList()

    fun movies(
        providerId: ProviderId,
        text: String,
        categories: Map<String, String>,
    ): List<VodItem>? {
        val array = parse(text) as? JsonArray ?: return null
        return array.mapNotNull { (it as? JsonObject)?.let { o -> movie(providerId, o, categories) } }
    }

    private fun movie(
        providerId: ProviderId,
        o: JsonObject,
        categories: Map<String, String>,
    ): VodItem? {
        val id = o.str("stream_id") ?: return null
        val name = o.str("name") ?: return null
        return VodItem(
            id = "${providerId.value}:m$id",
            providerId = providerId,
            kind = VodKind.MOVIE,
            title = cleanTitle(name),
            ref = "xtream:movie:$id.${o.str("container_extension") ?: DEFAULT_VOD_EXT}",
            poster = o.str("stream_icon"),
            year = o.str("year")?.take(YEAR_LEN)?.toIntOrNull() ?: yearIn(name),
            rating = o.str("rating")?.toDoubleOrNull()?.takeIf { it > 0 },
            category = o.str("category_id")?.let { categories[it] },
            addedMs = o.str("added")?.toLongOrNull()?.times(MS),
        )
    }

    fun series(
        providerId: ProviderId,
        text: String,
        categories: Map<String, String>,
    ): List<VodItem>? {
        val array = parse(text) as? JsonArray ?: return null
        return array.mapNotNull { (it as? JsonObject)?.let { o -> seriesItem(providerId, o, categories) } }
    }

    private fun seriesItem(
        providerId: ProviderId,
        o: JsonObject,
        categories: Map<String, String>,
    ): VodItem? {
        val id = o.str("series_id") ?: return null
        val name = o.str("name") ?: return null
        return VodItem(
            id = "${providerId.value}:s$id",
            providerId = providerId,
            kind = VodKind.SERIES,
            title = cleanTitle(name),
            ref = "xtream:series:$id",
            poster = o.str("cover"),
            backdrop = ((o["backdrop_path"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull,
            year =
                (o.str("year") ?: o.str("releaseDate") ?: o.str("release_date"))?.take(YEAR_LEN)?.toIntOrNull()
                    ?: yearIn(name),
            rating = o.str("rating")?.toDoubleOrNull()?.takeIf { it > 0 },
            genre = o.str("genre"),
            plot = o.str("plot"),
            category = o.str("category_id")?.let { categories[it] },
            addedMs = o.str("last_modified")?.toLongOrNull()?.times(MS),
        )
    }

    /** `get_series_info`: sezony -> odcinki. Zwraca null, gdy odpowiedź nie ma sensu. */
    fun episodes(
        series: VodItem,
        text: String,
    ): List<Episode>? {
        val root = parse(text) as? JsonObject ?: return null
        val seasons = root["episodes"] ?: return emptyList()
        val buckets: List<Pair<Int?, JsonArray>> =
            when (seasons) {
                is JsonObject ->
                    seasons.entries.mapNotNull { (k, v) ->
                        (v as? JsonArray)?.let { k.toIntOrNull() to it }
                    }
                is JsonArray -> seasons.mapNotNull { v -> (v as? JsonArray)?.let { null to it } }
                else -> return null
            }
        return buckets
            .flatMap { (seasonKey, list) ->
                list.mapNotNull { it as? JsonObject }.mapNotNull { o ->
                    val epId = o.str("id") ?: return@mapNotNull null
                    val info = o["info"] as? JsonObject
                    val number = o.str("episode_num")?.toIntOrNull() ?: return@mapNotNull null
                    Episode(
                        id = "${series.id}:e$epId",
                        seriesId = series.id,
                        season = o.str("season")?.toIntOrNull() ?: seasonKey ?: 1,
                        number = number,
                        title = o.str("title") ?: "Odcinek $number",
                        ref = "xtream:episode:$epId.${o.str("container_extension") ?: DEFAULT_VOD_EXT}",
                        plot = info?.str("plot"),
                        durationMin = info?.str("duration_secs")?.toIntOrNull()?.div(SECS_PER_MIN),
                        poster = info?.str("movie_image"),
                        addedMs = o.str("added")?.toLongOrNull()?.times(MS),
                    )
                }
            }.sortedWith(compareBy({ it.season }, { it.number }))
    }

    /** `xtream:movie:42.mkv` / `xtream:episode:7.mp4` -> (rodzaj, id, rozszerzenie). */
    fun parseVodRef(ref: String): Triple<String, String, String>? {
        val parts = ref.split(':')
        if (parts.size != REF_PARTS || parts[0] != "xtream") return null
        val dot = parts[2].lastIndexOf('.')
        return if (dot > 0) Triple(parts[1], parts[2].substring(0, dot), parts[2].substring(dot + 1)) else null
    }

    private val yearRegex = Regex("\\((19|20)\\d{2}\\)")
    private val tagRegex = Regex("^(?:[A-Za-z]{2,3}\\s*[-|:]\\s*)")

    /** `PL - Pitbull (2005)` -> `Pitbull`: bez przedrostka języka i roku w nawiasie. */
    private fun cleanTitle(raw: String): String =
        raw
            .replace(tagRegex, "")
            .replace(yearRegex, "")
            .trim()
            .ifEmpty { raw }

    private fun yearIn(raw: String): Int? =
        yearRegex
            .find(raw)
            ?.value
            ?.trim('(', ')')
            ?.toIntOrNull()

    const val DEFAULT_VOD_EXT = "mp4"
    private const val YEAR_LEN = 4
    private const val MS = 1000L
    private const val SECS_PER_MIN = 60
    private const val REF_PARTS = 3
}
