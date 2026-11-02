package pl.ketchupplayer.tv.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRulesTest {
    private val film = 6_000_000L // 100 min

    @Test
    fun `wznowienie z pozycji w srodku filmu`() {
        assertEquals(1_800_000L, ResumeRules.resumePosition(1_800_000, film))
    }

    /** Wartości graniczne ze starej aplikacji: 1 s i 97%. */
    @Test
    fun `granice wznowienia to 1 s i 97 procent`() {
        assertEquals(0L, ResumeRules.resumePosition(999, film))
        assertEquals(1_000L, ResumeRules.resumePosition(1_000, film))
        assertEquals(5_819_999L, ResumeRules.resumePosition(5_819_999, film)) // tuż poniżej 97%
        assertEquals(0L, ResumeRules.resumePosition(5_820_000, film)) // dokładnie 97%
        assertEquals(0L, ResumeRules.resumePosition(film, film))
    }

    @Test
    fun `ukonczony i nieznana dlugosc`() {
        assertEquals(0L, ResumeRules.resumePosition(1_800_000, film, completed = true))
        assertEquals(1_800_000L, ResumeRules.resumePosition(1_800_000, 0)) // długość nieznana: wznawiamy
    }

    /** Oznaczenie „kontynuuj”: od 30 s do 95%. */
    @Test
    fun `w trakcie od 30 sekund do 95 procent`() {
        assertFalse(ResumeRules.inProgress(29_999, film))
        assertTrue(ResumeRules.inProgress(30_000, film))
        assertTrue(ResumeRules.inProgress(5_699_999, film))
        assertFalse(ResumeRules.inProgress(5_700_000, film)) // 95%
        assertFalse(ResumeRules.inProgress(1_800_000, film, completed = true))
        assertTrue(ResumeRules.inProgress(60_000, 0))
    }

    @Test
    fun `obejrzany od 97 procent`() {
        assertFalse(ResumeRules.completed(5_819_999, film))
        assertTrue(ResumeRules.completed(5_820_000, film))
        assertFalse(ResumeRules.completed(5_900_000, 0)) // długość nieznana
    }
}
