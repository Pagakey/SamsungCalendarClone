package com.example.samsungcalendarclone.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Calendar layouts sharing one selected date and one set of calendar filters. */
enum class CalendarView(val label: String) { MONTH("Month"), WEEK("Week"), DAY("Day"), AGENDA("Agenda") }

/** The Monday-first week containing [date]. */
fun weekDates(date: LocalDate): List<LocalDate> {
    val start = date.minusDays((date.dayOfWeek.value - 1).toLong())
    return List(7) { start.plusDays(it.toLong()) }
}

/** "5 – 11 Oct", "28 Sep – 4 Oct" or, across a year end, "28 Dec 2026 – 3 Jan 2027". */
fun weekLabel(days: List<LocalDate>, locale: Locale = Locale.getDefault()): String {
    val first = days.first()
    val last = days.last()
    fun format(pattern: String, date: LocalDate) = date.format(DateTimeFormatter.ofPattern(pattern, locale))
    return when {
        first.year != last.year -> "${format("d MMM yyyy", first)} – ${format("d MMM yyyy", last)}"
        first.month != last.month -> "${format("d MMM", first)} – ${format("d MMM", last)}"
        else -> "${first.dayOfMonth} – ${format("d MMM", last)}"
    }
}

const val MINUTES_PER_DAY = 24 * 60

/** A timed event placed on one day of a time grid; overlapping events share the width in [columns]. */
data class TimedBlock(val event: CalendarEvent, val startMinute: Int, val endMinute: Int, val column: Int, val columns: Int)

/**
 * Places the timed events of [date] by local wall-clock minutes. Events crossing midnight are
 * clipped to the day, short events get [minMinutes] of height, and each group of overlapping
 * events is split into side-by-side columns.
 */
fun layoutDay(events: List<CalendarEvent>, date: LocalDate, zone: ZoneId, minMinutes: Int = 20): List<TimedBlock> {
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun minuteOf(millis: Long): Int {
        val local = Instant.ofEpochMilli(millis).atZone(zone)
        return local.hour * 60 + local.minute
    }
    val spans = events.filter { !it.allDay && it.begin < dayEnd && (it.end > dayStart || it.begin >= dayStart) }.map { event ->
        val start = if (event.begin <= dayStart) 0 else minuteOf(event.begin)
        val end = if (event.end >= dayEnd) MINUTES_PER_DAY else minuteOf(event.end)
        val shownStart = minOf(start, MINUTES_PER_DAY - minMinutes)
        Triple(event, shownStart, maxOf(end, shownStart + minMinutes).coerceAtMost(MINUTES_PER_DAY))
    }.sortedWith(compareBy<Triple<CalendarEvent, Int, Int>> { it.second }.thenByDescending { it.third }.thenBy { it.first.title })

    val result = mutableListOf<TimedBlock>()
    var cluster = mutableListOf<Pair<Triple<CalendarEvent, Int, Int>, Int>>()
    var clusterEnd = -1
    val columnEnds = mutableListOf<Int>()
    fun flush() {
        val columns = columnEnds.size
        cluster.forEach { (span, column) -> result += TimedBlock(span.first, span.second, span.third, column, columns) }
        cluster = mutableListOf(); columnEnds.clear()
    }
    spans.forEach { span ->
        if (span.second >= clusterEnd) flush()
        val column = columnEnds.indexOfFirst { it <= span.second }.takeIf { it >= 0 } ?: columnEnds.size.also { columnEnds += 0 }
        columnEnds[column] = span.third
        cluster += span to column
        clusterEnd = maxOf(if (cluster.size == 1) span.third else clusterEnd, span.third)
    }
    flush()
    return result
}
