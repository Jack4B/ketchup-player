package pl.ketchupplayer.tv.core.model

/** Audycja z przewodnika. Czasy w ms od epoki UTC; [channelId] to id kanału z katalogu (nie id z XMLTV). */
data class EpgProgramme(
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
) {
    fun isOnAt(nowMs: Long) = nowMs in startMs until endMs

    /** Postęp audycji 0..1 w chwili [nowMs]. */
    fun progressAt(nowMs: Long): Float =
        if (endMs <= startMs) 0f else ((nowMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
}
