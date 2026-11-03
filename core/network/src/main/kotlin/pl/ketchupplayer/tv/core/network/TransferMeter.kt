package pl.ketchupplayer.tv.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Licznik pobranych bajtów bieżącego pobierania listy: UI pokazuje „Pobrano X MB”,
 * więc przy wolnej sieci widać, że import idzie.
 */
class TransferMeter {
    private val total = MutableStateFlow(0L)
    val bytes: StateFlow<Long> = total

    fun reset() {
        total.value = 0L
    }

    fun add(count: Int) {
        total.value += count
    }
}
