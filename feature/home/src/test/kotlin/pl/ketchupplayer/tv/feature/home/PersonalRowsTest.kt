package pl.ketchupplayer.tv.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pl.ketchupplayer.tv.core.data.vod.VodEntry
import pl.ketchupplayer.tv.core.designsystem.DeviceClass
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind
import pl.ketchupplayer.tv.core.model.personalization.FeatureNamespace
import pl.ketchupplayer.tv.core.model.personalization.FollowSuggestion
import pl.ketchupplayer.tv.core.model.personalization.SportCard
import pl.ketchupplayer.tv.core.model.personalization.SportChannel
import pl.ketchupplayer.tv.core.model.personalization.SportEventStatus
import pl.ketchupplayer.tv.core.model.personalization.Team
import pl.ketchupplayer.tv.core.model.personalization.WhyChip
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Pasy personalizacji na Home: „Bo oglądasz”, „Twoje drużyny” i pytanie o obserwowanie. Sterowanie pilotem (D-pad,
 * OK), ocena z przytrzymania, karta bez kanału, która nie jest martwym kaflem, oraz zrzut. Dane zmyślone.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h900dp-xhdpi")
class PersonalRowsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun utc(
        day: Int,
        hour: Int,
    ) = LocalDateTime.of(2026, 3, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val now = utc(21, 10)

    private fun entry(
        id: String,
        title: String,
    ) = VodEntry(
        VodItem(id, ProviderId("p"), VodKind.MOVIE, title, "ref", year = 2025, rating = 7.4, genre = "Kryminał"),
    )

    private val because =
        listOf(
            BecauseEntry(entry("d", "Mgła na molo"), listOf(WhyChip(FeatureNamespace.DIRECTOR, "Anna Testowa"))),
            BecauseEntry(
                entry("e", "Druga latarnia"),
                listOf(WhyChip(FeatureNamespace.GENRE, "Kryminał")),
                liked = true,
            ),
            BecauseEntry(entry("x", "Sezon ogórkowy"), emptyList(), explore = true),
        )

    private fun card(
        id: String,
        title: String,
        kickoff: Long,
        channels: List<SportChannel>,
        status: SportEventStatus = SportEventStatus.SCHEDULED,
        live: Boolean = false,
    ) = SportCard(
        id,
        title,
        kickoff,
        kickoff + 2 * 3_600_000L,
        live,
        false,
        status,
        channels,
        listOf("wisla-nadmorska"),
        now,
    )

    private val one = listOf(SportChannel("ch_sport_1", "Sport Testowy 1", 0.95))
    private val two = one + SportChannel("ch_sport_2", "Sport Testowy 2", 0.95)
    private val sport =
        listOf(
            card("m1", "Wisła Nadmorska - Hutnik Leśny", utc(21, 19), one),
            card("m2", "Stal Próbna - Wisła Nadmorska", utc(22, 16), emptyList()),
            card("m3", "Wisła Nadmorska - Górnik Zmyślony", utc(28, 14), two, SportEventStatus.POSTPONED),
        )
    private val suggestion =
        FollowSuggestion(listOf(Team("hutnik-lesny", "Hutnik Leśny")), listOf(utc(7, 16), utc(14, 16), utc(21, 9)))

    private val state =
        HomeUiState(
            loading = false,
            hasProviders = true,
            nowMs = now,
            featured = entry("f", "W mieście strachu"),
            because = because,
            sport = sport,
            sportSoon = true,
            followSuggestion = suggestion,
        )

    private class Recorded {
        val details = mutableListOf<String>()
        val channels = mutableListOf<String>()
        var guide = 0
        val likes = mutableListOf<Pair<String, Boolean>>()
        val hidden = mutableListOf<String>()
        val followed = mutableListOf<List<String>>()
        val declined = mutableListOf<List<String>>()
    }

    private fun show(
        s: HomeUiState = state,
        r: Recorded = Recorded(),
    ): Recorded {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                Box(Modifier.fillMaxSize().background(KetchupTheme.colors.background).padding(16.dp)) {
                    HomeScreen(
                        s,
                        { r.channels += it },
                        { r.guide++ },
                        {},
                        { r.details += it },
                        {},
                        PersonalActions(
                            onLike = { id, liked -> r.likes += id to liked },
                            onHide = { r.hidden += it },
                            onFollow = { r.followed += it },
                            onDecline = { r.declined += it },
                        ),
                    )
                }
            }
        }
        compose.waitForIdle()
        return r
    }

    private fun key(k: Key) {
        compose.onRoot().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    /** Klawisz w otwartym oknie wyboru (okno jest osobnym korzeniem drzewa). */
    private fun dialogKey(k: Key) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(k) }
        compose.waitForIdle()
    }

    /** Home to leniwa lista: element poniżej ekranu trzeba przewinąć w pole widzenia (pilot robi to fokusem). */
    private fun reveal(text: String) {
        compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text, substring = true))
        compose.waitForIdle()
    }

    private fun clickable(text: String) = compose.onAllNodesWithText(text, substring = true).filter(hasClickAction())[0]

    @Test
    fun pasyMajaPrawdziwePowodyGodzinyIStany() {
        show()
        compose.onNodeWithText("Bo oglądasz").assertIsDisplayed()
        compose.onNodeWithText("Reżyser: Anna Testowa").assertExists()
        compose.onNodeWithText("Lubisz to").assertExists()
        compose.onNodeWithText("Dla odmiany").assertExists()
        compose.onNodeWithText("Twoje drużyny").assertExists()
        compose.onNodeWithText("Dziś 20:00").assertExists()
        compose.onNodeWithText("Jutro 17:00").assertExists()
        compose.onNodeWithText("sob. 28.03, 15:00").assertExists()
        compose.onNodeWithText("Termin zmieniony").assertExists()
        compose.onNodeWithText("Kanał niepotwierdzony", substring = true).assertExists()
        compose.onNodeWithText("Sport Testowy 1, Sport Testowy 2").assertExists()
        reveal("Obserwować drużynę?")
        compose.onNodeWithText("Obserwować drużynę?").assertIsDisplayed()
        compose.onNodeWithText("7.03, 14.03, 21.03", substring = true).assertExists()
    }

    @Test
    fun bezPersonalizacjiPasowNieMa() {
        show(state.copy(because = emptyList(), sport = emptyList(), followSuggestion = null))
        listOf(TAG_BECAUSE_ROW, TAG_SPORT_ROW, TAG_FOLLOW_CARD).forEach {
            assertEquals(it, 0, compose.onAllNodesWithTag(it).fetchSemanticsNodes().size)
        }
    }

    /** Pilot: z hero w dół do meczów, w prawo po kartach, OK otwiera kanał albo (bez kanału) Na żywo. */
    @Test
    fun dpadPoKartachMeczowOtwieraKanalAlboNaZywo() {
        val r = show()
        key(Key.DirectionDown) // „Mam czas na…”
        key(Key.DirectionDown) // Twoje drużyny (mecz w ciągu 48 h jest wysoko)
        key(Key.DirectionCenter)
        assertEquals(listOf("ch_sport_1"), r.channels)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals("karta bez kanału nie jest martwa: prowadzi do Na żywo", 1, r.guide)
        assertEquals(listOf("ch_sport_1"), r.channels)
        key(Key.DirectionLeft)
        key(Key.DirectionCenter)
        assertEquals(listOf("ch_sport_1", "ch_sport_1"), r.channels)
    }

    @Test
    fun meczNaDwochKanalachPytaOKanal() {
        val r = show()
        clickable("Wisła Nadmorska - Górnik Zmyślony").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Wybierz kanał").assertExists()
        dialogKey(Key.DirectionDown) // fokus startuje na pierwszym kanale
        dialogKey(Key.DirectionCenter)
        compose.waitForIdle()
        assertEquals(listOf("ch_sport_2"), r.channels)
    }

    /** Pilot: w dół do „Bo oglądasz”, w prawo, OK otwiera szczegóły właściwej pozycji; BACK/GÓRA nie gubi fokusu. */
    @Test
    fun dpadPoPasieBoOgladaszOtwieraSzczegoly() {
        val r = show()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        key(Key.DirectionDown) // Bo oglądasz
        key(Key.DirectionCenter)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(listOf("d", "e"), r.details)
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        assertTrue("GÓRA wraca do meczów", r.channels.isNotEmpty() || r.guide > 0)
    }

    @Test
    fun przytrzymanieOtwieraOceneZPowodemAWyborJestPrzekazywany() {
        val r = show()
        clickable("Mgła na molo").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Dlaczego to widzę: Reżyser: Anna Testowa").assertExists()
        dialogKey(Key.DirectionCenter) // fokus startuje na „Lubię to”
        assertEquals(listOf("d" to true), r.likes)

        clickable("Druga latarnia").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Cofnij „Lubię to”").assertExists()
        dialogKey(Key.DirectionDown)
        dialogKey(Key.DirectionCenter)
        assertEquals(listOf("e"), r.hidden)
        assertEquals("zwykłe OK nadal otwiera szczegóły, ocena tego nie zmienia", emptyList<String>(), r.details)
    }

    @Test
    fun pytanieODruzyneNieObserwujeSamoIPrzekazujeDecyzje() {
        val r = show()
        assertTrue("samo wyświetlenie pytania niczego nie zmienia", r.followed.isEmpty() && r.declined.isEmpty())
        reveal("Nie, dziękuję")
        clickable("Nie, dziękuję").performClick()
        assertEquals(listOf(listOf("hutnik-lesny")), r.declined)
        clickable("Obserwuj: Hutnik Leśny").performClick()
        assertEquals(listOf(listOf("hutnik-lesny")), r.followed)
    }

    @Test
    fun paraDruzynMaPrzyciskDlaKazdejIDlaObu() {
        val pair = suggestion.copy(teams = suggestion.teams + Team("wisla-nadmorska", "Wisła Nadmorska"))
        val r = show(state.copy(followSuggestion = pair))
        reveal("Obserwuj obie")
        clickable("Obserwuj obie").performClick()
        assertEquals(listOf(listOf("hutnik-lesny", "wisla-nadmorska")), r.followed)
        compose.onNodeWithText("Obserwuj: Wisła Nadmorska").assertExists()
    }

    @Test
    fun godzinaMeczuJestWarszawskaTakzePoZmianieCzasu() {
        show(
            state.copy(
                nowMs = utc(28, 10),
                because = emptyList(),
                followSuggestion = null,
                sport =
                    listOf(
                        card("z1", "Wisła Nadmorska - Hutnik Leśny", utc(28, 19), one),
                        card("z2", "Wisła Nadmorska - Stal Próbna", utc(29, 19), one),
                    ),
            ),
        )
        compose.onNodeWithText("Dziś 20:00").assertExists()
        compose.onNodeWithText("Jutro 21:00").assertExists()
    }

    @Test
    fun screenshotPasowPersonalizacji() {
        show()
        compose.onNodeWithTag(TAG_BECAUSE_ROW).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/home_personal.png")
    }
}
