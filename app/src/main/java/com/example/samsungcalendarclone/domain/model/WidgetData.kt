package com.example.samsungcalendarclone.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A day of the agenda widget and the events still worth showing on it. */
data class AgendaDay(val date: LocalDate, val events: List<CalendarEvent>)

/**
 * Days with events from [today] for [days] days. Timed events that already ended today are
 * dropped, and the list stops after [limit] events so a widget stays a quick glance.
 */
fun agendaDays(events: List<CalendarEvent>, today: LocalDate, days: Int, zone: ZoneId, now: Long, limit: Int): List<AgendaDay> {
    val dates = List(days) { today.plusDays(it.toLong()) }
    var remaining = limit
    return eventsByDate(events, dates, zone).mapNotNull { (date, list) ->
        val shown = list.filter { date != today || it.allDay || it.end > now }.take(remaining)
        remaining -= shown.size
        if (shown.isEmpty()) null else AgendaDay(date, shown)
    }
}

/** One cell of the month widget's six-week grid. */
data class MonthCell(val date: LocalDate, val inMonth: Boolean, val today: Boolean, val events: Int)

fun monthCells(month: YearMonth, today: LocalDate, byDate: Map<LocalDate, List<CalendarEvent>>): List<MonthCell> =
    monthDates(month).map { MonthCell(it, YearMonth.from(it) == month, it == today, byDate[it]?.size ?: 0) }
