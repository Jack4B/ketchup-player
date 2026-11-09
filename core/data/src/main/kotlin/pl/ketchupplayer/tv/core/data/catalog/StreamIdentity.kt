package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.database.ChannelRef
import java.net.URI

/** Jednoznaczny identyfikator strumienia w obrębie dostawcy: rodzaj (live, movie, series) i numer strumienia. */
internal data class StreamKey(
    val kind: String,
    val number: String,
)

/**
 * Tożsamość strumienia niezależna od trybu importu. Ten sam kanał ma w M3U adres `.../live/U/P/101.ts`, a w API
 * `stream_id` 101 (`xtream:live:101.ts`); identyfikatory w bazie różnią się (skrót nazwy i adresu albo `l101`), więc
 * ulubione, ukryte i postęp oglądania przenosimy po numerze strumienia, nigdy po nazwie.
 */
internal object StreamIdentity {
    private val m3uPath = Regex("""^/(?:(live|movie|series)/)?[^/]+/[^/]+/(\d+)(?:\.[A-Za-z0-9]+)?$""")
    private val apiRef = Regex("""^xtream:(live|movie):(\d+)(?:\.[A-Za-z0-9]+)?$""")

    /** Klucz z adresu M3U (`/live/U/P/ID.ext`, `/movie/...`, `/U/P/ID` dla live) albo odwołania API; inaczej null. */
    fun of(ref: String?): StreamKey? {
        if (ref.isNullOrBlank()) return null
        apiRef.matchEntire(ref)?.let { return StreamKey(it.groupValues[1], it.groupValues[2]) }
        val path = runCatching { URI(ref).rawPath }.getOrNull() ?: return null
        val m = m3uPath.matchEntire(path) ?: return null
        return StreamKey(m.groupValues[1].ifEmpty { "live" }, m.groupValues[2])
    }

    /**
     * Mapa „stare id → nowe id” dla pozycji, które po zmianie trybu importu mają inne id, ale ten sam strumień.
     * Tylko pary jednoznaczne: klucz występuje dokładnie raz po obu stronach (duplikaty to niejednoznaczność, więc
     * zostają bez zmian). Id, które nadal istnieje w nowej liście, nie jest przenoszone.
     */
    fun plan(
        old: List<ChannelRef>,
        new: List<ChannelRef>,
    ): Map<String, String> {
        val newIds = new.mapTo(HashSet()) { it.id }
        val oldByKey = old.mapNotNull { r -> of(r.ref)?.let { it to r.id } }.groupBy({ it.first }, { it.second })
        val newByKey = new.mapNotNull { r -> of(r.ref)?.let { it to r.id } }.groupBy({ it.first }, { it.second })
        val plan = HashMap<String, String>()
        for ((key, oldIds) in oldByKey) {
            val oldId = oldIds.singleOrNull() ?: continue
            val newId = newByKey[key]?.singleOrNull()
            if (newId != null && oldId != newId && oldId !in newIds) plan[oldId] = newId
        }
        return plan
    }
}
