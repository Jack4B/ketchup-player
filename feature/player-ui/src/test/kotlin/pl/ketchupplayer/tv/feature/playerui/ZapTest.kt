package pl.ketchupplayer.tv.feature.playerui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.model.Channel
import pl.ketchupplayer.tv.core.model.ProviderId

class ZapTest {
    private fun ch(
        id: String,
        group: String?,
    ) = Channel(id, ProviderId("p"), id, "http://h.invalid/$id", group = group)

    private val all = listOf(ch("a", "Info"), ch("b", "Sport"), ch("c", "Info"), ch("d", "Info"))

    @Test
    fun `lista zappingu to kanaly z tej samej grupy`() {
        assertEquals(listOf("a", "c", "d"), zapList(all[0], all).map { it.id })
        assertEquals(listOf("b"), zapList(all[1], all).map { it.id })
    }

    /** rc4: dwie listy z grupą „Sport”: CH+/CH− zostaje w liście bieżącego kanału. */
    @Test
    fun `ta sama nazwa grupy w innej liscie to inna grupa`() {
        val a1 = Channel("a1", ProviderId("A"), "A1", "http://a.invalid/1", group = "Sport")
        val a2 = Channel("a2", ProviderId("A"), "A2", "http://a.invalid/2", group = "Sport")
        val b1 = Channel("b1", ProviderId("B"), "B1", "http://b.invalid/1", group = "Sport")
        assertEquals(listOf("a1", "a2"), zapList(a1, listOf(a1, b1, a2)).map { it.id })
        assertEquals(listOf("b1"), zapList(b1, listOf(a1, b1, a2)).map { it.id })
    }

    @Test
    fun `kanal bez grupy dostaje cala liste`() {
        val z = zapList(ch("x", null), all)
        assertEquals(5, z.size)
        assertEquals("x", z.first().id)
    }

    @Test
    fun `sasiedzi zawijaja sie na koncach`() {
        val l = listOf(ch("a", "g"), ch("b", "g"), ch("c", "g"))
        assertEquals("b", neighbour(l, "a", +1)?.id)
        assertEquals("a", neighbour(l, "c", +1)?.id)
        assertEquals("c", neighbour(l, "a", -1)?.id)
    }

    @Test
    fun `brak sasiada gdy jeden kanal, a nieznany biezacy daje pierwszy`() {
        assertNull(neighbour(listOf(ch("a", "g")), "a", +1))
        assertEquals("a", neighbour(listOf(ch("a", "g"), ch("b", "g")), "nie-ma", +1)?.id)
    }

    @Test
    fun `duza grupa jest przycieta ale zawiera biezacy`() {
        val big = (1..2_000).map { ch("k$it", "G") }
        val cur = big.last()
        val z = zapList(cur, big)
        assertTrue(z.size <= 500)
        assertTrue(z.any { it.id == cur.id })
    }
}
