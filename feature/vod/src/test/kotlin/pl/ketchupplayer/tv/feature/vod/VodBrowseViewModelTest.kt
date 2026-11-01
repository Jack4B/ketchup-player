package pl.ketchupplayer.tv.feature.vod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VodBrowseViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `szukanie, kategoria i sortowanie w ViewModelu`() =
        runTest {
            val vm = VodBrowseViewModel(FakeVodRepo(MOVIES))
            vm.bind(VodMode.MOVIES)
            vm.state.first { !it.loading }
            // Pusty stan startowy zostaje zastąpiony po pierwszej emisji; sprawdzamy wyniki przez zdarzenia.
            vm.onEvent(VodBrowseEvent.SelectCategory("Akcja"))
            assertEquals(
                listOf("Burza", "Ostatni pociąg", "Czerwona glina"),
                vm.state
                    .first {
                        it.category == "Akcja"
                    }.entries
                    .map { it.item.title },
            )
            vm.onEvent(VodBrowseEvent.Sort(VodSort.RATING))
            assertEquals(
                "Burza",
                vm.state
                    .first { it.sort == VodSort.RATING }
                    .entries
                    .first()
                    .item.title,
            )
            vm.onEvent(VodBrowseEvent.Search("czerw"))
            assertEquals(
                listOf("Czerwona glina"),
                vm.state
                    .first { it.query == "czerw" }
                    .entries
                    .map { it.item.title },
            )
            vm.onEvent(VodBrowseEvent.Search("zażółć"))
            assertEquals(
                0,
                vm.state
                    .first { it.query == "zażółć" }
                    .entries.size,
            )
        }

    /** Regresja: panel Xtream nie zawsze podaje datę dodania; sortowanie „Najnowsze” nie może się wywracać. */
    @Test
    fun `sortowanie najnowsze dziala dla pozycji bez daty dodania`() =
        runTest {
            val noDate = MOVIES.map { it.copy(item = it.item.copy(addedMs = null)) }
            val mixed = noDate.take(2) + MOVIES.drop(2)
            val vm = VodBrowseViewModel(FakeVodRepo(mixed))
            vm.bind(VodMode.MOVIES)
            val state = vm.state.first { !it.loading }
            assertEquals(MOVIES.size, state.entries.size)
            // Z datą najpierw (najnowsza pierwsza), bez daty na końcu.
            assertEquals(
                "Burza",
                state.entries
                    .first()
                    .item.title,
            )
        }
}
