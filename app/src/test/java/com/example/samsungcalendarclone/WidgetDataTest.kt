package com.example.samsungcalendarclone

import com.example.samsungcalendarclone.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class WidgetDataTest {
    private val athens = ZoneId.of("Europe/Athens")
    private val today = LocalDate.of(2026, 10, 10)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(athens).toInstant().toEpochMilli()
    private fun timed(id: Long, date: LocalDate, from: Int, to: Int) = CalendarEvent(id, 1, "Event $id", at(date, from), at(date, to), false, 0, "")

    @Test fun agendaDropsEndedEventsTodayAndEmptyDays() {
        val allDay = CalendarEvent(9, 1, "Holiday", today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true, 0, "")
        val events = listOf(timed(1, today, 8, 9), timed(2, today, 18, 19), allDay, timed(3, today.plusDays(3), 10, 11))
        val days = agendaDays(events, today, 14, athens, now = at(today, 12), limit = 40)
        assertEquals(listOf(today, today.plusDays(3)), days.map { it.date })
        assertEquals(listOf(9L, 2L), days.first().events.map { it.id })
    }
    @Test fun agendaStopsAtTheLimit() {
        val events = (0L until 10L).map { timed(it + 1, today.plusDays(it), 10, 11) }
        val days = agendaDays(events, today, 14, athens, now = at(today, 0), limit = 4)
        assertEquals(4, days.sumOf { it.events.size })
    }
    @Test fun monthCellsMarkTodayOutsideDaysAndCounts() {
        val cells = monthCells(YearMonth.of(2026, 10), today, mapOf(today to listOf(timed(1, today, 9, 10), timed(2, today, 11, 12))))
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 9, 28), cells.first().date)
        assertTrue(!cells.first().inMonth)
        val cell = cells.single { it.today }
        assertEquals(today to 2, cell.date to cell.events)
    }
}
