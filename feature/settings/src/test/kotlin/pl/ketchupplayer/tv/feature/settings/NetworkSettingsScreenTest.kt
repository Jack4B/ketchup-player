package pl.ketchupplayer.tv.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.RequestMethod
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

    private fun showWithCustom(
        rows: List<ProviderHeaderRow>,
        accept: (ProviderHeaderRow, String) -> Boolean,
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                NetworkSettingsScreen(rows, emptyList(), { _, _ -> }, { true }, {}, {}, onSelectCustom = accept)
            }
        }
        compose.waitForIdle()
    }

    private fun openCustomEditor(listName: String) {
        compose.onNodeWithText(listName).performClick()
        compose.waitForIdle()
        // Okno wyboru to lista leniwa: „Własny…” jest ostatnią pozycją i może wymagać przewinięcia do widoku.
        if (compose.onAllNodes(hasText("Własny…")).fetchSemanticsNodes().isEmpty()) {
            compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Własny…"))
        }
        compose.onNodeWithText("Własny…").performClick()
        compose.waitForIdle()
    }

    /** Własny User-Agent wpisujemy bezpośrednio dla listy, bez wcześniejszego dodawania w ustawieniach globalnych. */
    @Test
    fun wlasnyUserAgentListyZapisywanyBezUstawienGlobalnych() {
        val saved = mutableListOf<Pair<String, String>>()
        showWithCustom(rows) { r, v ->
            saved += r.providerId to v
            true
        }
        openCustomEditor("365TV")
        compose.onNode(hasSetTextAction()).performTextInput("MojAgent/3")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("a" to "MojAgent/3"), saved)
    }

    /** Ponowne otwarcie edycji: zapisana wartość wraca w polu, a na liście jest ukryta (jak dane dostępu). */
    @Test
    fun ponowneOtwarcieEdycjiPokazujeZapisanaWartoscAListaJejNie() {
        val stored = listOf(ProviderHeaderRow("a", "365TV", UserAgentProfiles.custom("StaryAgent/1")))
        showWithCustom(stored) { _, _ -> true }
        compose.onNodeWithText("Własny User-Agent (wartość ukryta)").assertExists()
        assertTrue(compose.onAllNodes(hasText("StaryAgent/1")).fetchSemanticsNodes().isEmpty())
        openCustomEditor("365TV")
        compose.onNode(hasText("StaryAgent/1") and hasSetTextAction()).assertExists()
    }

    @Test
    fun niepoprawnyWlasnyUserAgentPokazujeBladIniczegoNieZapisuje() {
        val saved = mutableListOf<String>()
        showWithCustom(rows) { _, v ->
            saved += v
            false
        }
        openCustomEditor("365TV")
        compose.onNode(hasSetTextAction()).performTextInput("zażółć")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithText("Niepoprawny", substring = true).assertExists()
        assertEquals(listOf("zażółć"), saved)
    }

    @Test
    fun screenshot() {
        compose.setContent {
            KetchupTheme(
                device = DeviceClass.TV,
            ) { NetworkSettingsScreen(rows, customs, { _, _ -> }, { true }, {}, {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/network_settings.png")
    }

    /** D-pad: OK otwiera okno wyboru z ptaszkiem; DÓŁ + OK wybiera kolejną pozycję (VLC, po domyślnym). */
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
        assertEquals(listOf("a:vlc"), picked)
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
        // Wiersze: nagłówki, metody zapytania, potem test połączenia; D-pad na wierszach jest sprawdzany osobno
        // (dpadOkOtwieraListeWyboruIWybieraNaglowek, dpadWybieraMetodePostDlaListy), tu liczy się podpięcie testu.
        compose.onNodeWithText("Testuj połączenie: 365TV").performClick()
        assertEquals(listOf("a"), probed)
        // Wyniki są pod wierszami (po dodaniu wierszy metody poza ekranem 540 dp): lista leniwa wymaga przewinięcia.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Too many connections", substring = true))
        compose.onNodeWithText("Too many connections", substring = true).assertExists()
    }

    /** Ustawienia listy: wiersz „Metoda zapytania” otwiera wybór GET/POST; D-pad: DÓŁ do wiersza, OK, DÓŁ, OK. */
    @Test
    fun dpadWybieraMetodePostDlaListy() {
        val picked = mutableListOf<String>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                NetworkSettingsScreen(
                    rows,
                    customs,
                    { _, _ -> },
                    { true },
                    {},
                    {},
                    onSelectMethod = { r, m -> picked += r.providerId + ":" + m },
                )
            }
        }
        compose.waitForIdle()
        repeat(rows.size) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("✓").assertExists()
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf("a:POST"), picked)
    }

    @Test
    fun dotykWybieraMetodePostDlaListy() {
        val picked = mutableListOf<String>()
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                NetworkSettingsScreen(
                    rows,
                    customs,
                    { _, _ -> },
                    { true },
                    {},
                    {},
                    onSelectMethod = { r, m -> picked += r.providerId + ":" + m },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Metoda zapytania: Lista PL").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("POST").performClick()
        assertEquals(listOf("b:POST"), picked)
    }

    @Test
    fun wierszMetodyPokazujeZapisanaMetode() {
        val postRows = listOf(ProviderHeaderRow("a", "365TV", UserAgentProfiles.AUTO, RequestMethod.POST))
        compose.setContent {
            KetchupTheme(
                device = DeviceClass.TV,
            ) { NetworkSettingsScreen(postRows, customs, { _, _ -> }, { true }, {}, {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("POST").assertExists()
    }
}
