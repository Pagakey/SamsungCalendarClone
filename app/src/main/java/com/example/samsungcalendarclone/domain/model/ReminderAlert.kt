package com.example.samsungcalendarclone.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One reminder the calendar provider fired for an event occurrence. */
data class ReminderAlert(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val color: Int,
)

/** Notification body: "Today, 09:00 – 10:00 · Studio", "Tomorrow · All day", "Fri 13 Feb, 17:00 – 19:00". */
fun reminderAlertText(alert: ReminderAlert, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): String {
    val dateZone = if (alert.allDay) ZoneOffset.UTC else zone
    val start = Instant.ofEpochMilli(alert.begin).atZone(dateZone)
    val day = when (start.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> start.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    val time = if (alert.allDay) "All day" else {
        val end = Instant.ofEpochMilli(alert.end).atZone(zone)
        "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} – ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
    return listOf("$day, $time", alert.location.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
}

/** Stable per occurrence, so a repeated broadcast replaces rather than stacks a notification. */
fun reminderNotificationId(alert: ReminderAlert): Int = (alert.eventId * 31 + alert.begin).hashCode()
