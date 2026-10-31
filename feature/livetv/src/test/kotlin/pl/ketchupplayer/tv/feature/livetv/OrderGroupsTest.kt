package pl.ketchupplayer.tv.feature.livetv

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderGroupsTest {
    @Test
    fun `polskie grupy na poczatku, reszta alfabetycznie`() {
        val out = orderGroups(listOf("UK| NEWS", "PL| SPORTOWE", "Info", "Polska", "PLUS", "PL-VIP| X"))
        assertEquals(listOf("PL-VIP| X", "PL| SPORTOWE", "Polska", "Info", "PLUS", "UK| NEWS"), out)
    }
}
