package pl.ketchupplayer.tv.core.policy

/**
 * Profil bufora odtwarzania. Wartości `maxSeconds` (15 / 30 / 50 / 90) pochodzą ze starej aplikacji
 * (`BkSmartBufferPolicy.maxSeconds`, 1.8.0.24); klucze `small`/`large` to jej starsze nazwy tych samych profili.
 * Mały bufor = szybsze przełączanie kanałów i krótszy dystans do „na żywo”; duży = odporność na słabe łącze.
 */
enum class BufferProfile(
    val key: String,
    val maxSeconds: Int,
) {
    FAST("fast", FAST_SECONDS),
    STANDARD("standard", STANDARD_SECONDS),
    STABLE("stable", STABLE_SECONDS),
    VERY_LARGE("very_large", VERY_LARGE_SECONDS),
    ;

    companion object {
        /** Nieznany lub brakujący klucz to profil standardowy (jak w starej aplikacji). */
        fun fromKey(key: String?): BufferProfile =
            when (key) {
                "fast", "small" -> FAST
                "stable", "large" -> STABLE
                "very_large" -> VERY_LARGE
                else -> STANDARD
            }
    }
}

private const val FAST_SECONDS = 15
private const val STANDARD_SECONDS = 30
private const val STABLE_SECONDS = 50
private const val VERY_LARGE_SECONDS = 90

/** Czasy dla `DefaultLoadControl` w ms: minimum, maksimum, start odtwarzania i wznowienie po zatrzymaniu. */
data class BufferMs(
    val minMs: Int,
    val maxMs: Int,
    val playbackMs: Int,
    val rebufferMs: Int,
)

object LiveBufferPolicy {
    private const val MS = 1_000

    /** Start po 1,5 s danych (szybkie wejście na kanał), wznowienie po zacięciu po 3 s; minimum to połowa maksimum. */
    private const val PLAYBACK_START_MS = 1_500
    private const val REBUFFER_MS = 3_000

    fun bufferMs(profile: BufferProfile): BufferMs {
        val max = profile.maxSeconds * MS
        return BufferMs(minMs = max / 2, maxMs = max, playbackMs = PLAYBACK_START_MS, rebufferMs = REBUFFER_MS)
    }
}
