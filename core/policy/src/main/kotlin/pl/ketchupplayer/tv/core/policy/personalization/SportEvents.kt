package pl.ketchupplayer.tv.core.policy.personalization

import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.personalization.SportCard
import pl.ketchupplayer.tv.core.model.personalization.SportChannel
import pl.ketchupplayer.tv.core.model.personalization.SportEventStatus

/** Audycja EPG w kształcie potrzebnym parserowi (bez adresów). */
data class EpgSlot(
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
)

/** Kanał z katalogu użytkownika. [mapping] = pewność powiązania kanału z jego wpisami EPG. */
data class InventoryChannel(
    val id: String,
    val name: String,
    val hidden: Boolean = false,
    val mapping: Double = SportEvents.EPG_LINK_CONFIDENCE,
)

/** Dowód, że wydarzenie jest na kanale użytkownika. [abstain] = nie wolno wskazać tego kanału. */
data class EventAvailability(
    val eventId: String,
    val channelId: String,
    val confidence: Double,
    val abstain: Boolean,
    val programmeStartMs: Long,
    val programmeEndMs: Long,
    val evidenceAtMs: Long,
)

/** Wydarzenie sportowe wyprowadzone z EPG. Czasy w UTC. */
data class SportEvent(
    val eventId: String,
    val teamIds: List<String>,
    val competitionKey: String?,
    val title: String,
    val kickoffUtcMs: Long,
    val endUtcMs: Long,
    val status: SportEventStatus,
    val replay: Boolean,
    val scheduleUpdatedAtMs: Long,
    val availability: List<EventAvailability> = emptyList(),
)

sealed interface ChannelVerdict {
    /** Żaden kanał nie jest dość pewny: UI pokazuje „kanał niepotwierdzony”. */
    data object Abstain : ChannelVerdict

    data class Known(
        val channels: List<EventAvailability>,
    ) : ChannelVerdict
}

/**
 * Wydarzenia sportowe z EPG dla obserwowanych drużyn. Jedno spotkanie na wielu kanałach to jedna karta; powtórki są
 * osobno i niżej; kanał wskazujemy tylko wtedy, gdy audycja jest przypięta do kanału z aktualnej listy użytkownika.
 */
object SportEvents {
    const val EPG_LINK_CONFIDENCE = 0.95
    private const val SAME_BROADCAST_MS = 45 * PersonalizationParams.MINUTE_MS

    @Suppress("LongParameterList")
    fun build(
        slots: List<EpgSlot>,
        followed: Set<String>,
        index: AliasIndex,
        channels: Map<String, InventoryChannel>,
        nowMs: Long,
        p: PersonalizationParams = PersonalizationParams.DEFAULT,
    ): List<SportEvent> {
        if (followed.isEmpty()) return emptyList()
        val matched =
            slots
                .filter { it.endMs > it.startMs && it.endMs > nowMs && it.startMs <= nowMs + p.sportHorizonMs }
                .mapNotNull { slot ->
                    val parse = TeamParser.parse(slot.title, slot.description, slot.category, index)
                    val usable = !parse.ambiguous && parse.confidence >= p.minTeamConfidence
                    if (usable && parse.teams.any { it in followed }) slot to parse else null
                }
        return matched
            .groupBy { (slot, parse) -> matchKey(slot, parse) }
            .flatMap { (key, group) -> eventsOf(key, group, channels, nowMs) }
            .sortedBy { it.kickoffUtcMs }
    }

    /** Klucz spotkania: drużyny (bez kolejności) i dzień w Warszawie; przy jednej znanej stronie także tytuł. */
    private fun matchKey(
        slot: EpgSlot,
        parse: TeamParse,
    ): String {
        val teams = parse.teams.sorted().joinToString("+")
        val who = if (parse.teams.size >= 2) teams else teams + "~" + Channel.normalizeName(slot.title)
        return who + "@" + WarsawTime.localDate(slot.startMs)
    }

    private fun eventsOf(
        key: String,
        group: List<Pair<EpgSlot, TeamParse>>,
        channels: Map<String, InventoryChannel>,
        nowMs: Long,
    ): List<SportEvent> {
        val live = group.filter { !it.second.replay }
        val kickoff = live.minOfOrNull { it.first.startMs }
        // Ta sama para później tego samego dnia to powtórka, nawet bez słowa „powtórka” w tytule.
        val (first, later) =
            live.partition { kickoff != null && it.first.startMs - kickoff <= SAME_BROADCAST_MS }
        val replays = group.filter { it.second.replay } + later
        return listOfNotNull(
            first.takeIf { it.isNotEmpty() }?.let { event(key, it, false, channels, nowMs) },
            replays.takeIf { it.isNotEmpty() }?.let { event("$key|R", it, true, channels, nowMs) },
        )
    }

    private fun event(
        id: String,
        group: List<Pair<EpgSlot, TeamParse>>,
        replay: Boolean,
        channels: Map<String, InventoryChannel>,
        nowMs: Long,
    ): SportEvent {
        val (lead, parse) = group.minBy { it.first.startMs }
        val kickoff = lead.startMs
        val sameSlot = group.filter { it.first.startMs - kickoff <= SAME_BROADCAST_MS }
        val end = sameSlot.maxOf { it.first.endMs }
        val note = group.firstNotNullOfOrNull { TeamParser.scheduleNote(it.first.title, it.first.description) }
        val status =
            when {
                note == ScheduleNote.CANCELED -> SportEventStatus.CANCELED
                note == ScheduleNote.POSTPONED -> SportEventStatus.POSTPONED
                nowMs >= end -> SportEventStatus.FINISHED
                nowMs >= kickoff -> SportEventStatus.LIVE
                else -> SportEventStatus.SCHEDULED
            }
        val availability =
            sameSlot
                .map { it.first }
                .distinctBy { it.channelId }
                .map { slot ->
                    val channel = channels[slot.channelId]?.takeIf { !it.hidden }
                    EventAvailability(
                        eventId = id,
                        channelId = slot.channelId,
                        confidence = channel?.mapping ?: 0.0,
                        abstain = channel == null,
                        programmeStartMs = slot.startMs,
                        programmeEndMs = slot.endMs,
                        evidenceAtMs = nowMs,
                    )
                }
        return SportEvent(
            eventId = id,
            teamIds = group.flatMap { it.second.teams }.distinct().sorted(),
            competitionKey = parse.competitionKey,
            title = lead.title.trim(),
            kickoffUtcMs = kickoff,
            endUtcMs = end,
            status = status,
            replay = replay,
            scheduleUpdatedAtMs = nowMs,
            availability = availability,
        )
    }

    /**
     * Zestawia nowy stan z poprzednim: wydarzenie, którego już nie ma w EPG, a jeszcze się nie zaczęło, zostaje
     * wycofane (RETRACTED), więc stary termin nie wisi obok nowego.
     */
    fun reconcile(
        previous: List<SportEvent>,
        current: List<SportEvent>,
        nowMs: Long,
    ): List<SportEvent> {
        val ids = current.map { it.eventId }.toSet()
        val retracted =
            previous
                .filter { it.eventId !in ids && it.kickoffUtcMs > nowMs && it.status != SportEventStatus.RETRACTED }
                .map {
                    it.copy(
                        status = SportEventStatus.RETRACTED,
                        scheduleUpdatedAtMs = nowMs,
                        availability = emptyList(),
                    )
                }
        return current + retracted
    }

    /** Kanały, które wolno pokazać: powyżej progu pewności, od najpewniejszego. Inaczej wstrzymanie się. */
    fun channelFor(
        matches: List<EventAvailability>,
        threshold: Double,
    ): ChannelVerdict {
        val ok = matches.filter { !it.abstain && it.confidence >= threshold }.sortedByDescending { it.confidence }
        return if (ok.isEmpty()) ChannelVerdict.Abstain else ChannelVerdict.Known(ok)
    }

    /** Karty pasa: najbliższe transmisje na żywo przed powtórkami; wycofane i zakończone znikają. */
    fun cards(
        events: List<SportEvent>,
        channels: Map<String, InventoryChannel>,
        nowMs: Long,
        p: PersonalizationParams = PersonalizationParams.DEFAULT,
    ): List<SportCard> =
        events
            .filter { it.status != SportEventStatus.RETRACTED && it.status != SportEventStatus.FINISHED }
            .filter { it.endUtcMs > nowMs }
            .sortedWith(compareBy<SportEvent> { it.replay }.thenBy { it.kickoffUtcMs }.thenBy { it.eventId })
            .take(p.sportCards)
            .map { e ->
                val known =
                    when (val verdict = channelFor(e.availability, p.channelThreshold)) {
                        ChannelVerdict.Abstain -> emptyList()
                        is ChannelVerdict.Known -> verdict.channels
                    }
                SportCard(
                    eventId = e.eventId,
                    title = e.title,
                    kickoffUtcMs = e.kickoffUtcMs,
                    endUtcMs = e.endUtcMs,
                    live = e.status == SportEventStatus.LIVE,
                    replay = e.replay,
                    statusNote = e.status,
                    channels =
                        known.mapNotNull { a ->
                            channels[a.channelId]?.let { SportChannel(a.channelId, it.name, a.confidence) }
                        },
                    teamIds = e.teamIds,
                    evidenceAtMs = e.availability.maxOfOrNull { it.evidenceAtMs } ?: e.scheduleUpdatedAtMs,
                )
            }
}
