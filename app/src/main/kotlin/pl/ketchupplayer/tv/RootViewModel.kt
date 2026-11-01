package pl.ketchupplayer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.onboarding.OnboardingRepository
import javax.inject.Inject

/** Stan korzenia aplikacji: czy kreator pierwszego uruchomienia jest zakończony (null = jeszcze nie wiadomo). */
@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        private val onboarding: OnboardingRepository,
        vod: pl.ketchupplayer.tv.core.data.vod.VodRepository,
        splash: SplashPolicy,
        private val catalog: pl.ketchupplayer.tv.core.data.catalog.CatalogRepository,
        private val epg: pl.ketchupplayer.tv.core.data.epg.EpgRepository,
        private val prefs: pl.ketchupplayer.tv.core.data.playback.PlayerPrefsRepository,
        private val clock: pl.ketchupplayer.tv.core.common.Clock,
    ) : ViewModel() {
        init {
            autoRefresh()
        }

        /**
         * Start: EPG starsze niż 6 h i listy starsze niż doba odświeżają się same (można wyłączyć w Ustawieniach →
         * Telewizja). Zawsze po dodaniu albo odświeżeniu listy pobieramy EPG, bo adres przewodnika bierze się z listy.
         */
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        private fun autoRefresh() {
            viewModelScope.launch {
                onboarding.done.first { it }
                val providers = catalog.providers.first()
                if (providers.isNotEmpty() && prefs.autoRefresh.first()) {
                    providers
                        // Bez daty odświeżenia lista jest w trakcie importu: nie dublujemy pobierania.
                        .filter { p -> p.lastRefreshMs?.let { it < clock.nowMs() - LIST_STALE_MS } == true }
                        .forEach { catalog.refresh(it.id) }
                    epg.refreshIfStale(EPG_STALE_MS)
                }
                // Po starcie: zmiana stanu list (dodanie, ręczne odświeżenie) = świeże EPG.
                var last = catalog.providers.first().map { it.id to it.lastRefreshMs }
                catalog.providers
                    .map { list -> list.map { it.id to it.lastRefreshMs } }
                    .distinctUntilChanged()
                    .debounce(DEBOUNCE_MS)
                    .collect { now ->
                        if (now != last && now.isNotEmpty()) epg.refresh()
                        last = now
                    }
            }
        }

        /** Są seriale z nowymi odcinkami: zielona kropka na pozycji „Seriale” w szufladzie. */
        val newEpisodes: StateFlow<Boolean> =
            vod.anyNewEpisodes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), false)

        val splashMs: Long = splash.durationMs

        val onboardingDone: StateFlow<Boolean?> =
            onboarding.done.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun finishOnboarding() {
            viewModelScope.launch { onboarding.setDone(true) }
        }

        private companion object {
            const val STOP_MS = 5_000L
            const val DEBOUNCE_MS = 2_000L
            const val EPG_STALE_MS = 6L * 60 * 60 * 1000
            const val LIST_STALE_MS = 24L * 60 * 60 * 1000
        }
    }
