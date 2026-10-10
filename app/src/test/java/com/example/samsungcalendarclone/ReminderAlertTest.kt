package com.example.samsungcalendarclone

import com.example.samsungcalendarclone.domain.model.ReminderAlert
import com.example.samsungcalendarclone.domain.model.reminderAlertText
import com.example.samsungcalendarclone.domain.model.reminderNotificationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderAlertTest {
    private val athens = ZoneId.of("Europe/Athens")
    private val today = LocalDate.of(2026, 10, 10)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(athens).toInstant().toEpochMilli()
    private fun alert(begin: Long, end: Long, allDay: Boolean = false, location: String = "") =
        ReminderAlert(7, 1, "Fictional review", begin, end, allDay, location, 0)

    @Test fun timedAlertsNameTheDayAndPlace() {
        assertEquals("Today, 09:00 – 10:00 · Studio", reminderAlertText(alert(at(today, 9), at(today, 10), location = "Studio"), athens, today))
        assertEquals("Tomorrow, 17:00 – 19:00", reminderAlertText(alert(at(today.plusDays(1), 17), at(today.plusDays(1), 19)), athens, today))
        assertEquals("Thu 15 Oct, 08:00 – 09:00", reminderAlertText(alert(at(today.plusDays(5), 8), at(today.plusDays(5), 9)), athens, today))
    }
    @Test fun allDayAlertsUseTheUtcDate() {
        val begin = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("Tomorrow, All day", reminderAlertText(alert(begin, begin + 86_400_000, allDay = true), athens, today))
    }
    @Test fun notificationIdsDifferPerOccurrence() {
        val first = alert(at(today, 9), at(today, 10))
        assertEquals(reminderNotificationId(first), reminderNotificationId(first.copy(title = "Renamed")))
        assertNotEquals(reminderNotificationId(first), reminderNotificationId(first.copy(begin = at(today.plusDays(1), 9))))
    }
}
