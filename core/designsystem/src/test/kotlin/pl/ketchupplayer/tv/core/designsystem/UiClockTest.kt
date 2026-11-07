package pl.ketchupplayer.tv.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class UiClockTest {
    @Test
    fun stalyCzasPokazujeGodzineWJawnejStrefie() {
        val instant = Instant.parse("2026-01-15T11:34:00Z")
        assertEquals("12:34", UiClock.fixed(instant, ZoneId.of("Europe/Warsaw")).hhmm())
        assertEquals("11:34", UiClock.fixed(instant, ZoneOffset.UTC).hhmm())
    }

    @Test
    fun strefaUwzglednaCzasLetni() {
        val summer = Instant.parse("2026-07-01T10:34:00Z")
        assertEquals("12:34", UiClock.fixed(summer, ZoneId.of("Europe/Warsaw")).hhmm())
    }

    @Test
    fun zamrozonyZegarPokazujeStalaGodzineBezWzgleduNaCzasUruchomienia() {
        assertEquals("12:34", UiClock.Frozen.hhmm())
        assertEquals("12:34", UiClock.Frozen.hhmm())
    }

    @Test
    fun zegarZrodlowyCzytaCzasPrzyKazdymOdczycieWiecSieOdswieza() {
        var now = Instant.parse("2026-01-15T10:00:00Z")
        val clock = UiClock({ now }, { ZoneOffset.UTC })
        assertEquals("10:00", clock.hhmm())
        now = now.plusSeconds(60)
        assertEquals("10:01", clock.hhmm())
    }

    @Test
    fun zegarZrodlowyCzytaStrefePrzyKazdymOdczycie() {
        var zone: ZoneId = ZoneOffset.UTC
        val clock = UiClock({ Instant.parse("2026-01-15T10:00:00Z") }, { zone })
        assertEquals("10:00", clock.hhmm())
        zone = ZoneId.of("Europe/Warsaw")
        assertEquals("11:00", clock.hhmm())
    }

    @Test
    fun zegarSystemowyPokazujeBiezacyCzasUrzadzenia() {
        val before = LocalTime.now().withSecond(0).withNano(0)
        val shown = UiClock.System.hhmm()
        val after = LocalTime.now().withSecond(0).withNano(0)
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        assertEquals(true, shown == before.format(fmt) || shown == after.format(fmt))
    }

    @Test
    fun godzinaPoUplywieOdstepuLiczonaWJawnejStrefie() {
        val clock = UiClock.fixed(Instant.parse("2026-01-15T11:34:00Z"), ZoneId.of("Europe/Warsaw"))
        assertEquals("12:34", clock.hhmmAfter(0))
        assertEquals("13:38", clock.hhmmAfter(3_840_000))
    }

    @Test
    fun godzinaPoUplywieOdstepuPrzechodziPrzezPolnoc() {
        val clock = UiClock.fixed(Instant.parse("2026-01-15T22:50:00Z"), ZoneId.of("Europe/Warsaw"))
        assertEquals("23:50", clock.hhmm())
        assertEquals("00:54", clock.hhmmAfter(64 * 60_000L))
    }

    @Test
    fun biezacyCzasWMilisekundachPochodziZeZrodla() {
        val instant = Instant.parse("2026-01-15T11:34:00Z")
        assertEquals(instant.toEpochMilli(), UiClock.fixed(instant, ZoneOffset.UTC).nowMs())
        assertEquals(UiClock.Frozen.nowMs(), UiClock.Frozen.nowMs())
    }

    @Test
    fun zegarSystemowyMilisekundyBliskoCzasuSystemowego() {
        val before = System.currentTimeMillis()
        val shown = UiClock.System.nowMs()
        val after = System.currentTimeMillis()
        assertEquals(true, shown in (before - 5)..(after + 5))
    }
}
