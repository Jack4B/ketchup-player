package pl.ketchupplayer.tv.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Rozmiar interfejsu (raport z boxa: na starym telewizorze Full HD napisy są za duże). Skala zmienia gęstość całego
 * UI, więc kurczą się równo napisy, odstępy i elementy. [AUTO] dobiera skalę do ekranu: tak, żeby szerokość
 * robocza wynosiła około [TARGET_WIDTH_DP] dp (Full HD przy 320 dpi ma tylko 960 dp, a typowy telewizor 1280 dp).
 */
enum class UiScale(
    val factor: Float?,
) {
    AUTO(null),
    X70(0.7f),
    X80(0.8f),
    X90(0.9f),
    X100(1f),
    X115(1.15f),
}

const val TARGET_WIDTH_DP = 1280f
private const val AUTO_MIN = 0.7f
private const val PHONE_MAX_WIDTH_DP = 600

/** Współczynnik dla wybranej skali; auto: telefon bez zmian, telewizor tak, by szerokość roboczą zbliżyć do 1280 dp. */
fun resolveUiScale(
    scale: UiScale,
    screenWidthDp: Int,
): Float =
    scale.factor
        ?: if (screenWidthDp < PHONE_MAX_WIDTH_DP) 1f else (screenWidthDp / TARGET_WIDTH_DP).coerceIn(AUTO_MIN, 1f)

/** Zakłada skalę UI na całe poddrzewo (gęstość i czcionki razem). */
@Composable
fun ProvideUiScale(
    scale: UiScale,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val factor = resolveUiScale(scale, LocalConfiguration.current.screenWidthDp)
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * factor, density.fontScale),
        content = content,
    )
}
