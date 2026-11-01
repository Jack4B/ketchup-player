package pl.ketchupplayer.tv.feature.playerui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.catalog.CatalogRepository
import pl.ketchupplayer.tv.core.data.playback.FailoverSettingsRepository
import pl.ketchupplayer.tv.core.data.vod.VodRepository
import pl.ketchupplayer.tv.core.policy.FailoverSettings
import pl.ketchupplayer.tv.player.engine.PlayerController
import pl.ketchupplayer.tv.player.engine.PlayerControllerFactory
import pl.ketchupplayer.tv.player.failover.FailoverCoordinator
import pl.ketchupplayer.tv.player.failover.FailoverEvent
import pl.ketchupplayer.tv.player.failover.FailoverState
import pl.ketchupplayer.tv.player.failover.SourceCandidate
import javax.inject.Inject

/** Stan rozwiązywania kanału; stan samego odtwarzania jest w [PlayerController.state]. */
sealed interface PlayerUiState {
    data class Resolving(
        val channelName: String,
    ) : PlayerUiState

    data class Playing(
        val channelName: String,
    ) : PlayerUiState

    data class Failed(
        val channelName: String,
        val unknownChannel: Boolean,
    ) : PlayerUiState
}

/** Informacje o filmie/odcinku do OSD: tytuł, wiersz meta (rok, gatunek, czas, ocena), opis, obsada i źródło opisu. */
data class VodInfoUi(
    val title: String,
    val meta: String = "",
    val plot: String? = null,
    val cast: String? = null,
    val source: String? = null,
)

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val vod: VodRepository,
        private val failoverSettings: FailoverSettingsRepository,
        prefs: pl.ketchupplayer.tv.core.data.playback.PlayerPrefsRepository,
        factory: PlayerControllerFactory,
    ) : ViewModel() {
        /** Zegar w rogu ekranu (Ustawienia → Odtwarzanie). */
        val clockEnabled: StateFlow<Boolean> =
            prefs.clockEnabled.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, true)

        val controller: PlayerController = factory.create()
        private val _ui = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving(""))
        val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
        private val _notice = MutableStateFlow<String?>(null)

        /** Krótka informacja o przełączeniu źródła („Lista 2 · nazwa”); znika po [NOTICE_MS]. */
        val notice: StateFlow<String?> = _notice.asStateFlow()
        private val failover = FailoverCoordinator(controller, FailoverSettings(), viewModelScope)
        private var currentId: String? = null
        private var channelName = ""

        init {
            viewModelScope.launch {
                failover.state.collect { st ->
                    when (st) {
                        is FailoverState.Playing -> _ui.value = PlayerUiState.Playing(channelName)
                        is FailoverState.Failed -> _ui.value = PlayerUiState.Failed(channelName, unknownChannel = false)
                        else -> Unit
                    }
                }
            }
            viewModelScope.launch {
                failover.events.collect { ev ->
                    if (ev is FailoverEvent.Switched) {
                        _notice.value = ev.label
                        delay(NOTICE_MS)
                        _notice.value = null
                    }
                }
            }
        }

        /** Rozpoczyna odtwarzanie kanału; to samo id drugi raz (np. po obrocie) jest ignorowane, chyba że [force]. */
        fun start(
            channelId: String,
            force: Boolean = false,
        ) {
            if (!force && channelId == currentId) return
            currentId = channelId
            vodId = null
            vodJob?.cancel()
            _vodTitle.value = null
            _vodInfo.value = null
            viewModelScope.launch {
                val channel = catalog.channel(channelId)
                if (channel == null) {
                    _ui.value = PlayerUiState.Failed("", unknownChannel = true)
                    return@launch
                }
                channelName = channel.name
                catalog.markWatched(channelId)
                _ui.value = PlayerUiState.Resolving(channel.name)
                val chain = catalog.equivalents(channelId).ifEmpty { listOf(channel) }
                failover.settings = failoverSettings.settings.first()
                failover.start(
                    chain.mapIndexed { i, ch ->
                        val label =
                            if (i == 0 &&
                                chain.size == 1
                            ) {
                                ch.displayName
                            } else {
                                "Lista ${i + 1} · ${ch.displayName}"
                            }
                        SourceCandidate(label) { catalog.resolveStream(ch.id) }
                    },
                )
            }
        }

        private val _vodInfo = MutableStateFlow<VodInfoUi?>(null)

        /** Opis, obsada i dane filmu z katalogu dostawcy; null dla kanałów na żywo. */
        val vodInfo: StateFlow<VodInfoUi?> = _vodInfo.asStateFlow()

        private val _vodTitle = MutableStateFlow<String?>(null)

        /** Tytuł odtwarzanego filmu/odcinka; null dla kanałów na żywo (steruje wyglądem OSD). */
        val vodTitle: StateFlow<String?> = _vodTitle.asStateFlow()
        private var vodId: String? = null
        private var vodJob: kotlinx.coroutines.Job? = null

        /** Film lub odcinek: wznawia od zapisanej pozycji, chyba że [fromStart]; zapisuje postęp co kilka sekund. */
        fun startVod(
            itemId: String,
            fromStart: Boolean,
            force: Boolean = false,
        ) {
            if (!force && vodId == itemId) return
            vodId = itemId
            currentId = null
            vodJob?.cancel()
            vodJob =
                viewModelScope.launch {
                    val info = vod.playback(itemId)
                    if (info == null) {
                        _ui.value = PlayerUiState.Failed("", unknownChannel = true)
                        return@launch
                    }
                    channelName = info.title
                    _vodTitle.value = info.title
                    _vodInfo.value = VodInfoUi(info.title)
                    // Opis i obsada: pobierane w tle (dostawca Xtream: get_vod_info), OSD uzupełnia się, gdy dojdą.
                    launch {
                        vod.refreshInfo(itemId)
                        vod.entry(itemId).first()?.let { _vodInfo.value = it.toInfoUi() }
                    }
                    _ui.value = PlayerUiState.Resolving(info.title)
                    failover.settings = failoverSettings.settings.first()
                    failover.start(listOf(SourceCandidate(info.title) { vod.resolve(itemId) }))
                    val resumeAt = if (fromStart) 0L else resumePosition(info.positionMs, info.durationMs)
                    var seeked = resumeAt <= 0
                    while (true) {
                        delay(PROGRESS_MS)
                        controller.poll()
                        val st = controller.state.value
                        if (!seeked && (st.durationMs ?: 0) > 0) {
                            controller.seekTo(resumeAt)
                            seeked = true
                        }
                        saveProgress()
                    }
                }
        }

        private fun pl.ketchupplayer.tv.core.data.vod.VodEntry.toInfoUi(): VodInfoUi {
            val i = item
            val rating = i.rating?.let { "★ " + String.format(java.util.Locale.forLanguageTag("pl"), "%.1f", it) }
            val meta =
                listOfNotNull(i.year?.toString(), i.genre ?: i.category, i.durationMin?.let { "$it min" }, rating)
                    .joinToString(" · ")
            return VodInfoUi(i.title, meta, i.plot, i.cast, i.plot?.let { "Opis z katalogu dostawcy" })
        }

        private fun resumePosition(
            positionMs: Long,
            durationMs: Long,
        ) = if (durationMs > 0 && positionMs < durationMs * RESUME_LIMIT) positionMs else 0L

        private suspend fun saveProgress() {
            val id = vodId ?: return
            val st = controller.state.value
            val duration = st.durationMs ?: return
            if (duration > 0 && st.positionMs > 0) vod.saveProgress(id, st.positionMs, duration)
        }

        /** Zapis przed wyjściem z odtwarzacza (BACK); nie może zostać anulowany razem z ViewModelem. */
        fun saveNow() {
            if (vodId == null) return
            controller.poll()
            viewModelScope.launch(NonCancellable) { saveProgress() }
        }

        fun retry() =
            vodId?.let { startVod(it, fromStart = false, force = true) } ?: currentId?.let { start(it, force = true) }

        override fun onCleared() {
            failover.stop()
            controller.release()
        }

        private companion object {
            const val NOTICE_MS = 4_000L
            const val PROGRESS_MS = 5_000L
            const val RESUME_LIMIT = 0.9
        }
    }
