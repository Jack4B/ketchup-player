package pl.ketchupplayer.tv.core.data.epg

import kotlinx.coroutines.flow.Flow
import pl.ketchupplayer.tv.core.model.EpgProgramme

data class EpgRefreshReport(
    val sources: Int,
    val failedSources: Int,
    val programmes: Int,
    val matchedChannels: Int,
)

interface EpgRepository {
    /** Rośnie po każdym zakończonym imporcie; ekrany przeładowują wtedy audycje (EPG może dojść w tle). */
    val revision: kotlinx.coroutines.flow.StateFlow<Int>

    /** Audycje trwające w chwili [nowMs]: id kanału katalogu -> audycja. Odświeża się też po imporcie. */
    fun onAir(nowMs: Long): Flow<Map<String, EpgProgramme>>

    /** Audycje kanału w oknie czasowym (do przewodnika). */
    suspend fun range(
        channelId: String,
        fromMs: Long,
        toMs: Long,
    ): List<EpgProgramme>

    /** Audycje wszystkich kanałów zachodzące na okno [fromMs, toMs) (Radar). */
    suspend fun window(
        fromMs: Long,
        toMs: Long,
    ): List<EpgProgramme>

    /**
     * Pobiera i importuje włączone źródła XMLTV (epg.ovh, własne, adresy dostawców).
     * Jednoczesne wywołania nie dublują importu (single-flight). Gdy żadne źródło się nie powiedzie,
     * dotychczasowy przewodnik zostaje nietknięty.
     */
    suspend fun refresh(): Result<EpgRefreshReport>

    /** Odświeża tylko wtedy, gdy ostatni udany import jest starszy niż [maxAgeMs] albo go nie było. */
    suspend fun refreshIfStale(maxAgeMs: Long): Result<EpgRefreshReport>?
}
