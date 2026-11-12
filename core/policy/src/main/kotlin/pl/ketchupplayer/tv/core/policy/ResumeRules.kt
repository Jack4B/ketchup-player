package pl.ketchupplayer.tv.core.policy

/**
 * Reguły wznowienia filmu/odcinka. Progi ze starej aplikacji: `CinemaResumePolicy.resumePosition` (poniżej 1 s
 * lub od 97% nie proponujemy wznowienia) i `SeriesEpisodePolicy.watchBadge` („kontynuuj” od 30 s do 95%).
 */
object ResumeRules {
    private const val MIN_RESUME_MS = 1_000L
    private const val DONE_PERCENT = 97
    private const val CONTINUE_FROM_MS = 30_000L
    private const val CONTINUE_UNTIL_PERCENT = 95
    private const val PERCENT = 100L

    /** Od której pozycji wznowić (0 = od początku): nie dla ukończonych, przypadkowej sekundy ani końcówki. */
    fun resumePosition(
        positionMs: Long,
        durationMs: Long,
        completed: Boolean = false,
    ): Long {
        if (completed || positionMs < MIN_RESUME_MS) return 0L
        if (durationMs > 0 && positionMs * PERCENT >= durationMs * DONE_PERCENT) return 0L
        return positionMs
    }

    /** Film/odcinek uznajemy za obejrzany od 97% długości (długość nieznana = nie). */
    fun completed(
        positionMs: Long,
        durationMs: Long,
    ): Boolean = durationMs > 0 && positionMs * PERCENT >= durationMs * DONE_PERCENT

    /** Czy pozycja jest „w trakcie” (rząd Kontynuuj): co najmniej 30 s i jeszcze nie koniec (95%). */
    fun inProgress(
        positionMs: Long,
        durationMs: Long,
        completed: Boolean = false,
    ): Boolean =
        !completed &&
            positionMs >= CONTINUE_FROM_MS &&
            (durationMs <= 0 || positionMs * PERCENT < durationMs * CONTINUE_UNTIL_PERCENT)
}
