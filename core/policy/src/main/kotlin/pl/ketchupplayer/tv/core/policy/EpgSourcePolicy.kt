package pl.ketchupplayer.tv.core.policy

import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.ProviderId
import java.net.URI

/** Reguły źródeł EPG: walidacja własnych URL-i i wybór aktywnych źródeł. Czysta JVM. */
object EpgSourcePolicy {
    const val MAX_NAME_LENGTH = 60

    sealed interface UrlCheck {
        data class Valid(
            val normalized: String,
        ) : UrlCheck

        data class Invalid(
            val reason: UrlError,
        ) : UrlCheck
    }

    enum class UrlError { EMPTY, NOT_HTTP, NO_HOST, MALFORMED }

    /** Własne źródło: http lub https (listy IPTV bywają po HTTP); inne schematy odrzucone. */
    fun validateUrl(raw: String): UrlCheck {
        val text = raw.trim()
        if (text.isEmpty()) return UrlCheck.Invalid(UrlError.EMPTY)
        if (text.any { it.isWhitespace() }) return UrlCheck.Invalid(UrlError.MALFORMED)
        val uri = runCatching { URI(text) }.getOrNull() ?: return UrlCheck.Invalid(UrlError.MALFORMED)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return UrlCheck.Invalid(UrlError.NOT_HTTP)
        if (uri.host.isNullOrBlank()) return UrlCheck.Invalid(UrlError.NO_HOST)
        return UrlCheck.Valid(text)
    }

    fun validateName(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

    /** Źródła do użycia dla dostawcy: włączone i bez zawężenia albo zawężone do tego dostawcy. */
    fun activeFor(
        sources: List<EpgSource>,
        providerId: ProviderId?,
    ): List<EpgSource> = sources.filter { it.enabled && (it.providerId == null || it.providerId == providerId) }

    fun isDuplicateUrl(
        sources: List<EpgSource>,
        url: String,
    ): Boolean = sources.any { it.url?.trimEnd('/')?.equals(url.trimEnd('/'), ignoreCase = true) == true }
}
