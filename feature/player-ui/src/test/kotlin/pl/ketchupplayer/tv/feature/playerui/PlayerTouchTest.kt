package pl.ketchupplayer.tv.feature.playerui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.LocalUiClock
import pl.ketchupplayer.tv.core.designsystem.UiClock
import pl.ketchupplayer.tv.player.engine.PlaybackStatus
import pl.ketchupplayer.tv.player.engine.PlayerState

/**
 * Dotyk w odtwarzaczu (telefon, minimalna obsługa do testów; pełny telefon w 2.0, patrz docs/adr/tv-first.md).
 * Tryb dotyku włącza dopiero dotknięcie ekranu, więc na TV (pilot) ekran wygląda i działa jak dotąd.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PlayerTouchTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val film = PlayerState(status = PlaybackStatus.READY, isPlaying = true, positionMs = POS, durationMs = DUR)

    private fun show(
        controller: FakeController,
        vodTitle: String? = "Film",
        vodInfo: VodInfoUi? = null,
        onBack: () -> Unit = {},
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                CompositionLocalProvider(LocalUiClock provides UiClock.Frozen) {
                    PlayerScreen(
                        PlayerUiState.Playing(vodTitle ?: "JEDEN HD"),
                        controller,
                        onRetry = {},
                        onBack = onBack,
                        vodTitle = vodTitle,
                        vodInfo = vodInfo,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Stuknięcie w pusty pas u góry, w połowie szerokości: poza przyciskami, zegarem i dolnym OSD. */
    private fun tapEmptyArea() {
        compose.onRoot().performTouchInput { click(percentOffset(0.5f, 0.12f)) }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun present(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun key(k: Key) {
        compose.onRoot().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    @Test
    fun bezDotykuNieMaPrzyciskowDotykowych() {
        show(FakeController(film))
        ALL_TOUCH_TAGS.forEach { assertFalse(it, present(it)) }
    }

    @Test
    fun stukniecieWlaczaTrybDotykuPokazujeOsdAKolejneStukniecieChowaIPokazuje() {
        show(FakeController(film))
        tapEmptyArea() // pierwsze dotknięcie tylko włącza tryb dotyku, OSD zostaje
        assertTrue(present(TAG_PLAY_PAUSE))
        tapEmptyArea()
        assertFalse(present(TAG_PLAY_PAUSE))
        assertFalse(present(TAG_EXIT))
        tapEmptyArea()
        assertTrue(present(TAG_PLAY_PAUSE))
        assertTrue(present(TAG_EXIT))
        assertTrue(present(TAG_SEEKBAR))
    }

    @Test
    fun osdDotykowyChowaSiePoCzasieJakOsdPilota() {
        show(FakeController(film))
        tapEmptyArea()
        assertTrue(present(TAG_PLAY_PAUSE))
        compose.mainClock.advanceTimeBy(OSD_HIDE_MS)
        compose.waitForIdle()
        assertFalse(present(TAG_PLAY_PAUSE))
    }

    @Test
    fun dotykPrzyciskuResetujeLicznikChowania() {
        show(FakeController(film))
        tapEmptyArea()
        compose.mainClock.advanceTimeBy(3_000)
        tap(TAG_BACK10) // reset licznika
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        assertTrue("po 6 s od pierwszego, ale 3 s od ostatniego dotyku", present(TAG_PLAY_PAUSE))
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        assertFalse(present(TAG_PLAY_PAUSE))
    }

    @Test
    fun przyciskPauzyWstrzymujeAWznawiaGdyStoi() {
        val grajacy = FakeController(film)
        show(grajacy)
        tapEmptyArea()
        tap(TAG_PLAY_PAUSE)
        assertEquals(1, grajacy.pauses)
        assertEquals(0, grajacy.resumes)
    }

    @Test
    fun przyciskOdtwarzaWznawiaWstrzymany() {
        val stojacy = FakeController(film.copy(isPlaying = false))
        show(stojacy)
        tapEmptyArea()
        tap(TAG_PLAY_PAUSE)
        assertEquals(1, stojacy.resumes)
        assertEquals(0, stojacy.pauses)
    }

    @Test
    fun skokiOdDziesiecSekundPrzewijajWPrzodIWTyl() {
        val c = FakeController(film)
        show(c)
        tapEmptyArea()
        tap(TAG_BACK10)
        tap(TAG_FORWARD10)
        assertEquals(listOf(POS - TEN_S, POS + TEN_S), c.seeks)
    }

    @Test
    fun skokiNieWychodzaPozaFilm() {
        val naPoczatku = FakeController(film.copy(positionMs = 5_000))
        show(naPoczatku)
        tapEmptyArea()
        tap(TAG_BACK10)
        assertEquals(listOf(0L), naPoczatku.seeks)
    }

    @Test
    fun stukniecieNaPaskuPrzewijaWJednoMiejsce() {
        val c = FakeController(film)
        show(c)
        tapEmptyArea()
        compose.onNodeWithTag(TAG_SEEKBAR).performTouchInput { click(percentOffset(0.5f, 0.5f)) }
        compose.waitForIdle()
        assertEquals(1, c.seeks.size)
        assertEquals(DUR / 2f, c.seeks.single().toFloat(), DUR / 100f)
    }

    @Test
    fun przeciagniecieNaPaskuPrzewijaRazPoPuszczeniu() {
        val c = FakeController(film)
        show(c)
        tapEmptyArea()
        compose.onNodeWithTag(TAG_SEEKBAR).performTouchInput {
            swipe(percentOffset(0.2f, 0.5f), percentOffset(0.8f, 0.5f))
        }
        compose.waitForIdle()
        assertEquals("jeden seek, po puszczeniu palca", 1, c.seeks.size)
        assertEquals(DUR * 0.8f, c.seeks.single().toFloat(), DUR / 20f)
    }

    @Test
    fun nazywoBezPaskaPrzewijaniaIBezSkokow() {
        val c = FakeController(PlayerState(status = PlaybackStatus.READY, isPlaying = true))
        show(c, vodTitle = null)
        tapEmptyArea()
        assertTrue(present(TAG_PLAY_PAUSE))
        assertTrue(present(TAG_EXIT))
        listOf(TAG_BACK10, TAG_FORWARD10, TAG_SEEKBAR).forEach { assertFalse(it, present(it)) }
        tap(TAG_PLAY_PAUSE)
        assertEquals(1, c.pauses)
    }

    @Test
    fun wyjscieZPrzyciskuRobiToSamoCoBack() {
        var backs = 0
        show(FakeController(film), onBack = { backs++ })
        tapEmptyArea()
        tap(TAG_EXIT)
        assertEquals(1, backs)
    }

    @Test
    fun systemowyBackDzialaWTrybieDotyku() {
        var backs = 0
        show(FakeController(film), onBack = { backs++ })
        tapEmptyArea()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, backs)
    }

    /** Regresja pilota: klawisze po dotyku wracają do trybu klawiszowego, a OK nadal pauzuje/wznawia film. */
    @Test
    fun klawiszPoDotykuWracaDoPilotaAOkSteruje() {
        val c = FakeController(film.copy(isPlaying = false))
        show(c)
        tapEmptyArea()
        assertTrue(present(TAG_PLAY_PAUSE))
        key(Key.ShiftLeft) // pierwszy klawisz po dotyku może tylko przełączyć tryb wejścia systemu
        key(Key.ShiftLeft)
        assertFalse(present(TAG_PLAY_PAUSE))
        key(Key.DirectionCenter)
        assertEquals(1, c.resumes)
    }

    @Test
    fun screenshotOsdDotykowyFilmu() {
        show(FakeController(film), vodInfo = VodInfoUi("Film", "2026 · Akcja · 104 min", null, null, null))
        tapEmptyArea()
        compose.onRoot().captureRoboImage("src/test/screenshots/player_touch_vod.png")
    }

    @Test
    fun screenshotOsdDotykowyNaZywo() {
        show(FakeController(PlayerState(status = PlaybackStatus.READY, isPlaying = true)), vodTitle = null)
        tapEmptyArea()
        compose.onRoot().captureRoboImage("src/test/screenshots/player_touch_live.png")
    }

    /** Telefon poziomo (podgląd 2.0): sprawdza, że przyciski i pasek mieszczą się na niskim ekranie. */
    @Test
    @Config(qualifiers = "w800dp-h360dp-xxhdpi")
    fun screenshotOsdDotykowyTelefonPoziomo() {
        show(FakeController(film), vodInfo = VodInfoUi("Film", "2026 · Akcja · 104 min", null, null, null))
        tapEmptyArea()
        compose.onRoot().captureRoboImage("src/test/screenshots/player_touch_phone.png")
    }

    private companion object {
        const val POS = 2_400_000L
        const val DUR = 6_240_000L
        const val TEN_S = 10_000L
        const val OSD_HIDE_MS = 4_100L
        val ALL_TOUCH_TAGS = listOf(TAG_PLAY_PAUSE, TAG_BACK10, TAG_FORWARD10, TAG_EXIT, TAG_SEEKBAR)
    }
}
