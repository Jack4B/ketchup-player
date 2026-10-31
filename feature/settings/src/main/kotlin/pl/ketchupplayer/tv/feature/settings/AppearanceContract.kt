package pl.ketchupplayer.tv.feature.settings

import pl.ketchupplayer.tv.core.designsystem.AccentChoice
import pl.ketchupplayer.tv.core.designsystem.AccentId
import pl.ketchupplayer.tv.core.designsystem.AccentWarning
import pl.ketchupplayer.tv.core.designsystem.ThemeId

/** Edytor własnego koloru: HEX albo suwaki HSL (odcień 0–360, nasycenie i jasność 0–1). */
data class CustomEditor(
    val hex: String,
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val hexError: Boolean = false,
    val adjusted: Boolean = false,
    val warnings: Set<AccentWarning> = emptySet(),
)

data class AppearanceUiState(
    val theme: ThemeId = ThemeId.GRANAT,
    val accent: AccentChoice = AccentChoice.Preset(AccentId.TURKUS),
    val editor: CustomEditor? = null,
)

sealed interface AppearanceEvent {
    data class SelectTheme(
        val theme: ThemeId,
    ) : AppearanceEvent

    data class SelectPreset(
        val id: AccentId,
    ) : AppearanceEvent

    data object OpenCustom : AppearanceEvent

    data object CloseCustom : AppearanceEvent

    data class HexChanged(
        val value: String,
    ) : AppearanceEvent

    data class HueChanged(
        val value: Float,
    ) : AppearanceEvent

    data class SaturationChanged(
        val value: Float,
    ) : AppearanceEvent

    data class LightnessChanged(
        val value: Float,
    ) : AppearanceEvent
}
