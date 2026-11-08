package pl.ketchupplayer.tv.feature.vod

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import pl.ketchupplayer.tv.core.designsystem.FocusItem
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ShellPreview
import pl.ketchupplayer.tv.core.model.VodItem
import pl.ketchupplayer.tv.core.model.VodKind

private fun series(
    n: Int,
    title: String,
    category: String,
    year: Int,
    added: Long,
) = VodEntry(
    VodItem(
        "p:s$n",
        PID,
        VodKind.SERIES,
        title,
        "xtream:series:$n",
        year = year,
        category = category,
        addedMs = added,
    ),
)

/** Seriale są nowsze od filmów, więc w siatce (Najnowsze) stoją w pierwszym rzędzie obok filmów. */
private val SERIALE =
    listOf(
        series(1, "Rodzina Kowalskich", "Komedia", 2011, 20),
        series(2, "Komisariat 7", "Kryminał", 2021, 21),
    )

private val KATALOG = MOVIES + SERIALE

/** Najnowszy na Mojej liście jest „Złote miasto” (p:m7). */
private val MOJA_LISTA = listOf(MOVIES[6], MOVIES[1], SERIALE[0])

private val KATEGORIE = listOf("Wszystkie", "Ostatnio dodane", "Dramat", "Akcja", "Komedia", "Kryminał")
private val SZYNA = listOf("Profil", "Szukaj", "Home", "Na żywo", "Przewodnik", "Filmy", "Seriale", "Moja lista")

private const val SETTLE_MS = 600L
private const val MAX_LEFT = 6

/**
 * Plan 1.3 (D-pad na każdym ekranie): tryby MOJA LISTA i SZUKAJ tej samej siatki co Filmy. Prawdziwe zdarzenia
 * D-pad / OK / BACK na ekranie w powłoce z szyną; sprawdzamy, gdzie jest fokus i co się stało (otwarcie szczegółów,
 * zdarzenie wyszukiwania, powrót na szynę). Tekst frazy wpisuje klawiatura ekranowa (IME), więc w teście to
 * `performTextInput` na polu, do którego fokus doszedł pilotem; reszta ścieżki idzie klawiszami.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class VodBrowseModesScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private val queries = mutableListOf<String>()
    private var providers = 0

    private fun stateOf(
        mode: VodMode,
        repo: FakeVodRepo,
        query: String = "",
    ): VodBrowseUiState {
        val vm = VodBrowseViewModel(repo, FakeGroups())
        vm.bind(mode)
        if (query.isNotEmpty()) vm.onEvent(VodBrowseEvent.Search(query))
        return runBlocking { vm.state.first { !it.loading && it.mode == mode && it.query == query } }
    }

    /** [results]: stan po wpisaniu danej frazy (ViewModel liczy go poza ekranem, tu podstawiamy gotowy). */
    private fun show(
        initial: VodBrowseUiState,
        railId: String,
        results: Map<String, VodBrowseUiState> = emptyMap(),
    ) {
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                // TV startuje w trybie nie-dotykowym, a Robolectric w dotykowym; w dotykowym FocusItem (clickable) nie
                // pokazuje fokusu w semantyce, więc test byłby ślepy. Przełączenie w SideEffect następuje przed
                // pierwszym żądaniem fokusu ekranu (efekty startują po zastosowaniu kompozycji).
                val modes = LocalInputModeManager.current
                SideEffect { modes.requestInputMode(InputMode.Keyboard) }
                var state by remember { mutableStateOf(initial) }
                ShellPreview(railId) {
                    VodBrowseScreen(
                        state,
                        { event ->
                            if (event is VodBrowseEvent.Search) {
                                queries += event.query
                                results[event.query]?.let { state = it }
                            }
                        },
                        { opened += it },
                        { providers++ },
                    )
                }
            }
        }
        // Bez przesuwania zegara: początkowy fokus ekran ponawia przez kilkanaście klatek.
        compose.waitForIdle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS) // animacje paneli i ponowienia fokusu
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun describe(node: SemanticsNode): String {
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val isField = node.config.contains(SemanticsProperties.EditableText)
        return listOfNotNull(
            description?.let { "opis=$it" },
            text?.let { "tekst=$it" },
            "pole".takeIf { isField },
        ).joinToString(", ")
    }

    private fun focused(): List<String> = compose.onAllNodes(isFocused()).fetchSemanticsNodes().map { describe(it) }

    private fun focusedHas(part: String) = focused().any { it.contains(part) }

    private fun assertFocus(part: String) = assertTrue("Fokus: ${focused()}, oczekiwano: $part", focusedHas(part))

    private fun onRail() = focused().any { f -> SZYNA.any { f.startsWith("opis=$it") } }

    private fun onCategory() = focused().any { f -> KATEGORIE.any { f == "tekst=$it" } }

    /** Naturalny ruch użytkownika ku treści: z szyny i z kategorii w prawo, z reszty w dół. */
    private fun nextKey() = if (onRail() || onCategory()) Key.DirectionRight else Key.DirectionDown

    /** Przycisk „Szukaj” klawiatury ekranowej: aplikacja przenosi wtedy fokus z pola w dół, do wyników. */
    private fun imeSearch() {
        compose.onNode(hasSetTextAction()).performImeAction()
        settle()
    }

    /**
     * Idzie pilotem do pozycji z [target] (max [maxSteps] ruchów); zwraca trasę fokusu do opisu błędu. Z pola
     * wyszukiwania wychodzi przyciskiem „Szukaj” klawiatury ekranowej, bo strzałki zostają w polu.
     */
    private fun walkTo(
        target: String,
        maxSteps: Int = 3,
    ): String {
        val trace = mutableListOf(focused().toString())
        repeat(maxSteps) {
            if (!focusedHas(target)) {
                if (focusedHas("pole")) imeSearch() else press(nextKey())
                trace += focused().toString()
            }
        }
        val route = trace.joinToString(" -> ")
        println("TRASA FOKUSU do '$target': $route")
        assertTrue("Nie dotarto do '$target' w $maxSteps ruchach: $route", focusedHas(target))
        return route
    }

    /** LEWO, aż fokus trafi na szynę; po drodze fokus nie może zniknąć. */
    private fun leaveToRail(): String {
        val trace = mutableListOf(focused().toString())
        repeat(MAX_LEFT) {
            if (!onRail()) {
                press(Key.DirectionLeft)
                trace += focused().toString()
                assertTrue("Fokus zniknął po LEWO: ${trace.joinToString(" -> ")}", focused().isNotEmpty())
            }
        }
        val route = trace.joinToString(" -> ")
        println("TRASA FOKUSU na szynę: $route")
        assertTrue("LEWO nie doprowadziło na szynę: $route", onRail())
        return route
    }

    private fun filledMyList() = stateOf(VodMode.MYLIST, FakeVodRepo(KATALOG, MOJA_LISTA))

    private fun emptyMyList() = stateOf(VodMode.MYLIST, FakeVodRepo(KATALOG))

    private fun search(vararg phrases: String): Pair<VodBrowseUiState, Map<String, VodBrowseUiState>> {
        val repo = FakeVodRepo(KATALOG)
        return stateOf(VodMode.SEARCH, repo) to phrases.associateWith { stateOf(VodMode.SEARCH, repo, it) }
    }

    // --- SONDA (tymczasowa): tryb wejścia a początkowy fokus ---

    @Test
    fun sondaTrybuWejscia() {
        val a = FocusRequester()
        val b = FocusRequester()
        var clicks = ""
        var modes: InputModeManager? = null
        compose.setContent {
            KetchupTheme(device = DeviceClass.TV) {
                modes = LocalInputModeManager.current
                Column {
                    FocusItem({ clicks += "A" }, focusRequester = a) { Text("A") }
                    FocusItem({ clicks += "B" }, focusRequester = b) { Text("B") }
                }
            }
        }
        compose.waitForIdle()

        fun state(label: String) {
            println("SONDA1 $label: tryb=${modes?.inputMode} fokus=${focused()} klikniecia='$clicks'")
        }

        state("start")
        compose.runOnIdle { println("SONDA1 requestFocus A: ${a.requestFocus()}") }
        state("po requestFocus A")
        press(Key.DirectionCenter)
        state("po OK")
        press(Key.ShiftLeft)
        state("po ShiftLeft")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        state("po DOL+OK")
        val switched = compose.runOnIdle { modes?.requestInputMode(InputMode.Keyboard) }
        state("po requestInputMode(Keyboard)=$switched")
        compose.runOnIdle { println("SONDA1 requestFocus B: ${b.requestFocus()}") }
        state("po requestFocus B")
        press(Key.DirectionCenter)
        state("po OK na B")
    }

    @Test
    fun sondaPoczatkowegoFokusuEkranu() {
        show(stateOf(VodMode.MOVIES, FakeVodRepo(KATALOG)), "movies")
        println("SONDA2 po show: ${focused()}")
        press(Key.ShiftLeft)
        println("SONDA2 po ShiftLeft: ${focused()}")
        press(Key.DirectionRight)
        println("SONDA2 po PRAWO: ${focused()}")
        press(Key.DirectionCenter)
        println("SONDA2 po OK: otwarto=$opened")
        press(Key.DirectionLeft)
        println("SONDA2 po LEWO: ${focused()}")
    }

    // --- Moja lista z pozycjami ---

    @Test
    fun `moja lista - kategorie, PRAWO na najnowszy plakat, OK otwiera szczegoly`() {
        show(filledMyList(), "mylist")
        press(Key.ShiftLeft) // pierwszy klawisz po starcie tylko przełącza tryb wejścia
        assertFocus("Wszystkie")
        press(Key.DirectionRight)
        assertFocus("Złote miasto")
        press(Key.DirectionCenter)
        assertEquals(listOf("p:m7"), opened)
    }

    @Test
    fun `moja lista - LEWO z pierwszej kolumny wysuwa kategorie, kolejne LEWO przenosi na szyne`() {
        show(filledMyList(), "mylist")
        press(Key.ShiftLeft)
        press(Key.DirectionRight)
        assertFocus("Złote miasto")
        compose.onNodeWithText("Wszystkie").assertDoesNotExist() // na miniaturach kategorie są schowane
        press(Key.DirectionLeft)
        assertFocus("Wszystkie")
        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Moja lista").assertIsFocused()
    }

    @Test
    fun `moja lista - BACK z siatki wysuwa kategorie, drugi BACK przenosi na szyne`() {
        show(filledMyList(), "mylist")
        press(Key.ShiftLeft)
        press(Key.DirectionRight)
        assertFocus("Złote miasto")
        back()
        assertFocus("Wszystkie")
        back()
        compose.onNodeWithContentDescription("Moja lista").assertIsFocused()
    }

    // --- Moja lista pusta ---

    @Test
    fun `pusta moja lista - BACK przenosi fokus na szyne`() {
        show(emptyMyList(), "mylist")
        compose.onNodeWithText("Moja lista jest pusta", substring = true).assertExists()
        press(Key.ShiftLeft)
        back()
        compose.onNodeWithContentDescription("Moja lista").assertIsFocused()
    }

    /**
     * Zachowanie obecnej implementacji (nie decyzja UX): ekran pusty ma jedną akcję, tę samą co pusty ekran Filmów
     * (przejście do Dostawców). Test pilnuje, że akcja jest osiągalna z szyny samym pilotem i że OK ją wykonuje.
     */
    @Test
    fun `pusta moja lista - z szyny przez pole i akcje Szukaj klawiatury dochodzi sie do akcji, OK ja wykonuje`() {
        show(emptyMyList(), "mylist")
        press(Key.ShiftLeft)
        back() // fokus na szynie, jak po wyborze pozycji z szyny
        compose.onNodeWithContentDescription("Moja lista").assertIsFocused()
        walkTo("Dostawcy", maxSteps = 4)
        press(Key.DirectionCenter)
        assertEquals(1, providers)
    }

    @Test
    fun `pusta moja lista - LEWO z akcji wraca na szyne`() {
        show(emptyMyList(), "mylist")
        press(Key.ShiftLeft)
        back()
        walkTo("Dostawcy", maxSteps = 4)
        leaveToRail()
        compose.onNodeWithContentDescription("Moja lista").assertIsFocused()
    }

    // --- Szukaj ---

    @Test
    fun `szukaj - na starcie filmy i seriale razem, fokus na kategorii`() {
        val (initial, _) = search()
        show(initial, "search")
        press(Key.ShiftLeft)
        assertFocus("Wszystkie")
        compose.onNodeWithText("Komisariat 7").assertExists() // serial
        compose.onNodeWithText("Burza", substring = true).assertExists() // film
    }

    @Test
    fun `szukaj - GORA z kategorii wchodzi w pole, akcja Szukaj klawiatury prowadzi do wyniku`() {
        val (initial, _) = search()
        show(initial, "search")
        press(Key.ShiftLeft)
        press(Key.DirectionUp)
        compose.onNode(hasSetTextAction()).assertIsFocused()
        walkTo("Komisariat 7") // pole -> akcja Szukaj klawiatury -> fokus w dół
        press(Key.DirectionCenter)
        assertEquals(listOf("p:s2"), opened)
    }

    @Test
    fun `szukaj - fraza zaweza wyniki do serialu, ktory pilotem da sie otworzyc`() {
        val (initial, results) = search("kowal")
        show(initial, "search", results)
        press(Key.ShiftLeft)
        press(Key.DirectionUp)
        compose.onNode(hasSetTextAction()).assertIsFocused()
        press(Key.DirectionCenter) // OK w polu otwiera klawiaturę ekranową; fokus zostaje w polu
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNode(hasSetTextAction()).performTextInput("kowal") // IME
        settle()
        imeSearch() // przycisk „Szukaj” klawiatury
        assertEquals(listOf("kowal"), queries)
        compose.onNodeWithText("Rodzina Kowalskich").assertExists()
        compose.onNodeWithText("Burza", substring = true).assertDoesNotExist()
        walkTo("Rodzina Kowalskich")
        press(Key.DirectionCenter)
        assertEquals(listOf("p:s1"), opened)
    }

    @Test
    fun `szukaj - brak wynikow, komunikat i powrot pilotem na szyne bez gubienia fokusu`() {
        val (initial, results) = search("zzz")
        show(initial, "search", results)
        press(Key.ShiftLeft)
        press(Key.DirectionUp)
        compose.onNode(hasSetTextAction()).performTextInput("zzz") // IME
        settle()
        compose.onNodeWithText("Brak wyników dla", substring = true).assertExists()
        imeSearch() // przycisk „Szukaj” klawiatury: fokus idzie w dół, mimo że wyników nie ma
        assertTrue("Fokus zniknął po akcji Szukaj", focused().isNotEmpty())
        press(Key.DirectionRight)
        assertTrue("Fokus zniknął po PRAWO przy braku wyników", focused().isNotEmpty())
        leaveToRail()
        compose.onNodeWithContentDescription("Szukaj").assertIsFocused()
    }

    @Test
    fun `szukaj - BACK z pola wyszukiwania przenosi fokus na szyne`() {
        val (initial, _) = search()
        show(initial, "search")
        press(Key.ShiftLeft)
        press(Key.DirectionUp)
        compose.onNode(hasSetTextAction()).assertIsFocused()
        back()
        compose.onNodeWithContentDescription("Szukaj").assertIsFocused()
    }
}
