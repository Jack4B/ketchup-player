package pl.ketchupplayer.tv.core.data.metadata

import java.util.concurrent.ConcurrentHashMap

/**
 * Dzienny budżet zapytań do źródeł z limitem (OMDb i MDBList: po 1000 dziennie na darmowym kluczu). Licznik zeruje się
 * o północy UTC (tak liczą te serwisy), ale tylko w pamięci: po restarcie aplikacji liczymy od zera, a zapas
 * (cap poniżej 1000) pokrywa niewielkie różnice.
 */
class DailyBudget(
    private val dayNow: () -> Long = { System.currentTimeMillis() / DAY_MS },
) {
    private val used = ConcurrentHashMap<String, Int>()
    private var day = dayNow()

    /** Zajmuje [n] zapytań źródła [source]; false, gdy przekroczyłoby [cap] w bieżącej dobie. */
    @Synchronized
    fun take(
        source: String,
        n: Int,
        cap: Int,
    ): Boolean {
        val today = dayNow()
        if (today != day) {
            used.clear()
            day = today
        }
        val now = used[source] ?: 0
        if (now + n > cap) return false
        used[source] = now + n
        return true
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
