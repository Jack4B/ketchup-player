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
    ) : ViewModel() {
        val onboardingDone: StateFlow<Boolean?> =
            onboarding.done.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        fun finishOnboarding() {
            viewModelScope.launch { onboarding.setDone(true) }
        }
    }
