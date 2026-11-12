package pl.ketchupplayer.tv.core.policy.personalization

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Czas w UI personalizacji: w bazie zawsze UTC, na ekranie zawsze Europe/Warsaw (z czasem letnim i zimowym). */
object WarsawTime {
    val ZONE: ZoneId = ZoneId.of("Europe/Warsaw")

    fun at(utcMs: Long): ZonedDateTime = Instant.ofEpochMilli(utcMs).atZone(ZONE)

    fun localDate(utcMs: Long): LocalDate = at(utcMs).toLocalDate()

    fun sameDay(
        aUtcMs: Long,
        bUtcMs: Long,
    ): Boolean = localDate(aUtcMs) == localDate(bUtcMs)

    /** Godzina `HH:mm` w Warszawie. */
    fun hhmm(utcMs: Long): String = at(utcMs).let { "%02d:%02d".format(it.hour, it.minute) }

    /** Data `d.MM` w Warszawie (np. „14.03”). */
    fun dayMonth(utcMs: Long): String = at(utcMs).let { "%d.%02d".format(it.dayOfMonth, it.monthValue) }

    /** Względny dzień wydarzenia wobec [nowMs]: dziś, jutro albo dalszy termin. */
    fun dayKind(
        utcMs: Long,
        nowMs: Long,
    ): DayKind {
        val days = localDate(utcMs).toEpochDay() - localDate(nowMs).toEpochDay()
        return when (days) {
            0L -> DayKind.TODAY
            1L -> DayKind.TOMORROW
            else -> DayKind.OTHER
        }
    }

    /** Dzień tygodnia 1 (poniedziałek) do 7 (niedziela) w Warszawie. */
    fun dayOfWeek(utcMs: Long): Int = at(utcMs).dayOfWeek.value

    /** Najbliższa godzina [hour]:[minute] w Warszawie po [nowMs] (do planowania prac nocnych). */
    fun nextAt(
        nowMs: Long,
        hour: Int,
        minute: Int,
    ): Long {
        val now = at(nowMs)
        var next = now.toLocalDate().atTime(hour, minute).atZone(ZONE)
        if (!next.isAfter(now)) {
            next =
                now
                    .toLocalDate()
                    .plusDays(1)
                    .atTime(hour, minute)
                    .atZone(ZONE)
        }
        return next.toInstant().toEpochMilli()
    }
}

enum class DayKind { TODAY, TOMORROW, OTHER }
