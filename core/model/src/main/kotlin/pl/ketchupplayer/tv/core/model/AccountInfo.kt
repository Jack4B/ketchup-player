package pl.ketchupplayer.tv.core.model

/**
 * Stan konta u dostawcy listy (Xtream `user_info`): ile połączeń jest w użyciu, ile wolno, do kiedy ważne.
 * Pola, których panel nie podaje, są null.
 */
data class AccountInfo(
    val active: Boolean,
    val expiresEpochSec: Long?,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val trial: Boolean = false,
) {
    /** Wszystkie dozwolone połączenia zajęte: kolejny odtwarzacz może zostać odrzucony przez serwer. */
    val connectionsFull: Boolean
        get() =
            activeConnections != null &&
                maxConnections != null &&
                maxConnections > 0 &&
                activeConnections >= maxConnections
}
