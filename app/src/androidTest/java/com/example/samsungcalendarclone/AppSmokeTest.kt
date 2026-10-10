package com.example.samsungcalendarclone

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The installed package wires up what the features rely on, independent of any calendar data. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packages = context.packageManager

    @Test fun launchesAndDeclaresOnlyTheNeededPermissions() {
        assertEquals("com.example.samsungcalendarclone", context.packageName)
        assertNotNull(packages.getLaunchIntentForPackage(context.packageName))
        val requested = packages.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue(requested.containsAll(listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.POST_NOTIFICATIONS)))
        assertFalse("Reminders are scheduled by the provider", Manifest.permission.SCHEDULE_EXACT_ALARM in requested)
        assertFalse(Manifest.permission.INTERNET in requested)
    }

    @Test fun reminderBroadcastAndWidgetsAreRegistered() {
        val reminder = Intent(CalendarContract.ACTION_EVENT_REMINDER, CalendarContract.CONTENT_URI.buildUpon().appendPath("1").build())
            .setPackage(context.packageName)
        assertEquals(1, packages.queryBroadcastReceivers(reminder, 0).size)
        val providers = AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == context.packageName }
        assertEquals(setOf("AgendaWidgetReceiver", "MonthWidgetReceiver"), providers.map { it.provider.shortClassName.substringAfterLast('.') }.toSet())
    }
}
