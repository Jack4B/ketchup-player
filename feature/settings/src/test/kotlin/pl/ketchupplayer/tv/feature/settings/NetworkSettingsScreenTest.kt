package pl.ketchupplayer.tv.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
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
import pl.ketchupplayer.tv.core.network.UserAgentProfiles

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class NetworkSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val rows =
        listOf(
            ProviderHeaderRow("a", "365TV", UserAgentProfiles.AUTO),
            ProviderHeaderRow("b", "Lista PL", UserAgentProfiles.VLC),
        )

    private val customs =
        listOf(UserAgentProfiles.custom("PremierLeagueModPlayer"), UserAgentProfiles.custom("Wink/1.31.1"))

    @Test
    fun screenshot() {
        compose.setContent {
            KetchupTheme(
                device = DeviceClass.TV,
            ) { NetworkSettingsScreen(rows, customs, { _, _ -> }, { true }, {}, {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/network_settings.png")
    }

    /** D-pad: OK na liście otwiera okno wyboru z ptaszkiem; DÓŁ + OK wybiera kolejną pozycję (OTT Navigator). */
    @Test
    fun dpadOkOtwieraListeWyboruIWybieraNaglowek() {
        val picked = mutableListOf<String>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                NetworkSettingsScreen(rows, customs, { r, id -> picked += r.providerId + ":" + id }, { true }, {}, {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("✓").assertExists() // zaznaczona pozycja ma ptaszek
        // Okno wyboru to osobny korzeń (Dialog): klawisze trafiają do ostatniego.
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf("a:ott"), picked)
    }

    @Test
    fun backWychodzi() {
        var backs = 0
        compose.setContent {
            KetchupTheme(
                device = DeviceClass.TV,
            ) { NetworkSettingsScreen(rows, customs, { _, _ -> }, { true }, {}, { backs++ }) }
        }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }

    /** Test połączenia: wiersz „Testuj połączenie” wywołuje sprawdzenie listy, a wyniki są widoczne na ekranie. */
    @Test
    fun testPolaczeniaWywolujeSprawdzenieIPokazujeOdpowiedzSerwera() {
        val probed = mutableListOf<String>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                NetworkSettingsScreen(
                    rows,
                    customs,
                    { _, _ -> },
                    { true },
                    {},
                    {},
                    probe = listOf("VLC: HTTP 513 · serwer: nginx, «Too many connections»"),
                    onProbe = { probed += it.providerId },
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf("a"), probed)
        compose.onNodeWithText("Too many connections", substring = true).assertExists()
    }
}
