package pl.ketchupplayer.tv.provider.m3u

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ContentType
import pl.ketchupplayer.tv.core.model.Episode
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.VodCatalog
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind
import java.security.MessageDigest

/**
 * Zbiera filmy (`/movie/`) i odcinki (`/series/`) z listy M3U w tym samym przebiegu co kanały.
 * Odcinki `Serial S01E02` łączone są w seriale po znormalizowanej nazwie.
 */
class M3uVodCollector(
    private val providerId: ProviderId,
) {
    private val movies = ArrayList<VodItem>()
    private val series = LinkedHashMap<String, VodItem>()
    private val episodes = ArrayList<Episode>()

    val isEmpty: Boolean get() = movies.isEmpty() && episodes.isEmpty()

    fun add(
        type: ContentType,
        name: String,
        url: String,
        logo: String?,
        group: String?,
    ) {
        when (type) {
            ContentType.VOD -> if (movies.size < MAX_MOVIES) addMovie(name, url, logo, group)
            ContentType.SERIES -> if (episodes.size < MAX_EPISODES) addEpisode(name, url, logo, group)
            ContentType.LIVE -> Unit
        }
    }

    fun build(): VodCatalog = VodCatalog(movies + series.values, episodes)

    private fun addMovie(
        name: String,
        url: String,
        logo: String?,
        group: String?,
    ) {
        movies +=
            VodItem(
                id = "${providerId.value}:m${hash(url)}",
                providerId = providerId,
                kind = VodKind.MOVIE,
                title = VodTitles.clean(name),
                ref = url,
                poster = logo,
                year = VodTitles.year(name),
                category = group,
            )
    }

    private fun addEpisode(
        name: String,
        url: String,
        logo: String?,
        group: String?,
    ) {
        val m = EPISODE.matchEntire(name)
        val show = VodTitles.clean(m?.groupValues?.get(1)?.ifBlank { null } ?: name)
        val key = Channel.normalizeName(show)
        val seriesItem =
            series.getOrPut(key) {
                VodItem(
                    id = "${providerId.value}:s${hash(key)}",
                    providerId = providerId,
                    kind = VodKind.SERIES,
                    title = show,
                    ref = "m3u:series",
                    poster = logo,
                    year = VodTitles.year(name),
                    category = group,
                )
            }
        val season = m?.groupValues?.get(2)?.toIntOrNull() ?: 1
        val number = m?.groupValues?.get(3)?.toIntOrNull() ?: (episodes.size + 1)
        val title =
            m
                ?.groupValues
                ?.get(4)
                ?.trim(' ', '-', '.', '_', ':')
                ?.ifEmpty { null } ?: "Odcinek $number"
        episodes +=
            Episode(
                id = "${seriesItem.id}:e${hash(url)}",
                seriesId = seriesItem.id,
                season = season,
                number = number,
                title = title,
                ref = url,
                poster = logo,
            )
    }

    private fun hash(value: String): String =
        MessageDigest
            .getInstance(
                "SHA-1",
            ).digest(value.toByteArray())
            .take(HASH_BYTES)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        /** Limity chronią słabe boxy (kilkaset MB sterty) przed brakiem pamięci na bardzo dużych listach. */
        const val MAX_MOVIES = 40_000
        const val MAX_EPISODES = 80_000
        const val HASH_BYTES = 6
        val EPISODE = Regex("""^(.*?)[\s._-]*[Ss](\d{1,2})\s*[Ee](\d{1,3})(.*)$""")
    }
}

/** Czyszczenie tytułów z przedrostków języka i roku (wspólne dla list z paneli Xtream). */
internal object VodTitles {
    private val yearRegex = Regex("""\((19|20)\d{2}\)""")
    private val tagRegex = Regex("""^(?:[A-Za-z]{2,3}\s*[-|:]\s*)""")

    fun clean(raw: String): String =
        raw
            .replace(tagRegex, "")
            .replace(yearRegex, "")
            .trim()
            .ifEmpty { raw }

    fun year(raw: String): Int? =
        yearRegex
            .find(raw)
            ?.value
            ?.trim('(', ')')
            ?.toIntOrNull()
}
