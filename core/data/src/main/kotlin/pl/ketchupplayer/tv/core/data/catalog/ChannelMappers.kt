package pl.ketchupplayer.tv.core.data.catalog

import pl.ketchupplayer.tv.core.database.ChannelEntity
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ContentType
import pl.ketchupplayer.tv.core.model.ProviderId

internal fun Channel.toEntity() =
    ChannelEntity(id, providerId.value, name, normName, number, epgId, logo, group, type.name, streamUrl, userAgent)

internal fun ChannelEntity.toModel() =
    Channel(
        id,
        ProviderId(providerId),
        name,
        streamUrl,
        number,
        epgId,
        logo,
        groupName,
        ContentType.valueOf(type),
        userAgent,
    )
