package pl.ketchupplayer.tv.player.failover

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.ketchupplayer.tv.core.model.StreamSpec
import pl.ketchupplayer.tv.core.policy.BkFailoverPolicy
import pl.ketchupplayer.tv.core.policy.ContentKind
import pl.ketchupplayer.tv.core.policy.FailoverSettings
import pl.ketchupplayer.tv.core.policy.IncidentReason
import pl.ketchupplayer.tv.player.engine.PlaybackStatus
import pl.ketchupplayer.tv.player.engine.PlayerController

/**
 * Kaskadowy failover (KONSPEKT §7.1.3). Obserwuje [controller], liczy incydenty bieżącego źródła i przełącza na kolejne
 * źródło z łańcucha po przekroczeniu progów z [BkFailoverPolicy]. Czas pochodzi z [scope] (w testach: wirtualny).
 *
 * Odtworzone ze specyfikacji (do weryfikacji golden master): twarde błędy ponawiają to samo źródło
 * `tvRecoveryRetries` razy, rebuffery liczą się od `minimumStallMs`, ciągłe buforowanie ≥ `tvSwitchDelaySeconds`
 * wymusza przełączenie, 30 s stabilnego grania zeruje liczniki.
 */
class FailoverCoordinator(
    private val controller: PlayerController,
    private val settings: FailoverSettings,
    private val scope: CoroutineScope,
    private val kind: ContentKind = ContentKind.TV,
) {
    private val _state = MutableStateFlow<FailoverState>(FailoverState.Idle)
    val state: StateFlow<FailoverState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<FailoverEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<FailoverEvent> = _events.asSharedFlow()

    private var chain: List<SourceCandidate> = emptyList()
    private var index = 0
    private var cycle = 0
    private var hardIncidents = 0
    private var stallIncidents = 0
    private var monitor: Job? = null
    private var cycleJob: Job? = null

    private val enabled get() = BkFailoverPolicy.enabled(settings, kind)

    fun start(sources: List<SourceCandidate>) {
        stop()
        chain = sources
        index = 0
        cycle = 0
        if (sources.isEmpty()) {
            _state.value = FailoverState.Failed
            return
        }
        monitor = scope.launch { controller.state.collectLatest { onPlayerState(it.status) } }
        playCurrent()
    }

    /** Ponowny start od pierwszego źródła (przycisk „Spróbuj ponownie od Listy 1”). */
    fun restart() {
        val sources = chain
        if (sources.isNotEmpty()) start(sources)
    }

    fun stop() {
        monitor?.cancel()
        cycleJob?.cancel()
        monitor = null
        cycleJob = null
        controller.stop()
        _state.value = FailoverState.Idle
    }

    private suspend fun onPlayerState(status: PlaybackStatus) {
        when (status) {
            PlaybackStatus.ERROR -> onHardIncident()
            PlaybackStatus.BUFFERING -> watchBuffering()
            PlaybackStatus.READY -> {
                delay(STABLE_MS)
                hardIncidents = 0
                stallIncidents = 0
            }
            else -> Unit
        }
    }

    /** Buforowanie: po `minimumStallMs` liczy się jako rebuffer, po `tvSwitchDelaySeconds` wymusza zmianę źródła. */
    private suspend fun watchBuffering() {
        val sensitivity = settings.sensitivity
        val minStall = BkFailoverPolicy.minimumStallMs(sensitivity)
        val hardLimit = BkFailoverPolicy.tvSwitchDelaySeconds(settings.tvDelaySeconds) * MS_PER_SECOND
        delay(minStall)
        stallIncidents++
        val threshold = BkFailoverPolicy.incidentThreshold(sensitivity, IncidentReason.REBUFFER)
        if (enabled && stallIncidents >= threshold) {
            switchNext()
            return
        }
        delay((hardLimit - minStall).coerceAtLeast(0))
        if (enabled) switchNext() else playCurrent()
    }

    private fun onHardIncident() {
        hardIncidents++
        val retries = BkFailoverPolicy.tvRecoveryRetries(settings.sensitivity)
        if (!enabled || hardIncidents <= retries) playCurrent() else switchNext()
    }

    private fun playCurrent() {
        val candidate = chain[index]
        cycleJob?.cancel()
        cycleJob =
            scope.launch {
                val spec = resolve(candidate)
                if (spec == null) {
                    onHardIncident()
                } else {
                    controller.play(spec)
                    _state.value = FailoverState.Playing(index, chain.size, candidate.label)
                }
            }
    }

    private suspend fun resolve(candidate: SourceCandidate): StreamSpec? =
        try {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { candidate.resolve().getOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            null.also { e.message }
        }

    private fun switchNext() {
        hardIncidents = 0
        stallIncidents = 0
        if (index + 1 < chain.size) {
            index++
            _events.tryEmit(FailoverEvent.Switched(index, chain.size, chain[index].label))
            playCurrent()
        } else {
            exhausted()
        }
    }

    private fun exhausted() {
        controller.stop()
        val left = MAX_CYCLES - cycle - 1
        if (left <= 0) {
            _state.value = FailoverState.Failed
            return
        }
        _state.value = FailoverState.Exhausted(left)
        cycleJob?.cancel()
        cycleJob =
            scope.launch {
                delay(BkFailoverPolicy.cooldownMs(settings.sensitivity))
                cycle++
                index = 0
                playCurrent()
            }
    }

    private companion object {
        const val EVENT_BUFFER = 8
        const val STABLE_MS = 30_000L
        const val MS_PER_SECOND = 1_000L
        const val RESOLVE_TIMEOUT_MS = 20_000L
        const val MAX_CYCLES = 2
    }
}
