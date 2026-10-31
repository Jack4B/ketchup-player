package pl.ketchupplayer.tv.core.model

@JvmInline
value class ProviderId(val value: String)

enum class ProviderType { M3U, XTREAM, STALKER }
