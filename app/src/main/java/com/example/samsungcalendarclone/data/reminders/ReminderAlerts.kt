package com.example.samsungcalendarclone.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.samsungcalendarclone.MainActivity
import com.example.samsungcalendarclone.R
import com.example.samsungcalendarclone.domain.model.ReminderAlert
import com.example.samsungcalendarclone.domain.model.reminderAlertText
import com.example.samsungcalendarclone.domain.model.reminderNotificationId
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Reminder alerts posted by this app. The calendar provider owns the schedule: it sets the
 * alarms from reminder rows (including after reboots and clock changes) and broadcasts
 * [CalendarContract.ACTION_EVENT_REMINDER] when one is due. This app only shows those alerts,
 * and only when the user turned them on, since the device calendar app shows them too.
 */
object ReminderAlerts {
    private const val PREFERENCES = "calendar_preferences"
    private const val KEY_ENABLED = "app_reminders"
    const val CHANNEL_ID = "reminders"
    const val ACTION_OPEN_EVENT = "com.example.samsungcalendarclone.OPEN_EVENT"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_BEGIN = "begin"

    fun enabled(context: Context) = context.getSharedPreferences(PREFERENCES, 0).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, 0).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) ensureChannel(context)
    }

    private fun hiddenCalendars(context: Context) = context.getSharedPreferences(PREFERENCES, 0)
        .getStringSet("hidden", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    /** Alerts the provider scheduled for [alarmTime], whatever state another calendar app has moved them to. */
    fun alertsAt(context: Context, alarmTime: Long): List<ReminderAlert> {
        val alerts = linkedMapOf<Pair<Long, Long>, ReminderAlert>()
        context.contentResolver.query(CalendarAlerts.CONTENT_URI, arrayOf(CalendarAlerts.EVENT_ID, CalendarAlerts.CALENDAR_ID,
            CalendarAlerts.TITLE, CalendarAlerts.BEGIN, CalendarAlerts.END, CalendarAlerts.ALL_DAY, CalendarAlerts.EVENT_LOCATION,
            CalendarAlerts.DISPLAY_COLOR), "${CalendarAlerts.ALARM_TIME} = ? AND ${CalendarAlerts.VISIBLE} = 1",
            arrayOf(alarmTime.toString()), "${CalendarAlerts.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val alert = ReminderAlert(cursor.getLong(0), cursor.getLong(1), cursor.getString(2)?.takeIf { it.isNotBlank() } ?: "Untitled event",
                    cursor.getLong(3), cursor.getLong(4), cursor.getInt(5) != 0, cursor.getString(6).orEmpty(), cursor.getInt(7))
                // Two reminders of one occurrence at the same moment make a single alert.
                alerts.putIfAbsent(alert.eventId to alert.begin, alert)
            }
        }
        val hidden = hiddenCalendars(context)
        return alerts.values.filter { it.calendarId !in hidden }
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Event reminders", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Reminders for events in your calendars" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notification(context: Context, alert: ReminderAlert, zone: ZoneId = ZoneId.systemDefault()): android.app.Notification {
        val open = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_EVENT)
            .putExtra(EXTRA_EVENT_ID, alert.eventId).putExtra(EXTRA_BEGIN, alert.begin)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, reminderNotificationId(alert), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_event)
            .setContentTitle(alert.title)
            .setContentText(reminderAlertText(alert, zone))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(alert.color)
            .setWhen(alert.begin)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun canRead(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission") // canPost checks POST_NOTIFICATIONS.
    fun show(context: Context, alarmTime: Long) {
        if (!enabled(context) || !canRead(context) || !canPost(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        alertsAt(context, alarmTime).forEach { alert -> manager.notify(reminderNotificationId(alert), notification(context, alert)) }
    }
}

/**
 * Device conditions that can stop reminder broadcasts reaching this app. Read-only: the app
 * only explains them and links to its standard App info page, never changes them itself.
 */
object DeviceSetup {
    /** Xiaomi, Redmi and POCO phones gate background broadcasts behind Autostart and battery settings. */
    val xiaomiFamily: Boolean get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}

/** Receives the provider's reminder broadcast; the alarm time is the last segment of its data URI. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarContract.ACTION_EVENT_REMINDER) return
        val alarmTime = intent.data?.lastPathSegment?.toLongOrNull()
            ?: intent.getLongExtra(CalendarAlerts.ALARM_TIME, -1L).takeIf { it > 0 } ?: return
        val pending = goAsync()
        val app = context.applicationContext
        worker.execute {
            try { ReminderAlerts.show(app, alarmTime) }
            catch (_: SecurityException) { } // Calendar or notification access was revoked in between.
            finally { pending.finish() }
        }
    }

    private companion object { val worker = Executors.newSingleThreadExecutor() }
}
