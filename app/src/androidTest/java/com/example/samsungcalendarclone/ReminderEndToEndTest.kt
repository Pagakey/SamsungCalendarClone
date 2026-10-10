package com.example.samsungcalendarclone

import android.Manifest
import android.app.NotificationManager
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.samsungcalendarclone.data.reminders.ReminderAlerts
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * A real provider reminder, end to end: a fictional event in a temporary LOCAL calendar gets a
 * one-minute reminder, and the test waits for this app's notification. Opt-in only
 * (`-e e2e true`), because the device calendar app will also alert for the event and the app
 * needs real READ_CALENDAR and POST_NOTIFICATIONS grants for the receiver to post.
 */
@RunWith(AndroidJUnit4::class)
class ReminderEndToEndTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val account = "reminder-e2e-${UUID.randomUUID()}"
    private var calendarUri: Uri? = null
    private var wasEnabled = false

    private fun syncUri(uri: Uri) = uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, account).appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()

    @Before fun setUp() {
        assumeTrue("Run with -e e2e true", InstrumentationRegistry.getArguments().getString("e2e") == "true")
        assumeTrue("Grant READ_CALENDAR and POST_NOTIFICATIONS first", ReminderAlerts.canRead(context) && ReminderAlerts.canPost(context))
        wasEnabled = ReminderAlerts.enabled(context)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        calendarUri = resolver.insert(syncUri(Calendars.CONTENT_URI), ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, account); put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, account); put(Calendars.CALENDAR_DISPLAY_NAME, "Temporary reminder check")
            put(Calendars.OWNER_ACCOUNT, account); put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC"); put(Calendars.VISIBLE, 1); put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.ALLOWED_REMINDERS, "1"); put(Calendars.MAX_REMINDERS, 5)
        })
    }

    @After fun tearDown() {
        if (calendarUri == null) return
        try {
            context.getSystemService(NotificationManager::class.java).cancelAll()
            ReminderAlerts.setEnabled(context, wasEnabled)
            assertEquals(1, resolver.delete(syncUri(calendarUri!!), null, null))
            resolver.query(calendarUri!!, arrayOf(Calendars._ID), null, null, null)!!.use { assertFalse(it.moveToFirst()) }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    @Test fun providerReminderBecomesThisAppsNotification() {
        ReminderAlerts.setEnabled(context, true)
        // Start on a whole minute about three minutes out; the reminder is due a minute before.
        val begin = (System.currentTimeMillis() / 60_000 + 3) * 60_000
        val id = ContentUris.parseId(resolver.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, ContentUris.parseId(calendarUri!!)); put(Events.TITLE, "Fictional reminder check")
            put(Events.DTSTART, begin); put(Events.DTEND, begin + 15 * 60_000); put(Events.EVENT_TIMEZONE, "UTC")
        })!!)
        resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, id); put(Reminders.MINUTES, 1); put(Reminders.METHOD, Reminders.METHOD_ALERT)
        })
        val notifications = context.getSystemService(NotificationManager::class.java)
        val deadline = begin + 4 * 60_000
        var posted = false
        while (!posted && System.currentTimeMillis() < deadline) {
            Thread.sleep(5_000)
            posted = notifications.activeNotifications.any { it.notification.extras.getString("android.title") == "Fictional reminder check" }
        }
        val lateBy = (System.currentTimeMillis() - (begin - 60_000)) / 1000
        assertTrue("No notification within 3 minutes of the alarm time", posted)
        android.util.Log.i("ReminderE2E", "Reminder notification observed ${lateBy}s after its alarm time")
    }

}

/**
 * Sets the app's reminder switch for a manual background check (`-e set_reminders on|off`),
 * where the event is created over adb and the app process is not running when the alarm fires.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSwitchForManualCheck {
    @Test fun setFromArgument() {
        val value = InstrumentationRegistry.getArguments().getString("set_reminders")
        assumeTrue("Run with -e set_reminders on|off", value == "on" || value == "off")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ReminderAlerts.setEnabled(context, value == "on")
        // Switching off after a check also clears the alert it left behind.
        if (value == "off") context.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
