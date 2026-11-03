package pl.ketchupplayer.tv.epg.radar

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.EpgProgramme

/** Pozycja Radaru: audycja na kanale z wynikiem 0..~130 i opcjonalną oceną (dziesiąte części, 0..100). */
data class RadarItem(
    val channel: Channel,
    val programme: EpgProgramme,
    val score: Int,
    val ratingTenths: Int?,
)

/**
 * Buduje listę Radaru dla slotu z kanałów i ich audycji. Wynik: ocena z opisu (jeśli jest) + rodzaj audycji
 * (film, serial, sport, dokument…) + ulubiony kanał; zbyt krótkie audycje i telesprzedaż są odsuwane.
 * Ta sama audycja na kilku kanałach pojawia się raz (najlepiej oceniony kanał). Czysta logika, bez I/O.
 */
object RadarEngine {
    fun build(
        slot: RadarSlot,
        nowMs: Long,
        window: RadarPolicy.Window,
        channels: List<Channel>,
        programmes: List<EpgProgramme>,
        favoriteChannelIds: Set<String> = emptySet(),
    ): List<RadarItem> {
        val byId = channels.associateBy { it.id }
        val scored =
            programmes
                .asSequence()
                .filter { RadarPolicy.inSlot(slot, nowMs, it.startMs, it.endMs, window) }
                .mapNotNull { p -> byId[p.channelId]?.let { ch -> item(ch, p, favoriteChannelIds) } }
                .toList()
        // Ta sama audycja (tytuł + początek) na wielu kanałach: zostaje jedna, najlepsza.
        val unique =
            scored
                .groupBy { Channel.normalizeName(it.programme.title) to it.programme.startMs }
                .values
                .map { group -> group.maxBy { it.score } }
        val ranked = unique.sortedWith(compareByDescending<RadarItem> { it.score }.thenBy { it.programme.startMs })
        val ordered =
            when (slot) {
                RadarSlot.SOON -> ranked.sortedBy { it.programme.startMs }
                RadarSlot.TOP_DAY -> balanced(ranked)
                else -> ranked
            }
        return ordered.take(RadarPolicy.MAX_RESULTS)
    }

    private fun balanced(ranked: List<RadarItem>): List<RadarItem> {
        val head = ranked.take(RadarPolicy.MAX_RESULTS)
        val order =
            RadarPolicy.balancedTopOrder(
                IntArray(head.size) {
                    head[it].score
                },
                IntArray(head.size) { genre(head[it].programme) },
            )
        return order.map { head[it] }
    }

    private fun item(
        channel: Channel,
        p: EpgProgramme,
        favorites: Set<String>,
    ): RadarItem {
        val rating = RadarPolicy.ratingTenths(p.description)
        var score = rating?.let { BASE + it / RATING_DIV } ?: BASE
        score +=
            when (genre(p)) {
                GENRE_FILM -> FILM_BONUS
                GENRE_SERIAL -> SERIAL_BONUS
                GENRE_SPORT, GENRE_DOC -> OTHER_BONUS
                else -> 0
            }
        if (channel.id in favorites) score += FAVORITE_BONUS
        if (p.endMs - p.startMs < SHORT_MS) score -= SHORT_PENALTY
        if (SHOPPING.containsMatchIn(p.title)) score -= SHOPPING_PENALTY
        return RadarItem(channel, p, score, rating)
    }

    /** Numer gatunku do rozrzedzania TOP (1..15; 0 = nieznany). */
    internal fun genre(p: EpgProgramme): Int {
        val text = Channel.normalizeName("${p.category.orEmpty()} ${p.title}")
        return when {
            text.containsAny("serial", "telenowela", "sezon") -> GENRE_SERIAL
            text.containsAny("film", "kino", "dramat", "komedia", "thriller", "horror") -> GENRE_FILM
            text.containsAny("sport", "pilka", "mecz", "liga", "tenis", "formula") -> GENRE_SPORT
            text.containsAny("dokument", "przyroda", "podroz", "historia", "nauka") -> GENRE_DOC
            text.containsAny("wiadomosci", "fakty", "informacj", "publicyst") -> GENRE_NEWS
            text.containsAny("bajk", "dla dzieci", "animowan") -> GENRE_KIDS
            text.containsAny("muzyk", "koncert", "festiwal") -> GENRE_MUSIC
            else -> 0
        }
    }

    private fun String.containsAny(vararg parts: String) = parts.any { it in this }

    private const val BASE = 50
    private const val RATING_DIV = 2
    private const val FILM_BONUS = 12
    private const val SERIAL_BONUS = 8
    private const val OTHER_BONUS = 6
    private const val FAVORITE_BONUS = 10
    private const val SHORT_MS = 15L * 60_000L
    private const val SHORT_PENALTY = 30
    private const val SHOPPING_PENALTY = 40
    private val SHOPPING = Regex("(?i)telesklep|teleshopping|reklam")
    private const val GENRE_FILM = 1
    private const val GENRE_SERIAL = 2
    private const val GENRE_SPORT = 3
    private const val GENRE_DOC = 4
    private const val GENRE_NEWS = 5
    private const val GENRE_KIDS = 6
    private const val GENRE_MUSIC = 7
}
