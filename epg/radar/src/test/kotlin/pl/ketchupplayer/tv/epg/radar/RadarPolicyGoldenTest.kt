package pl.ketchupplayer.tv.epg.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden master: wartości wygenerowane przez `RadarGuidePolicy.java` ze starej aplikacji (poza repo) na tablicy
 * wejść z wartościami granicznymi. Rozbieżność trzeba uzasadnić (docs/adr/legacy-behavior-decision.md).
 */
class RadarPolicyGoldenTest {
    private val rows =
        checkNotNull(javaClass.getResourceAsStream("/golden/radar_policy.txt"))
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
            .map { it.split("|") }

    @Test
    fun `ocena z opisu jak w starej aplikacji`() {
        val ratings = rows.filter { it[0] == "rating" }
        assertTrue(ratings.size >= 15)
        for (r in ratings) {
            val text = if (r[1] == "<null>") null else r[1]
            val expected = r[2].toInt().takeIf { it >= 0 }
            assertEquals("opis '${r[1]}'", expected, RadarPolicy.ratingTenths(text))
        }
    }

    @Test
    fun `przynaleznosc do okna czasu jak w starej aplikacji`() {
        val now = 1_000_000_000L
        val m = 60_000L
        val w =
            RadarPolicy.Window(
                dayStart = now - 6 * 60 * m,
                dayEnd = now + 18 * 60 * m,
                eveningStart = now + 3 * 60 * m,
                eveningEnd = now + 7 * 60 * m,
            )
        val slotRows = rows.filter { it[0] == "slot" }
        assertEquals(4 * 15, slotRows.size)
        for (r in slotRows) {
            val slot = RadarSlot.valueOf(r[1])
            val actual = RadarPolicy.inSlot(slot, now, now + r[2].toLong(), now + r[3].toLong(), w)
            assertEquals(r.toString(), r[4].toBoolean(), actual)
        }
    }

    @Test
    fun `kolejnosc TOP DNIA bez trzech gatunkow z rzedu jak w starej aplikacji`() {
        val orders = rows.filter { it[0] == "order" }
        assertEquals(7, orders.size)
        for (r in orders) {
            val scores = ints(r[1])
            val genres = ints(r[2])
            val expected = r[3].split(",").filter { it.isNotBlank() }.map { it.toInt() }
            assertEquals(r.toString(), expected, RadarPolicy.balancedTopOrder(scores, genres).toList())
        }
    }

    private fun ints(s: String): IntArray =
        s
            .trim('[', ']')
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toInt() }
            .toIntArray()
}
