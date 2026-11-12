package pl.ketchupplayer.tv.feature.vod

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
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

/**
 * Moja lista używa tej samej siatki co Filmy: sortowanie „Najnowsze” = malejąco po czasie dodania pozycji do katalogu
 * (`addedMs`), a nie po kolejności na liście (VodBrowseViewModel, VodSort.NEWEST). Czasy są jawne i różne, a kolejność
 * na liście celowo inna niż oczekiwana w siatce, żeby test dowodził sortowania, a nie kolejności wejściowej.
 */
private val MOJA_LISTA =
    listOf(
        movie(1, "Most nad rzeką", "Dramat", 2018, 6.0, added = 100),
        series(1, "Rodzina Kowalskich", "Komedia", 2011, 500),
        movie(7, "Złote miasto", "Dramat", 2024, 8.6, added = 300),
    )

/** Oczekiwana kolejność plakatów w siatce Mojej listy (wpisana ręcznie z czasów powyżej: 500, 300, 100). */
private val MOJA_LISTA_W_SIATCE = listOf("Rodzina Kowalskich", "Złote miasto", "Most nad rzeką")

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

    private var inputModes: InputModeManager? = null
    private val opened = mutableListOf<String>()
    private val queries = mutableListOf<String>()
    private var providers = 0

    private fun stateOf(
        mode: VodMode,
        repo: FakeVodRepo,
        query: String = "",
    ): VodBrowseUiState {
        val vm = VodBrowseViewModel(repo, FakeGroups(), FakePlaylists())
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
                inputModes = modes
                SideEffect { if (modes.inputMode != InputMode.Keyboard) modes.requestInputMode(InputMode.Keyboard) }
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
        // Kontrola przełączenia: bez trybu klawiszowego semantyka fokusu FocusItem jest pusta, a asercje ślepe.
        check(inputModes?.inputMode == InputMode.Keyboard) {
            "Tryb wejścia po show() to ${inputModes?.inputMode}, oczekiwano Keyboard (jak TV)"
        }
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
        assertTrue("LEWO nie doprowadziło na szynę: $route", onRail())
        return route
    }

    private fun filledMyList() = stateOf(VodMode.MYLIST, FakeVodRepo(KATALOG, MOJA_LISTA))

    private fun emptyMyList() = stateOf(VodMode.MYLIST, FakeVodRepo(KATALOG))

    private fun search(vararg phrases: String): Pair<VodBrowseUiState, Map<String, VodBrowseUiState>> {
        val repo = FakeVodRepo(KATALOG)
        return stateOf(VodMode.SEARCH, repo) to phrases.associateWith { stateOf(VodMode.SEARCH, repo, it) }
    }

    // --- Moja lista z pozycjami ---

    @Test
    fun `moja lista - kategorie, PRAWO na najnowszy plakat, OK otwiera wlasnie ten element`() {
        show(filledMyList(), "mylist")
        assertFocus("Wszystkie") // fokus startowy ekranu na kategorii
        press(Key.DirectionRight)
        assertFocus(MOJA_LISTA_W_SIATCE[0])
        press(Key.DirectionCenter)
        assertEquals(listOf("p:s1"), opened) // serial z czasem 500, nie film z początku listy
    }

    @Test
    fun `moja lista - kolejnosc plakatow wg czasu dodania, OK otwiera ten, na ktorym jest fokus`() {
        show(filledMyList(), "mylist")
        press(Key.DirectionRight)
        assertFocus(MOJA_LISTA_W_SIATCE[0])
        press(Key.DirectionRight)
        assertFocus(MOJA_LISTA_W_SIATCE[1])
        press(Key.DirectionRight)
        assertFocus(MOJA_LISTA_W_SIATCE[2])
        press(Key.DirectionCenter)
        assertEquals(listOf("p:m1"), opened) // trzeci w siatce, a pierwszy na liście wejściowej
        press(Key.DirectionLeft)
        assertFocus(MOJA_LISTA_W_SIATCE[1])
        press(Key.DirectionCenter)
        assertEquals(listOf("p:m1", "p:m7"), opened)
    }

    @Test
    fun `moja lista - LEWO z pierwszej kolumny wysuwa kategorie, kolejne LEWO przenosi na szyne`() {
        show(filledMyList(), "mylist")
        press(Key.DirectionRight)
        assertFocus(MOJA_LISTA_W_SIATCE[0])
        compose.onNodeWithText("Wszystkie").assertDoesNotExist() // na miniaturach kategorie są schowane
        press(Key.DirectionLeft)
        assertFocus("Wszystkie")
        press(Key.DirectionLeft)
        compose.onNodeWithContentDescription("Moja lista").assertIsFocused()
    }

    @Test
    fun `moja lista - BACK z siatki wysuwa kategorie, drugi BACK przenosi na szyne`() {
        show(filledMyList(), "mylist")
        press(Key.DirectionRight)
        assertFocus(MOJA_LISTA_W_SIATCE[0])
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

    /**
     * Obecna trasa (nie decyzja UX): LEWO z akcji „Dostawcy” wchodzi w pole wyszukiwania, w którym strzałki zostają
     * (LEWO w polu nie zamyka formularza, KONSPEKT §formularze), a na szynę prowadzi BACK. Wymagania nie nakazują
     * bezpośredniego powrotu LEWO z akcji; wygodę tej trasy ocenia się na boxie (docs/TEST-CHECKLIST.md).
     */
    @Test
    fun `pusta moja lista - LEWO z akcji wchodzi w pole wyszukiwania, BACK z pola wraca na szyne`() {
        show(emptyMyList(), "mylist")
        press(Key.ShiftLeft)
        back() // szyna
        walkTo("Dostawcy", maxSteps = 4)
        press(Key.DirectionLeft)
        assertTrue("LEWO z akcji ma prowadzić do pola: ${focused()}", focusedHas("pole"))
        press(Key.DirectionLeft)
        assertTrue("strzałki zostają w polu: ${focused()}", focusedHas("pole"))
        back()
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
