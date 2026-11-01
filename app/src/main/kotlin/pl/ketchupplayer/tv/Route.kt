package pl.ketchupplayer.tv

/** Trasy najwyższego poziomu. Kodowane jako String, żeby stos przeżywał obrót i zabicie procesu. */
enum class Destination(
    val key: String,
) {
    LIVE("live"),
    GUIDE("guide"),
    PROVIDERS("providers"),
    EPG_SOURCES("epg"),
    PLAYBACK("playback"),
    APPEARANCE("look"),
}

object Routes {
    const val START = "start"
    private const val PLAYER_PREFIX = "player:"

    fun of(destination: Destination) = destination.key

    fun player(channelId: String) = PLAYER_PREFIX + channelId

    fun playerChannel(route: String): String? =
        route.takeIf { it.startsWith(PLAYER_PREFIX) }?.removePrefix(PLAYER_PREFIX)
}
