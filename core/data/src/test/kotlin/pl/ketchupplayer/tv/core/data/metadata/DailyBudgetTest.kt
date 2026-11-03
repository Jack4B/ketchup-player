package pl.ketchupplayer.tv.core.data.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBudgetTest {
    @Test
    fun limitDzialaNaZrodloIZeraSieOPolnocy() {
        var day = 10L
        val b = DailyBudget { day }
        assertTrue(b.take("mdblist", 600, 950))
        assertFalse(b.take("mdblist", 400, 950)) // 1000 > 950
        assertTrue(b.take("omdb", 900, 950)) // osobny licznik per źródło
        day = 11L
        assertTrue(b.take("mdblist", 950, 950)) // nowa doba
    }
}
