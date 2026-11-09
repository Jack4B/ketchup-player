package pl.ketchupplayer.tv.core.network

/**
 * Ciało żądania POST w formacie `application/x-www-form-urlencoded` (np. `username=...&password=...`). Zawiera dane
 * dostępu: [toString] nic z niego nie ujawnia, a ciało nie jest nigdzie logowane ani zapisywane.
 */
class FormBody(
    val encoded: String,
) {
    override fun toString(): String = "FormBody(***)"

    override fun equals(other: Any?): Boolean = other is FormBody && other.encoded == encoded

    override fun hashCode(): Int = encoded.hashCode()
}
