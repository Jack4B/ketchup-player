package pl.ketchupplayer.tv.core.data.personalization

import kotlinx.serialization.Serializable
import pl.ketchupplayer.tv.core.model.personalization.BecauseCard
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.FollowSuggestion
import pl.ketchupplayer.tv.core.model.personalization.HomeSnapshot
import pl.ketchupplayer.tv.core.model.personalization.SportCard
import pl.ketchupplayer.tv.core.model.personalization.SportChannel
import pl.ketchupplayer.tv.core.model.personalization.SportEventStatus
import pl.ketchupplayer.tv.core.model.personalization.Team
import pl.ketchupplayer.tv.core.model.personalization.WhyChip

/**
 * Zapisany pas Home. Tylko identyfikatory z katalogu, etykiety i czasy: żadnych adresów strumieni ani danych dostępu.
 * Karty rozwiązuje się do aktualnych pozycji katalogu dopiero przy wyświetlaniu (pozycja, która zniknęła, odpada).
 */
@Serializable
internal data class SnapshotPayload(
    val because: List<Because> = emptyList(),
    val sport: List<Sport> = emptyList(),
    val suggestion: Suggestion? = null,
) {
    @Serializable
    data class Because(
        val itemId: String,
        val why: List<Why> = emptyList(),
        val explore: Boolean = false,
    )

    @Serializable
    data class Why(
        val namespace: String,
        val label: String,
    )

    @Serializable
    data class Sport(
        val eventId: String,
        val title: String,
        val kickoffUtcMs: Long,
        val endUtcMs: Long? = null,
        val replay: Boolean = false,
        val status: String = SportEventStatus.SCHEDULED.name,
        val channels: List<Channel> = emptyList(),
        val teamIds: List<String> = emptyList(),
        val evidenceAtMs: Long = 0,
    )

    @Serializable
    data class Channel(
        val channelId: String,
        val name: String,
        val confidence: Double,
    )

    @Serializable
    data class Suggestion(
        val teams: List<SuggestedTeam>,
        val dates: List<Long>,
    )

    @Serializable
    data class SuggestedTeam(
        val id: String,
        val name: String,
    )

    fun toModel(builtAtMs: Long): HomeSnapshot =
        HomeSnapshot(
            because =
                because.map { card ->
                    BecauseCard(
                        card.itemId,
                        card.why.mapNotNull { w ->
                            namespace(w.namespace)?.let { WhyChip(it, w.label) }
                        },
                        card.explore,
                    )
                },
            sport =
                sport.map { s ->
                    val status =
                        SportEventStatus.entries.firstOrNull { it.name == s.status } ?: SportEventStatus.SCHEDULED
                    SportCard(
                        eventId = s.eventId,
                        title = s.title,
                        kickoffUtcMs = s.kickoffUtcMs,
                        endUtcMs = s.endUtcMs,
                        live = status == SportEventStatus.LIVE,
                        replay = s.replay,
                        statusNote = status,
                        channels = s.channels.map { SportChannel(it.channelId, it.name, it.confidence) },
                        teamIds = s.teamIds,
                        evidenceAtMs = s.evidenceAtMs,
                    )
                },
            suggestion = suggestion?.let { s -> FollowSuggestion(s.teams.map { Team(it.id, it.name) }, s.dates) },
            builtAtMs = builtAtMs,
        )

    companion object {
        fun of(
            because: List<BecauseCard>,
            sport: List<SportCard>,
            suggestion: FollowSuggestion?,
        ) = SnapshotPayload(
            because = because.map { c -> Because(c.itemId, c.why.map { Why(it.namespace.name, it.label) }, c.explore) },
            sport =
                sport.map { s ->
                    Sport(
                        eventId = s.eventId,
                        title = s.title,
                        kickoffUtcMs = s.kickoffUtcMs,
                        endUtcMs = s.endUtcMs,
                        replay = s.replay,
                        status = s.statusNote.name,
                        channels = s.channels.map { Channel(it.channelId, it.channelName, it.confidence) },
                        teamIds = s.teamIds,
                        evidenceAtMs = s.evidenceAtMs,
                    )
                },
            suggestion =
                suggestion?.let { s -> Suggestion(s.teams.map { SuggestedTeam(it.id, it.name) }, s.watchedDatesUtcMs) },
        )

        /** Uszkodzony albo stary zapis = brak pasa (ekran nie pokazuje wtedy niczego zamiast zgadywać). */
        fun decode(
            json: String,
            builtAtMs: Long,
        ): HomeSnapshot =
            try {
                PersonalizationEngine.JSON.decodeFromString(serializer(), json).toModel(builtAtMs)
            } catch (ignored: IllegalArgumentException) {
                // SerializationException dziedziczy po IllegalArgumentException.
                HomeSnapshot()
            }

        private fun namespace(name: String): FeatureNamespace? =
            FeatureNamespace.entries.firstOrNull { it.name == name }
    }
}
