package pl.ketchupplayer.tv.feature.providers

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo
import pl.ketchupplayer.tv.core.model.ProviderType
import pl.ketchupplayer.tv.core.testing.assertNoHorizontalOverflow

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ProvidersScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val p1 =
        ProviderInfo(ProviderId("a"), "Domowa lista", ProviderType.M3U, "iptv.example.invalid", 2514, 1L, null)
    private val p2 =
        ProviderInfo(ProviderId("b"), "Zapasowa", ProviderType.M3U, "backup.example.invalid", 0, 2L, "HTTP 503")

    private fun show(
        state: ProvidersUiState,
        events: MutableList<ProvidersEvent> = mutableListOf(),
        onBack: () -> Unit = {},
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) { ProvidersScreen(state, { events += it }, onBack) }
        }
        compose.waitForIdle()
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    @Test
    fun screenshotPusta() {
        show(ProvidersUiState())
        compose.onRoot().captureRoboImage("src/test/screenshots/providers_empty.png")
        compose.assertNoHorizontalOverflow()
    }

    @Test
    fun screenshotZListaIRozwinieciem() {
        show(ProvidersUiState(providers = listOf(p1, p2), expandedId = p1.id))
        compose.onRoot().captureRoboImage("src/test/screenshots/providers_list.png")
    }

    @Test
    fun screenshotFormularzaZBledem() {
        show(ProvidersUiState(form = ProviderForm("Moja", "ftp://x", urlError = true)))
        compose.onRoot().captureRoboImage("src/test/screenshots/providers_form.png")
    }

    @Test
    fun dpadPustaListaFokusNaDodaj() {
        val events = mutableListOf<ProvidersEvent>()
        show(ProvidersUiState(), events)
        key(Key.DirectionCenter)
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.StartAdd), events)
    }

    @Test
    fun dpadPierwszyDostawcaRozwijaAkcje() {
        val events = mutableListOf<ProvidersEvent>()
        show(ProvidersUiState(providers = listOf(p1, p2)), events)
        key(Key.DirectionCenter)
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.Toggle(p1.id)), events)
    }

    @Test
    fun backZamykaFormularzPotemWychodzi() {
        val events = mutableListOf<ProvidersEvent>()
        var backs = 0
        show(ProvidersUiState(form = ProviderForm()), events) { backs++ }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(listOf<ProvidersEvent>(ProvidersEvent.Cancel), events)
        assertEquals(0, backs)
    }
}
