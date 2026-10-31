package pl.ketchupplayer.tv.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.AccentChoice
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ThemeId
import pl.ketchupplayer.tv.core.model.BuiltInEpgSources
import pl.ketchupplayer.tv.core.model.EpgSource
import pl.ketchupplayer.tv.core.model.EpgSourceKind
import pl.ketchupplayer.tv.core.testing.assertNoHorizontalOverflow

/** Macierz ekranów: ta sama treść w pionie i poziomie na telefonie oraz na TV. Podklasy ustawiają rozmiar i klasę. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreensMatrixBase(
    private val device: DeviceClass,
    private val suffix: String,
) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sources =
        listOf(
            BuiltInEpgSources.ovh(),
            BuiltInEpgSources.teleman(),
            EpgSource(
                "c1",
                "Moje EPG z długą nazwą źródła",
                EpgSourceKind.CUSTOM_XMLTV,
                "https://moje.pl/epg.xml.gz",
                true,
            ),
        )

    private fun show(
        theme: ThemeId = ThemeId.GRANAT,
        accent: AccentChoice = AccentChoice.Preset(theme.defaultAccent),
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent { KetchupTheme(theme, accent, device) { content() } }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_$suffix.png")
        compose.assertNoHorizontalOverflow()
    }

    @Test
    fun wyglad() {
        show { AppearanceScreen(AppearanceUiState(), {}, {}) }
        shot("appearance")
    }

    @Test
    fun wygladBordoZEdytorem() {
        val editor = CustomEditor("#8844CC", 270f, 0.55f, 0.5f)
        val state = AppearanceUiState(ThemeId.BORDO, AccentChoice.Custom(0xFF8844CCL), editor)
        show(ThemeId.BORDO, state.accent) { AppearanceScreen(state, {}, {}) }
        shot("appearance_custom")
    }

    @Test
    fun zrodlaEpg() {
        show { EpgSourcesScreen(EpgSourcesUiState(sources), {}, {}) }
        shot("epg_sources")
    }

    @Test
    fun zrodlaEpgFormularzZBledem() {
        val state = EpgSourcesUiState(sources, AddForm("Moje", "ftp://x", FormError.URL))
        show { EpgSourcesScreen(state, {}, {}) }
        shot("epg_form")
    }
}
