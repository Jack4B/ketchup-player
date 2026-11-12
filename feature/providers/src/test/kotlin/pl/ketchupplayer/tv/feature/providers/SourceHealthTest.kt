package pl.ketchupplayer.tv.feature.providers

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.ketchupplayer.tv.core.model.ProviderId
import pl.ketchupplayer.tv.core.model.ProviderInfo
import pl.ketchupplayer.tv.core.model.ProviderType

class SourceHealthTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun provider(
        channels: Int = 100,
        refreshedHoursAgo: Long? = 1,
        error: String? = null,
    ) = ProviderInfo(
        ProviderId("p"),
        "Lista",
        ProviderType.M3U,
        "host.invalid",
        channels,
        refreshedHoursAgo?.let { now - it * hour },
        error,
    )

    @Test
    fun `swieza lista z kanalami jest sprawna`() {
        val h = SourceHealthRules.of(provider(), now)
        assertEquals(HealthLevel.OK, h.level)
        assertEquals(1L, h.ageHours)
    }

    @Test
    fun `blad ostatniego pobrania ma pierwszenstwo nawet gdy kanaly sa`() {
        assertEquals(HealthLevel.ERROR, SourceHealthRules.of(provider(error = "513"), now).level)
    }

    @Test
    fun `pusta lista i nieaktualna lista sa rozpoznawane`() {
        assertEquals(HealthLevel.EMPTY, SourceHealthRules.of(provider(channels = 0), now).level)
        assertEquals(HealthLevel.STALE, SourceHealthRules.of(provider(refreshedHoursAgo = 24 * 8), now).level)
        assertEquals(HealthLevel.STALE, SourceHealthRules.of(provider(refreshedHoursAgo = null), now).level)
    }

    @Test
    fun `granica nieaktualnosci to siedem dni`() {
        assertEquals(HealthLevel.OK, SourceHealthRules.of(provider(refreshedHoursAgo = 24 * 7 - 1), now).level)
        assertEquals(HealthLevel.STALE, SourceHealthRules.of(provider(refreshedHoursAgo = 24 * 7), now).level)
    }
}
