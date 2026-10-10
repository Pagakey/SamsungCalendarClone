package com.example.samsungcalendarclone

import android.Manifest
import android.app.Notification
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.samsungcalendarclone.data.reminders.ReminderAlerts
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * Reads provider alert rows for an isolated LOCAL calendar and builds (never posts) the
 * notification. The alert rows are inserted directly, as a calendar app may do, so no real
 * alarm fires and no other app is alerted.
 */
@RunWith(AndroidJUnit4::class)
class ReminderAlertsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val account = "reminder-test-${UUID.randomUUID()}"
    private var calendarUri: Uri? = null
    private var calendarId = -1L
    private val begin = Instant.parse("2030-04-10T09:00:00Z").toEpochMilli()
    private val alarm = begin - 10 * 60_000

    private fun syncUri(uri: Uri) = uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, account).appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()

    @Before fun setUp() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        calendarUri = resolver.insert(syncUri(Calendars.CONTENT_URI), ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, account); put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, account); put(Calendars.CALENDAR_DISPLAY_NAME, "Temporary reminder test")
            put(Calendars.OWNER_ACCOUNT, account); put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC"); put(Calendars.VISIBLE, 1); put(Calendars.SYNC_EVENTS, 1)
        })
        calendarId = ContentUris.parseId(calendarUri!!)
    }

    @After fun tearDown() {
        try {
            calendarUri?.let { uri ->
                assertEquals(1, resolver.delete(syncUri(uri), null, null))
                resolver.query(uri, arrayOf(Calendars._ID), null, null, null)!!.use { assertFalse(it.moveToFirst()) }
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    private fun event(title: String): Long = ContentUris.parseId(resolver.insert(Events.CONTENT_URI, ContentValues().apply {
        put(Events.CALENDAR_ID, calendarId); put(Events.TITLE, title); put(Events.DTSTART, begin); put(Events.DTEND, begin + 3_600_000)
        put(Events.EVENT_TIMEZONE, "UTC"); put(Events.EVENT_LOCATION, "Fictional room")
    })!!)

    private fun alertRow(eventId: Long, minutes: Int) = resolver.insert(CalendarAlerts.CONTENT_URI, ContentValues().apply {
        put(CalendarAlerts.EVENT_ID, eventId); put(CalendarAlerts.BEGIN, begin); put(CalendarAlerts.END, begin + 3_600_000)
        put(CalendarAlerts.ALARM_TIME, alarm); put(CalendarAlerts.CREATION_TIME, System.currentTimeMillis())
        put(CalendarAlerts.RECEIVED_TIME, 0); put(CalendarAlerts.NOTIFY_TIME, 0)
        put(CalendarAlerts.STATE, CalendarAlerts.STATE_SCHEDULED); put(CalendarAlerts.MINUTES, minutes)
    })

    @Test fun alertsAtAlarmTimeBecomeOneNotificationPerOccurrence() {
        val id = event("Fictional reminder")
        alertRow(id, 10); alertRow(id, 10)
        val alerts = ReminderAlerts.alertsAt(context, alarm).filter { it.calendarId == calendarId }
        assertEquals(1, alerts.size)
        assertEquals("Fictional reminder", alerts.single().title)
        assertEquals("Fictional room", alerts.single().location)
        assertTrue(ReminderAlerts.alertsAt(context, alarm + 1).none { it.calendarId == calendarId })
        val notification = ReminderAlerts.notification(context, alerts.single())
        assertEquals("Fictional reminder", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().endsWith("· Fictional room"))
        assertEquals(ReminderAlerts.CHANNEL_ID, notification.channelId)
        assertNotNull(notification.contentIntent)
    }

    @Test fun showingDoesNothingWhileTurnedOff() {
        ReminderAlerts.setEnabled(context, false)
        alertRow(event("Fictional quiet reminder"), 5)
        // Off by default: no notification is attempted, so no permission is needed to call it.
        ReminderAlerts.show(context, alarm)
        assertFalse(ReminderAlerts.enabled(context))
    }
}
