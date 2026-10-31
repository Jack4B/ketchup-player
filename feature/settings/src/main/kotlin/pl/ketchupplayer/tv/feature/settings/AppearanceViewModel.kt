package pl.ketchupplayer.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.ketchupplayer.tv.core.data.appearance.AppearanceRepository
import pl.ketchupplayer.tv.core.designsystem.AccentChoice
import pl.ketchupplayer.tv.core.designsystem.AccentFit
import pl.ketchupplayer.tv.core.designsystem.HexColor
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val repository: AppearanceRepository,
    ) : ViewModel() {
        private val editor = MutableStateFlow<CustomEditor?>(null)

        val state: StateFlow<AppearanceUiState> =
            combine(repository.appearance, editor) { a, e ->
                AppearanceUiState(a.theme, a.effectiveAccent, e)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppearanceUiState())

        fun onEvent(event: AppearanceEvent) {
            when (event) {
                is AppearanceEvent.SelectTheme -> viewModelScope.launch { repository.setTheme(event.theme) }
                is AppearanceEvent.SelectPreset -> {
                    editor.value = null
                    viewModelScope.launch { repository.setAccent(AccentChoice.Preset(event.id)) }
                }
                AppearanceEvent.OpenCustom ->
                    editor.value =
                        editorFrom(
                            (state.value.accent as? AccentChoice.Custom)?.argb
                                ?: accentArgb(state.value.accent),
                        )
                AppearanceEvent.CloseCustom -> editor.value = null
                is AppearanceEvent.HexChanged -> onHex(event.value)
                is AppearanceEvent.HueChanged -> onHsl(hue = event.value)
                is AppearanceEvent.SaturationChanged -> onHsl(saturation = event.value)
                is AppearanceEvent.LightnessChanged -> onHsl(lightness = event.value)
            }
        }

        private fun accentArgb(accent: AccentChoice): Long =
            when (accent) {
                is AccentChoice.Preset -> accent.id.argb
                is AccentChoice.Custom -> accent.argb
            }

        private fun editorFrom(argb: Long): CustomEditor {
            val (h, s, l) = AccentFit.toHsl(argb)
            return CustomEditor(HexColor.format(argb), h.toFloat(), s.toFloat(), l.toFloat())
        }

        private fun onHex(raw: String) {
            val argb = HexColor.parse(raw)
            if (argb == null) {
                editor.value = editor.value?.copy(hex = raw, hexError = true)
            } else {
                apply(editorFrom(argb).copy(hex = raw), argb)
            }
        }

        private fun onHsl(
            hue: Float? = null,
            saturation: Float? = null,
            lightness: Float? = null,
        ) {
            val e = editor.value ?: return
            val h = (hue ?: e.hue).coerceIn(0f, MAX_HUE)
            val s = (saturation ?: e.saturation).coerceIn(0f, 1f)
            val l = (lightness ?: e.lightness).coerceIn(0f, 1f)
            val argb = AccentFit.fromHsl(h.toDouble(), s.toDouble(), l.toDouble())
            apply(CustomEditor(HexColor.format(argb), h, s, l), argb)
        }

        /** Zapisuje kolor po korekcie kontrastu; edytor pokazuje wartości użytkownika i informację o korekcie. */
        private fun apply(
            draft: CustomEditor,
            argb: Long,
        ) {
            val theme = state.value.theme
            val fitted = AccentFit.fit(theme, argb)
            editor.value = draft.copy(hexError = false, adjusted = fitted.adjusted, warnings = fitted.warnings)
            viewModelScope.launch { repository.setAccent(AccentChoice.Custom(argb)) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val MAX_HUE = 360f
        }
    }
