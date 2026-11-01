package pl.ketchupplayer.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    ) : ViewModel() {
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
        }
    }
