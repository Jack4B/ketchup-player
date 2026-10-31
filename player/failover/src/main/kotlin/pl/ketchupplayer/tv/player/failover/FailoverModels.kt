package pl.ketchupplayer.tv.player.failover

import pl.ketchupplayer.tv.core.model.StreamSpec

/** Jedno źródło w łańcuchu (np. ten sam kanał u innego dostawcy). [resolve] zwraca adres odtwarzania. */
class SourceCandidate(
    val label: String,
    val resolve: suspend () -> Result<StreamSpec>,
)

sealed interface FailoverState {
    data object Idle : FailoverState

    /** Gra źródło [index] (od 0) z [total]; [label] to nazwa listy/dostawcy. */
    data class Playing(
        val index: Int,
        val total: Int,
        val label: String,
    ) : FailoverState

    /** Wszystkie źródła zawiodły; kolejny cykl ruszy po cooldownie (jeśli zostały [cyclesLeft]). */
    data class Exhausted(
        val cyclesLeft: Int,
    ) : FailoverState

    /** Wszystkie cykle wyczerpane — potrzebna decyzja użytkownika (ponów / wybierz źródło / wróć). */
    data object Failed : FailoverState
}

/** Zdarzenia jednorazowe do UI (toast „Przełączono na Lista 2 · nazwa”). */
sealed interface FailoverEvent {
    data class Switched(
        val index: Int,
        val total: Int,
        val label: String,
    ) : FailoverEvent
}
