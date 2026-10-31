package pl.ketchupplayer.tv.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity.CAUTIOUS
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity.FAST
import pl.ketchupplayer.tv.core.policy.FailoverSensitivity.STANDARD
import pl.ketchupplayer.tv.core.policy.IncidentReason.OTHER
import pl.ketchupplayer.tv.core.policy.IncidentReason.REBUFFER

/** Wartości z KONSPEKT §10.1 pkt 4 — muszą zostać dokładnie takie. */
class BkFailoverPolicyTest {
    private val all = listOf(FAST, STANDARD, CAUTIOUS)

    @Test
    fun `minimumStallMs 1500 2500 4500`() =
        assertEquals(listOf(1_500L, 2_500L, 4_500L), all.map(BkFailoverPolicy::minimumStallMs))

    @Test
    fun `cooldown 12 20 30 sekund`() =
        assertEquals(listOf(12_000L, 20_000L, 30_000L), all.map(BkFailoverPolicy::cooldownMs))

    @Test
    fun `progi incydentow rebuffer 1 2 3 inne 2 3 4`() {
        assertEquals(listOf(1, 2, 3), all.map { BkFailoverPolicy.incidentThreshold(it, REBUFFER) })
        assertEquals(listOf(2, 3, 4), all.map { BkFailoverPolicy.incidentThreshold(it, OTHER) })
    }

    @Test
    fun `ponowienia TV 0 1 2 i VOD 1 2 3`() {
        assertEquals(listOf(0, 1, 2), all.map(BkFailoverPolicy::tvRecoveryRetries))
        assertEquals(listOf(1, 2, 3), all.map(BkFailoverPolicy::vodRecoveryRetries))
    }

    @Test
    fun `opoznienie TV tylko 5 10 15 25, domyslnie 25`() {
        assertEquals(listOf(5, 10, 15, 25), BkFailoverPolicy.TV_DELAY_OPTIONS_SECONDS)
        assertEquals(25, BkFailoverPolicy.DEFAULT_TV_DELAY_SECONDS)
        assertEquals(10, BkFailoverPolicy.tvSwitchDelaySeconds(10))
        assertEquals(25, BkFailoverPolicy.tvSwitchDelaySeconds(7))
        assertEquals(25, BkFailoverPolicy.tvSwitchDelaySeconds(0))
    }

    @Test
    fun `wlaczenie wymaga przelacznika globalnego i typu`() {
        val s = FailoverSettings(tv = true, vod = false)
        assertTrue(BkFailoverPolicy.enabled(s, ContentKind.TV))
        assertFalse(BkFailoverPolicy.enabled(s, ContentKind.VOD))
        assertFalse(BkFailoverPolicy.enabled(s.copy(enabled = false), ContentKind.TV))
    }

    @Test
    fun `progi rosna z ostroznoscia`() {
        for (reason in IncidentReason.entries) {
            val t = all.map { BkFailoverPolicy.incidentThreshold(it, reason) }
            assertEquals(t.sorted(), t)
        }
    }
}
