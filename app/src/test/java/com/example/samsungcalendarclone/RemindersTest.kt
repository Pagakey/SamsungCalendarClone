package com.example.samsungcalendarclone

import com.example.samsungcalendarclone.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class RemindersTest {
    @Test fun timedLabelsUseLargestWholeUnit() {
        assertEquals("At start", reminderLabel(0, false))
        assertEquals("1 minute before", reminderLabel(1, false))
        assertEquals("10 minutes before", reminderLabel(10, false))
        assertEquals("2 hours before", reminderLabel(120, false))
        assertEquals("90 minutes before", reminderLabel(90, false))
        assertEquals("1 day before", reminderLabel(1440, false))
        assertEquals("1 week before", reminderLabel(10080, false))
        assertEquals("Calendar default", reminderLabel(-1, false))
    }
    @Test fun allDayLabelsCountBackFromMidnight() {
        assertEquals("On the day at 00:00", reminderLabel(0, true))
        assertEquals("1 day before at 09:00", reminderLabel(900, true))
        assertEquals("2 days before at 09:00", reminderLabel(2340, true))
        assertEquals("7 days before at 09:00", reminderLabel(9540, true))
        assertEquals("1 day before at 00:00", reminderLabel(1440, true))
    }
    @Test fun reminderComparisonIgnoresOrderButNotDuplicates() {
        val a = EventReminder(10, REMINDER_ALERT)
        val b = EventReminder(60, REMINDER_EMAIL)
        assertTrue(sameReminders(listOf(a, b), listOf(b, a)))
        assertFalse(sameReminders(listOf(a, a), listOf(a)))
        assertFalse(sameReminders(listOf(a), listOf(a.copy(method = REMINDER_EMAIL))))
    }
    @Test fun newRemindersPreferNotificationsWhenAllowed() {
        assertEquals(REMINDER_ALERT, DeviceCalendar(1, "A", "a", 0, true).newReminderMethod)
        assertEquals(REMINDER_ALERT, DeviceCalendar(1, "A", "a", 0, true, allowedReminderMethods = setOf(0, 1, 2)).newReminderMethod)
        assertEquals(REMINDER_EMAIL, DeviceCalendar(1, "A", "a", 0, true, allowedReminderMethods = setOf(2)).newReminderMethod)
    }
    @Test fun defaultsOnlyRemindTimedEvents() {
        assertEquals(listOf(EventReminder(10, REMINDER_ALERT)), defaultReminders(false, REMINDER_ALERT))
        assertTrue(defaultReminders(true, REMINDER_ALERT).isEmpty())
    }
}
