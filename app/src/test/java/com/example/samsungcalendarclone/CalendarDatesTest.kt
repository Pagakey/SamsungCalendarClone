package com.example.samsungcalendarclone

import com.example.samsungcalendarclone.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarDatesTest {
    @Test fun leapYearFebruary() {
        val dates = monthDates(YearMonth.of(2024, 2))
        assertEquals(42, dates.size)
        assertEquals(LocalDate.of(2024, 1, 29), dates.first())
        assertTrue(LocalDate.of(2024, 2, 29) in dates)
        assertEquals(29, dates.count { it.monthValue == 2 })
    }
    @Test fun monthStartingSunday() {
        val dates = monthDates(YearMonth.of(2026, 3))
        assertEquals(LocalDate.of(2026, 2, 23), dates.first())
        assertEquals(LocalDate.of(2026, 3, 1), dates[6])
    }
    @Test fun monthStartingMonday() {
        assertEquals(LocalDate.of(2026, 6, 1), monthDates(YearMonth.of(2026, 6)).first())
    }
    @Test fun decemberToJanuary() {
        val month = YearMonth.of(2026, 12).plusMonths(1)
        assertEquals(YearMonth.of(2027, 1), month)
        assertEquals(LocalDate.of(2026, 12, 28), monthDates(month).first())
    }
    @Test fun januaryToDecember() {
        val month = YearMonth.of(2026, 1).minusMonths(1)
        assertEquals(YearMonth.of(2025, 12), month)
        assertEquals(LocalDate.of(2026, 1, 11), monthDates(month).last())
    }
    @Test fun everyMonthHasSixConsecutiveMondayFirstWeeks() {
        for (year in 2020..2030) for (month in 1..12) {
            val dates = monthDates(YearMonth.of(year, month))
            assertEquals(42, dates.distinct().size)
            assertEquals(DayOfWeek.MONDAY, dates.first().dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, dates.last().dayOfWeek)
            dates.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
        }
    }
    private fun event(start: String, end: String, allDay: Boolean = false) = CalendarEvent(1, 1, "Test", Instant.parse(start).toEpochMilli(), Instant.parse(end).toEpochMilli(), allDay, 0, "")
    @Test fun midnightEndDoesNotSpillIntoNextDay() {
        val event = event("2026-10-10T20:00:00Z", "2026-10-11T00:00:00Z")
        assertEquals(LocalDate.of(2026, 10, 10), event.lastDate(ZoneOffset.UTC))
    }
    @Test fun allDayDatesDoNotShiftWithTimeZone() {
        val event = event("2026-10-10T00:00:00Z", "2026-10-12T00:00:00Z", true)
        for (zone in listOf("America/Los_Angeles", "Pacific/Auckland", "Europe/Athens")) {
            assertEquals(LocalDate.of(2026, 10, 10), event.firstDate(ZoneId.of(zone)))
            assertEquals(LocalDate.of(2026, 10, 11), event.lastDate(ZoneId.of(zone)))
        }
    }
    @Test fun timedEventsUseLocalZoneAcrossDaylightSavingChange() {
        val event = event("2026-03-28T23:00:00Z", "2026-03-29T02:00:00Z")
        val grouped = eventsByDate(listOf(event), monthDates(YearMonth.of(2026, 3)), ZoneId.of("Europe/Athens"))
        assertTrue(grouped[LocalDate.of(2026, 3, 28)].isNullOrEmpty())
        assertEquals(listOf(event), grouped[LocalDate.of(2026, 3, 29)])
    }
    @Test fun emptyEventsProduceEmptyDays() {
        assertTrue(eventsByDate(emptyList(), monthDates(YearMonth.of(2026, 10)), ZoneOffset.UTC).values.all { it.isEmpty() })
    }
}
