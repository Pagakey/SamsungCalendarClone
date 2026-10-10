package com.example.samsungcalendarclone.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** Always six Monday-first weeks, including the neighboring months. */
fun monthDates(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    return List(42) { start.plusDays(it.toLong()) }
}

data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val writable: Boolean,
    /** Provider limit on reminders per event; 0 means the calendar reports no limit. */
    val maxReminders: Int = 0,
    /** Reminder methods the calendar accepts (`Reminders.METHOD_*`); empty means unrestricted. */
    val allowedReminderMethods: Set<Int> = emptySet(),
) {
    /** Method used for reminders added in this app: a notification when the calendar allows it. */
    val newReminderMethod: Int get() = when {
        allowedReminderMethods.isEmpty() || REMINDER_ALERT in allowedReminderMethods -> REMINDER_ALERT
        else -> allowedReminderMethods.minOrNull() ?: REMINDER_DEFAULT
    }
}

data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String,
) {
    fun firstDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(begin)
        .atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()

    // Provider end timestamps are exclusive, including midnight and all-day ends.
    fun lastDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(maxOf(begin, end - 1))
        .atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
}

fun eventsByDate(events: List<CalendarEvent>, dates: List<LocalDate>, zone: ZoneId): Map<LocalDate, List<CalendarEvent>> =
    dates.associateWith { date ->
        events.filter { date >= it.firstDate(zone) && date <= it.lastDate(zone) }
            .sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin }.thenBy { it.title })
    }

data class NewEvent(
    val title: String,
    val calendarId: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val zone: String,
    val location: String,
    val notes: String,
    val reminders: List<EventReminder> = emptyList(),
)

/** The stored event plus its calendar capabilities; recurring occurrences stay read-only here. */
data class EventDetails(
    val id: Long,
    val event: NewEvent,
    val calendar: DeviceCalendar?,
    val recurrence: String?,
    val recurrenceDates: String?,
    val originalId: Long?,
    val sourceZone: String?,
    val reminders: List<EventReminder> = emptyList(),
    /** Series rows store DURATION instead of DTEND. */
    val duration: String? = null,
    val exceptionDates: String? = null,
    /** Set once an account has synced the row; the provider links exceptions to a series through it. */
    val syncId: String? = null,
) {
    val synced: Boolean get() = !syncId.isNullOrBlank()

    val repeating: Boolean get() = !recurrence.isNullOrBlank() || !recurrenceDates.isNullOrBlank() || originalId != null
    val hasSupportedZone: Boolean get() = runCatching { ZoneId.of(event.zone) }.isSuccess
    /** Single, non-repeating event that the plain update/delete path may change. */
    val canEdit: Boolean get() = calendar?.writable == true && !repeating && hasSupportedZone

    /** A changed or cancelled occurrence stored as its own row, linked to its series. */
    val isException: Boolean get() = originalId != null

    /** Scopes this app can apply natively; RDATE series stay with the device calendar app. */
    val recurrenceScopes: List<RecurrenceScope> get() = when {
        calendar?.writable != true || !hasSupportedZone || !recurrenceDates.isNullOrBlank() -> emptyList()
        isException -> listOf(RecurrenceScope.THIS)
        !recurrence.isNullOrBlank() -> RecurrenceScope.entries
        else -> emptyList()
    }
}


