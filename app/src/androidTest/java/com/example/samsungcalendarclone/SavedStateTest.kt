package com.example.samsungcalendarclone

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.samsungcalendarclone.domain.model.*
import com.example.samsungcalendarclone.ui.event.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The saved forms must survive real parcelling, as they do when Android restores a killed process. */
@RunWith(AndroidJUnit4::class)
class SavedStateTest {
    private fun Bundle.parcelled(): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(this)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally { parcel.recycle() }
    }

    private val calendar = DeviceCalendar(7, "Fictional work", "fictional@example.test", 0xFF527BCC.toInt(), true, 5, setOf(1, 2))
    private val event = NewEvent("Fictional planning", 7, 1_902_042_000_000, 1_902_045_600_000, false, "Europe/Athens",
        "Room 4", "Agenda", listOf(EventReminder(10, REMINDER_ALERT), EventReminder(1440, REMINDER_EMAIL)), "draft-uid")
    private val occurrence = CalendarEvent(42, 7, "Fictional planning", 1_902_128_400_000, 1_902_132_000_000, false, 0, "Room 4")

    @Test fun seriesEditRequestRoundTrips() {
        val details = EventDetails(42, event, calendar, "FREQ=DAILY;COUNT=5", null, null, "Europe/Athens",
            event.reminders, "P3600S", "20300411T090000Z", "fictional-sync-id")
        val request = EditRequest(details, occurrence, RecurrenceScope.FOLLOWING)
        assertEquals(request, request.toBundle().parcelled().toEditRequest())
    }

    @Test fun exceptionWithoutCalendarOrScopeRoundTrips() {
        val details = EventDetails(43, event.copy(uid = null, reminders = emptyList()), null, null, null, 41L, null)
        val request = EditRequest(details, occurrence, null)
        assertEquals(request, request.toBundle().parcelled().toEditRequest())
        assertEquals(occurrence, occurrence.toBundle().parcelled().toCalendarEvent())
    }
}
