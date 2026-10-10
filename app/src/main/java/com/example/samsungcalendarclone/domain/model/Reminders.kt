package com.example.samsungcalendarclone.domain.model

// Mirrors CalendarContract.Reminders.METHOD_* so the domain model stays Android-free.
const val REMINDER_DEFAULT = 0
const val REMINDER_ALERT = 1
const val REMINDER_EMAIL = 2
const val REMINDER_SMS = 3
const val REMINDER_ALARM = 4

/** [minutes] before the start; all-day reminders count back from local midnight. -1 is the calendar default. */
data class EventReminder(val minutes: Int, val method: Int)

/** Offered when adding a reminder to a timed event. */
val timedReminderPresets = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

/** Offered for all-day events: 09:00 on the day before, two days before and a week before. */
val allDayReminderPresets = listOf(900, 2340, 9540)

/** Default reminders for a new event; all-day events start without one. */
fun defaultReminders(allDay: Boolean, method: Int): List<EventReminder> =
    if (allDay) emptyList() else listOf(EventReminder(10, method))

/** Order-insensitive comparison, so reordering alone never rewrites reminder rows. */
fun sameReminders(a: List<EventReminder>, b: List<EventReminder>): Boolean =
    a.sortedWith(reminderOrder) == b.sortedWith(reminderOrder)

val reminderOrder: Comparator<EventReminder> = compareBy<EventReminder> { it.minutes }.thenBy { it.method }

fun reminderLabel(minutes: Int, allDay: Boolean): String {
    fun plural(n: Int, unit: String) = "$n $unit${if (n == 1) "" else "s"}"
    if (minutes < 0) return "Calendar default"
    if (allDay) {
        if (minutes == 0) return "On the day at 00:00"
        val days = (minutes + 1439) / 1440
        val time = days * 1440 - minutes
        return "${plural(days, "day")} before at %02d:%02d".format(time / 60, time % 60)
    }
    return when {
        minutes == 0 -> "At start"
        minutes % 10080 == 0 -> "${plural(minutes / 10080, "week")} before"
        minutes % 1440 == 0 -> "${plural(minutes / 1440, "day")} before"
        minutes % 60 == 0 -> "${plural(minutes / 60, "hour")} before"
        else -> "${plural(minutes, "minute")} before"
    }
}

fun reminderMethodLabel(method: Int): String? = when (method) {
    REMINDER_EMAIL -> "Email"
    REMINDER_SMS -> "SMS"
    REMINDER_ALARM -> "Alarm"
    else -> null
}
