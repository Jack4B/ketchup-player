package pl.ketchupplayer.tv.core.data.personalization

import pl.ketchupplayer.tv.core.database.EventAvailabilityEntity
import pl.ketchupplayer.tv.core.database.PreferenceFeatureEntity
import pl.ketchupplayer.tv.core.database.SportEventEntity
import pl.ketchupplayer.tv.core.database.VodFeatureEntity
import pl.ketchupplayer.tv.core.database.WatchSessionEntity
import pl.ketchupplayer.tv.core.model.personalization.ContentKind
import pl.ketchupplayer.tv.core.model.personalization.EndReason
import pl.ketchupplayer.tv.core.model.personalization.EvidenceClass
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.FeatureWeight
import pl.ketchupplayer.tv.core.model.personalization.ItemFeature
import pl.ketchupplayer.tv.core.model.personalization.Provenance
import pl.ketchupplayer.tv.core.model.personalization.SessionStatus
import pl.ketchupplayer.tv.core.model.personalization.SportEventStatus
import pl.ketchupplayer.tv.core.model.personalization.StartReason
import pl.ketchupplayer.tv.core.model.personalization.WatchSession
import pl.ketchupplayer.tv.core.policy.personalization.EventAvailability
import pl.ketchupplayer.tv.core.policy.personalization.SportEvent

private inline fun <reified E : Enum<E>> parse(
    value: String?,
    fallback: E,
): E = enumValues<E>().firstOrNull { it.name == value } ?: fallback

internal fun WatchSession.toEntity() =
    WatchSessionEntity(
        id = id,
        profileId = profileId,
        providerId = providerId,
        itemId = itemId,
        contentKind = kind.name,
        seriesId = seriesId,
        programmeStartMs = programmeStartMs,
        programmeEndMs = programmeEndMs,
        programmeTitleNorm = programmeTitleNorm,
        startedAtMs = startedAtMs,
        lastEventAtMs = lastEventAtMs,
        endedAtMs = endedAtMs,
        status = status.name,
        activeWatchMs = activeWatchMs,
        wallWatchMs = wallWatchMs,
        pauseMs = pauseMs,
        bufferMs = bufferMs,
        seekCount = seekCount,
        rebufferCount = rebufferCount,
        maxPositionMs = maxPositionMs,
        durationMs = durationMs,
        startReason = startReason.name,
        endReason = endReason?.name,
        evidenceClass = evidence.name,
        sourceVersion = sourceVersion,
        dedupKey = dedupKey,
        appliedStrength = appliedStrength,
        incognito = incognito,
    )

internal fun WatchSessionEntity.toModel() =
    WatchSession(
        id = id,
        profileId = profileId,
        providerId = providerId,
        itemId = itemId,
        kind = parse(contentKind, ContentKind.LIVE),
        seriesId = seriesId,
        programmeStartMs = programmeStartMs,
        programmeEndMs = programmeEndMs,
        programmeTitleNorm = programmeTitleNorm,
        startedAtMs = startedAtMs,
        lastEventAtMs = lastEventAtMs,
        endedAtMs = endedAtMs,
        status = parse(status, SessionStatus.CLOSED),
        activeWatchMs = activeWatchMs,
        wallWatchMs = wallWatchMs,
        pauseMs = pauseMs,
        bufferMs = bufferMs,
        seekCount = seekCount,
        rebufferCount = rebufferCount,
        maxPositionMs = maxPositionMs,
        durationMs = durationMs,
        startReason = parse(startReason, StartReason.USER),
        endReason = endReason?.let { parse(it, EndReason.USER_STOP) },
        evidence = parse(evidenceClass, EvidenceClass.NEUTRAL),
        sourceVersion = sourceVersion,
        dedupKey = dedupKey,
        appliedStrength = appliedStrength,
        incognito = incognito,
    )

internal fun FeatureWeight.toEntity(profileId: String) =
    PreferenceFeatureEntity(
        profileId = profileId,
        namespace = namespace.name,
        featureKey = key,
        displayLabel = label,
        rawWeight = rawWeight,
        decayedWeight = decayedWeight,
        evidence = evidence,
        confidence = confidence,
        lastUpdatedMs = lastUpdatedMs,
        provenance = provenance.name,
    )

internal fun PreferenceFeatureEntity.toModel() =
    FeatureWeight(
        namespace = parse(namespace, FeatureNamespace.GENRE),
        key = featureKey,
        label = displayLabel,
        rawWeight = rawWeight,
        decayedWeight = decayedWeight,
        evidence = evidence,
        confidence = confidence,
        lastUpdatedMs = lastUpdatedMs,
        provenance = parse(provenance, Provenance.PROVIDER),
    )

internal fun ItemFeature.toEntity(
    itemId: String,
    version: Int,
) = VodFeatureEntity(
    itemId = itemId,
    namespace = namespace.name,
    featureKey = key,
    displayLabel = label,
    weightOnItem = weightOnItem,
    provenance = provenance.name,
    confidence = confidence,
    sourceVersion = version,
)

internal fun VodFeatureEntity.toModel() =
    ItemFeature(
        namespace = parse(namespace, FeatureNamespace.GENRE),
        key = featureKey,
        label = displayLabel,
        weightOnItem = weightOnItem,
        provenance = parse(provenance, Provenance.PROVIDER),
        confidence = confidence,
    )

internal fun SportEvent.toEntity() =
    SportEventEntity(
        eventId = eventId,
        competitionKey = competitionKey,
        homeTeamId = teamIds.getOrNull(0),
        awayTeamId = teamIds.getOrNull(1),
        titleRaw = title,
        kickoffUtcMs = kickoffUtcMs,
        endUtcMs = endUtcMs,
        status = status.name,
        isReplay = replay,
        sourceKind = "EPG",
        sourceVersion = 1,
        scheduleUpdatedAtMs = scheduleUpdatedAtMs,
    )

internal fun SportEventEntity.toModel(availability: List<EventAvailabilityEntity>) =
    SportEvent(
        eventId = eventId,
        teamIds = listOfNotNull(homeTeamId, awayTeamId),
        competitionKey = competitionKey,
        title = titleRaw,
        kickoffUtcMs = kickoffUtcMs,
        endUtcMs = endUtcMs ?: kickoffUtcMs,
        status = parse(status, SportEventStatus.SCHEDULED),
        replay = isReplay,
        scheduleUpdatedAtMs = scheduleUpdatedAtMs,
        availability = availability.map { it.toModel() },
    )

internal fun EventAvailability.toEntity(providerId: String?) =
    EventAvailabilityEntity(
        eventId = eventId,
        channelId = channelId,
        providerId = providerId,
        matchMethod = "PROGRAMME_LINK",
        confidence = confidence,
        evidenceAtMs = evidenceAtMs,
        programmeStartMs = programmeStartMs,
        programmeEndMs = programmeEndMs,
        abstain = abstain,
    )

internal fun EventAvailabilityEntity.toModel() =
    EventAvailability(
        eventId = eventId,
        channelId = channelId,
        confidence = confidence,
        abstain = abstain,
        programmeStartMs = programmeStartMs,
        programmeEndMs = programmeEndMs,
        evidenceAtMs = evidenceAtMs,
    )
