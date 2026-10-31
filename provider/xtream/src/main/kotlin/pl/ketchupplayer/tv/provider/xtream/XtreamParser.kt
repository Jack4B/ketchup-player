package pl.ketchupplayer.tv.provider.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ContentType
import pl.ketchupplayer.tv.core.model.ProviderId

data class XtreamAccount(
    val authorized: Boolean,
    val active: Boolean,
    val expiresEpochSec: Long?,
    val maxConnections: Int?,
)

/**
 * Parser odpowiedzi Xtream Codes. Panele różnie typują pola (liczby jako teksty, null jako "null"),
 * stąd tolerancyjne odczyty.
 */
object XtreamParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun account(text: String): XtreamAccount? {
        val user = (parse(text) as? JsonObject)?.get("user_info") as? JsonObject ?: return null
        val auth = user.str("auth")
        return XtreamAccount(
            authorized = auth == "1" || auth.equals("true", ignoreCase = true),
            active = user.str("status").equals("Active", ignoreCase = true),
            expiresEpochSec = user.str("exp_date")?.toLongOrNull(),
            maxConnections = user.str("max_connections")?.toIntOrNull(),
        )
    }

    /** `category_id` -> nazwa. Zwraca null, gdy odpowiedź nie jest tablicą (np. błąd autoryzacji). */
    fun categories(text: String): Map<String, String>? {
        val array = parse(text) as? JsonArray ?: return null
        return array
            .mapNotNull { it as? JsonObject }
            .mapNotNull { o -> o.str("category_id")?.let { id -> id to (o.str("category_name") ?: id) } }
            .toMap()
    }

    /** Kanały live; `streamUrl` to odwołanie bez danych logowania (`xtream:live:<id>.<ext>`). */
    fun liveChannels(
        providerId: ProviderId,
        text: String,
        categories: Map<String, String>,
    ): List<Channel>? {
        val array = parse(text) as? JsonArray ?: return null
        return array.mapNotNull { it as? JsonObject }.mapIndexedNotNull { index, o ->
            val streamId = o.str("stream_id") ?: return@mapIndexedNotNull null
            val name = o.str("name") ?: return@mapIndexedNotNull null
            Channel(
                id = "${providerId.value}:l$streamId",
                providerId = providerId,
                name = name,
                streamUrl = liveRef(streamId, o.str("container_extension") ?: DEFAULT_LIVE_EXT),
                number = o.str("num")?.toIntOrNull() ?: (index + 1),
                epgId = o.str("epg_channel_id"),
                logo = o.str("stream_icon"),
                group = o.str("category_id")?.let { categories[it] },
                type = ContentType.LIVE,
            )
        }
    }

    fun liveRef(
        streamId: String,
        ext: String,
    ) = "xtream:live:$streamId.$ext"

    /** Rozbiera odwołanie z [liveRef]: (id, rozszerzenie) albo null. */
    fun parseLiveRef(ref: String): Pair<String, String>? {
        if (!ref.startsWith("xtream:live:")) return null
        val rest = ref.removePrefix("xtream:live:")
        val dot = rest.lastIndexOf('.')
        return if (dot > 0) rest.substring(0, dot) to rest.substring(dot + 1) else rest to DEFAULT_LIVE_EXT
    }

    private fun parse(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    const val DEFAULT_LIVE_EXT = "ts"
}
