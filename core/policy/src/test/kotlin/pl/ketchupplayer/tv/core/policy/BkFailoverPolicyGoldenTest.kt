package pl.ketchupplayer.tv.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden master: wartości wygenerowane przez `BkFailoverPolicy.java` ze starej aplikacji (poza repo) dla tablicy
 * wejść z wartościami granicznymi. Zmiana wyniku = czerwony test (docs/KONSPEKT.md §10).
 */
class BkFailoverPolicyGoldenTest {
    private val rows =
        checkNotNull(javaClass.getResourceAsStream("/golden/bk_failover_policy.txt"))
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
            .map { it.split("|") }

    private fun sens(s: String) =
        when (s) {
            "fast" -> FailoverSensitivity.FAST
            "cautious" -> FailoverSensitivity.CAUTIOUS
            else -> FailoverSensitivity.STANDARD
        }

    @Test
    fun `czulosc, progi, cooldown i ponowienia jak w starej aplikacji`() {
        val sensRows = rows.filter { it[0] == "sens" }
        assertTrue(sensRows.size >= 7)
        for (r in sensRows) {
            val s = sens(r[1])
            assertEquals("nazwa ${r[1]}", r[2], s.name.lowercase())
            assertEquals("minimumStall ${r[1]}", r[3].toLong(), BkFailoverPolicy.minimumStallMs(s))
            assertEquals("cooldown ${r[1]}", r[4].toLong(), BkFailoverPolicy.cooldownMs(s))
            assertEquals(
                "próg rebuffer ${r[1]}",
                r[5].toInt(),
                BkFailoverPolicy.incidentThreshold(s, IncidentReason.REBUFFER),
            )
            assertEquals("próg inny ${r[1]}", r[6].toInt(), BkFailoverPolicy.incidentThreshold(s, IncidentReason.OTHER))
            assertEquals("ponowienia TV ${r[1]}", r[7].toInt(), BkFailoverPolicy.tvRecoveryRetries(s))
            assertEquals("ponowienia VOD ${r[1]}", r[8].toInt(), BkFailoverPolicy.vodRecoveryRetries(s))
        }
    }

    @Test
    fun `opoznienie przelaczenia TV dopuszcza tylko 5, 10, 15, 25`() {
        val delayRows = rows.filter { it[0] == "delay" }
        assertTrue(delayRows.size >= 14)
        for (r in delayRows) {
            assertEquals("sekundy ${r[1]}", r[2].toInt(), BkFailoverPolicy.tvSwitchDelaySeconds(r[1].toInt()))
            assertEquals("ms ${r[1]}", r[3].toLong(), BkFailoverPolicy.tvSwitchDelayMs(r[1].toInt()))
        }
    }

    @Test
    fun `brak postepu liczony od pozniejszego z poczatku i ostatniego bufora`() {
        val npRows = rows.filter { it[0] == "noprogress" }
        assertTrue(npRows.size >= 6)
        for (r in npRows) {
            assertEquals(
                r.toString(),
                r[4].toLong(),
                BkFailoverPolicy.tvNoProgressMs(r[1].toLong(), r[2].toLong(), r[3].toLong()),
            )
        }
    }

    @Test
    fun `przelaczniki globalny i per rodzaj jak w starej aplikacji`() {
        val enRows = rows.filter { it[0] == "enabled" }
        assertEquals(5 * 16, enRows.size)
        for (r in enRows) {
            val kind =
                when (r[1]) {
                    "tv" -> ContentKind.TV
                    "vod" -> ContentKind.VOD
                    "episode" -> ContentKind.SERIES
                    else -> null // nieznany rodzaj: stara aplikacja zwraca false
                }
            val settings =
                FailoverSettings(
                    enabled = r[2].toBoolean(),
                    tv = r[3].toBoolean(),
                    vod = r[4].toBoolean(),
                    series = r[5].toBoolean(),
                )
            val actual = kind?.let { BkFailoverPolicy.enabled(settings, it) } ?: false
            assertEquals(r.toString(), r[6].toBoolean(), actual)
        }
    }
}
