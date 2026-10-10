package com.example.samsungcalendarclone.ui.event

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import com.example.samsungcalendarclone.domain.model.*

// Bundle forms of the event models, so an open event and an in-progress edit survive process death.
// An edit keeps the snapshot it was opened with; reloading it would defeat stale-change detection.

private fun Bundle.putNullableLong(key: String, value: Long?) { if (value != null) putLong(key, value) }
private fun Bundle.getNullableLong(key: String): Long? = if (containsKey(key)) getLong(key) else null
private fun List<EventReminder>.toIntArray() = flatMap { listOf(it.minutes, it.method) }.toIntArray()
private fun IntArray?.toReminders() = this?.toList()?.chunked(2)?.map { (minutes, method) -> EventReminder(minutes, method) }.orEmpty()

fun CalendarEvent.toBundle() = Bundle().apply {
    putLong("id", id); putLong("calendarId", calendarId); putString("title", title)
    putLong("begin", begin); putLong("end", end); putBoolean("allDay", allDay)
    putInt("color", color); putString("location", location)
}

fun Bundle.toCalendarEvent() = CalendarEvent(getLong("id"), getLong("calendarId"), getString("title").orEmpty(),
    getLong("begin"), getLong("end"), getBoolean("allDay"), getInt("color"), getString("location").orEmpty())

fun NewEvent.toBundle() = Bundle().apply {
    putString("title", title); putLong("calendarId", calendarId); putLong("begin", begin); putLong("end", end)
    putBoolean("allDay", allDay); putString("zone", zone); putString("location", location); putString("notes", notes)
    putIntArray("reminders", reminders.toIntArray()); putString("uid", uid)
}

fun Bundle.toNewEvent() = NewEvent(getString("title").orEmpty(), getLong("calendarId"), getLong("begin"), getLong("end"),
    getBoolean("allDay"), getString("zone").orEmpty(), getString("location").orEmpty(), getString("notes").orEmpty(),
    getIntArray("reminders").toReminders(), getString("uid"))

fun DeviceCalendar.toBundle() = Bundle().apply {
    putLong("id", id); putString("name", name); putString("account", account); putInt("color", color)
    putBoolean("writable", writable); putInt("maxReminders", maxReminders)
    putIntArray("allowedReminderMethods", allowedReminderMethods.toIntArray())
}

fun Bundle.toDeviceCalendar() = DeviceCalendar(getLong("id"), getString("name").orEmpty(), getString("account").orEmpty(),
    getInt("color"), getBoolean("writable"), getInt("maxReminders"), getIntArray("allowedReminderMethods")?.toSet().orEmpty())

fun EventDetails.toBundle() = Bundle().apply {
    putLong("id", id); putBundle("event", event.toBundle()); putBundle("calendar", calendar?.toBundle())
    putString("recurrence", recurrence); putString("recurrenceDates", recurrenceDates); putNullableLong("originalId", originalId)
    putString("sourceZone", sourceZone); putIntArray("reminders", reminders.toIntArray()); putString("duration", duration)
    putString("exceptionDates", exceptionDates); putString("syncId", syncId)
}


fun Bundle.toEventDetails() = EventDetails(getLong("id"), requireNotNull(getBundle("event")).toNewEvent(),
    getBundle("calendar")?.toDeviceCalendar(), getString("recurrence"), getString("recurrenceDates"), getNullableLong("originalId"),
    getString("sourceZone"), getIntArray("reminders").toReminders(), getString("duration"), getString("exceptionDates"), getString("syncId"))

/** An edit of a stored event: its loaded snapshot, the tapped occurrence and, for a series, the chosen scope. */
data class EditRequest(val details: EventDetails, val occurrence: CalendarEvent, val scope: RecurrenceScope?)

fun EditRequest.toBundle() = Bundle().apply {
    putBundle("details", details.toBundle()); putBundle("occurrence", occurrence.toBundle()); putString("scope", scope?.name)
}

fun Bundle.toEditRequest() = EditRequest(requireNotNull(getBundle("details")).toEventDetails(),
    requireNotNull(getBundle("occurrence")).toCalendarEvent(), getString("scope")?.let { RecurrenceScope.valueOf(it) })

val OccurrenceSaver = Saver<CalendarEvent?, Bundle>(save = { it?.toBundle() }, restore = { it.toCalendarEvent() })
val EditRequestSaver = Saver<EditRequest?, Bundle>(save = { it?.toBundle() }, restore = { it.toEditRequest() })
