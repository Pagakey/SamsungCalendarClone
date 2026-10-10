package com.example.samsungcalendarclone.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentProviderOperation
import android.provider.CalendarContract.Events as Events
import android.provider.CalendarContract.Reminders as Reminders
import com.example.samsungcalendarclone.domain.model.EventDetails
import com.example.samsungcalendarclone.domain.model.EventReminder
import com.example.samsungcalendarclone.domain.model.reminderOrder
import com.example.samsungcalendarclone.domain.model.sameReminders
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.example.samsungcalendarclone.domain.model.CalendarEvent
import com.example.samsungcalendarclone.domain.model.DeviceCalendar
import com.example.samsungcalendarclone.domain.model.NewEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class AndroidCalendarRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun changes() = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    suspend fun calendars(visibleOnly: Boolean = true): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DeviceCalendar>()
        resolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.MAX_REMINDERS, CalendarContract.Calendars.ALLOWED_REMINDERS), if (visibleOnly) "${CalendarContract.Calendars.VISIBLE} = ?" else null, if (visibleOnly) arrayOf("1") else null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE")?.use { cursor ->
            while (cursor.moveToNext()) result += DeviceCalendar(
                cursor.getLong(0), cursor.getString(1) ?: "Calendar", cursor.getString(2) ?: "On device",
                cursor.getInt(3), cursor.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                if (cursor.isNull(5)) 0 else cursor.getInt(5),
                cursor.getString(6).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
            )
        }
        result
    }

    suspend fun events(start: LocalDate, endExclusive: LocalDate, zone: ZoneId): List<CalendarEvent> = withContext(Dispatchers.IO) {
        // Cover both local timed events and UTC-encoded all-day events at range boundaries.
        val begin = minOf(start.atStartOfDay(zone).toInstant().toEpochMilli(), start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val end = maxOf(endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(), endExclusive.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val result = mutableListOf<CalendarEvent>()
        resolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.EVENT_LOCATION), "${CalendarContract.Instances.VISIBLE} = ? AND (${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ?)",
            arrayOf("1", CalendarContract.Events.STATUS_CANCELED.toString()), "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) result += CalendarEvent(
                cursor.getLong(0), cursor.getLong(1), cursor.getString(2)?.takeIf { it.isNotBlank() } ?: "Untitled event",
                cursor.getLong(3), cursor.getLong(4), cursor.getInt(5) != 0,
                cursor.getInt(6), cursor.getString(7) ?: "",
            )
        }
        result
    }

    suspend fun create(event: NewEvent) = withContext(Dispatchers.IO) {
        require(event.allDay || runCatching { ZoneId.of(event.zone) }.isSuccess) { "Choose a valid event time zone." }
        require(event.title.isNotBlank()) { "Give your event a title." }
        require(event.end > event.begin) { "End must be after start." }
        val calendar = calendars(visibleOnly = false).find { it.id == event.calendarId && it.writable }
        requireNotNull(calendar) { "This calendar is no longer writable." }
        validateReminders(event.reminders, calendar)
        // One batch so the event and its reminders are saved together or not at all.
        val operations = arrayListOf(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(event.values()).build())
        event.reminders.forEach { reminder ->
            operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                .withValueBackReference(Reminders.EVENT_ID, 0).withValues(reminder.values()).build()
        }
        checkNotNull(resolver.applyBatch(CalendarContract.AUTHORITY, operations).firstOrNull()?.uri) { "The calendar could not save this event." }
    }

    suspend fun details(id: Long, occurrence: CalendarEvent? = null): EventDetails? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, id)
        resolver.query(uri, arrayOf(Events.CALENDAR_ID, Events.TITLE, Events.DTSTART, Events.DTEND,
            Events.ALL_DAY, Events.EVENT_TIMEZONE, Events.EVENT_LOCATION, Events.DESCRIPTION,
            Events.RRULE, Events.RDATE, Events.ORIGINAL_ID),
            "${Events.DELETED} = 0", null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            val zone = cursor.getString(5)
            val event = NewEvent(cursor.getString(1).orEmpty(), cursor.getLong(0), cursor.getLong(2),
                if (cursor.isNull(3)) occurrence?.end ?: cursor.getLong(2) else cursor.getLong(3),
                cursor.getInt(4) != 0, zone ?: ZoneId.systemDefault().id,
                cursor.getString(6).orEmpty(), cursor.getString(7).orEmpty())
            val reminders = mutableListOf<EventReminder>()
            resolver.query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES, Reminders.METHOD),
                "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()), null)?.use { rows ->
                while (rows.moveToNext()) reminders += EventReminder(rows.getInt(0), rows.getInt(1))
            }
            reminders.sortWith(reminderOrder)
            EventDetails(id, event.copy(reminders = reminders), calendars(visibleOnly = false).find { it.id == event.calendarId },
                cursor.getString(8), cursor.getString(9), if (cursor.isNull(10)) null else cursor.getLong(10), zone, reminders)
        }
    }

    suspend fun update(original: EventDetails, event: NewEvent) = withContext(Dispatchers.IO) {
        val calendar = validateEditable(original)
        require(event.calendarId == original.event.calendarId) { "Moving an existing event to another calendar is not supported yet." }
        require(event.allDay || runCatching { ZoneId.of(event.zone) }.isSuccess) { "Choose a valid event time zone." }
        require(event.title.isNotBlank()) { "Give your event a title." }
        require(event.end > event.begin) { "End must be after start." }
        val (selection, args) = unchangedSelection(original)
        // Match the snapshot atomically so external changes cannot be silently overwritten.
        val operations = arrayListOf(ContentProviderOperation.newUpdate(Events.CONTENT_URI)
            .withSelection(selection, args).withValues(event.values()).withExpectedCount(1).build())
        if (!sameReminders(event.reminders, original.reminders)) {
            validateReminders(event.reminders, calendar, original.reminders)
            operations += replaceReminders(original, event.reminders)
        }
        // The provider applies a batch in one transaction, so a stale snapshot rolls back every row.
        resolver.applyBatch(CalendarContract.AUTHORITY, operations)
    }

    /** Asserts the loaded reminder rows are still current, then swaps them for [reminders]. */
    private fun replaceReminders(original: EventDetails, reminders: List<EventReminder>): List<ContentProviderOperation> {
        val eventId = original.id.toString()
        val byEvent = "${Reminders.EVENT_ID} = ?"
        val operations = mutableListOf(ContentProviderOperation.newAssertQuery(Reminders.CONTENT_URI)
            .withSelection(byEvent, arrayOf(eventId)).withExpectedCount(original.reminders.size).build())
        original.reminders.groupingBy { it }.eachCount().forEach { (reminder, count) ->
            operations += ContentProviderOperation.newAssertQuery(Reminders.CONTENT_URI)
                .withSelection("$byEvent AND ${Reminders.MINUTES} = ? AND ${Reminders.METHOD} = ?",
                    arrayOf(eventId, reminder.minutes.toString(), reminder.method.toString()))
                .withExpectedCount(count).build()
        }
        operations += ContentProviderOperation.newDelete(Reminders.CONTENT_URI).withSelection(byEvent, arrayOf(eventId)).build()
        reminders.forEach { reminder ->
            operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                .withValue(Reminders.EVENT_ID, original.id).withValues(reminder.values()).build()
        }
        return operations
    }

    private fun validateReminders(reminders: List<EventReminder>, calendar: DeviceCalendar, existing: List<EventReminder> = emptyList()) {
        require(calendar.maxReminders <= 0 || reminders.size <= calendar.maxReminders) {
            "${calendar.name} allows at most ${calendar.maxReminders} reminders."
        }
        require(reminders.all { it.minutes >= -1 }) { "Reminders must be at or before the start." }
        val allowed = calendar.allowedReminderMethods
        // Methods already on the event stay valid even when the calendar no longer offers them.
        require(allowed.isEmpty() || reminders.all { it.method in allowed || existing.any { old -> old.method == it.method } }) {
            "${calendar.name} doesn’t support this kind of reminder."
        }
    }

    suspend fun delete(original: EventDetails) = withContext(Dispatchers.IO) {
        validateEditable(original)
        val (selection, args) = unchangedSelection(original)
        val operation = ContentProviderOperation.newDelete(Events.CONTENT_URI)
            .withSelection(selection, args).withExpectedCount(1).build()
        resolver.applyBatch(CalendarContract.AUTHORITY, arrayListOf(operation))
    }

    private suspend fun validateEditable(original: EventDetails): DeviceCalendar {
        require(!original.repeating) { "Use the device calendar app to change this repeating event." }
        return requireNotNull(calendars(visibleOnly = false).find { it.id == original.event.calendarId && it.writable }) {
            "This calendar is read-only or no longer available."
        }
    }

    private fun unchangedSelection(original: EventDetails): Pair<String, Array<String>> {
        val event = original.event
        val clauses = mutableListOf("${Events._ID} = ?", "${Events.DELETED} = 0", "COALESCE(${Events.RRULE}, '') = ''",
            "COALESCE(${Events.RDATE}, '') = ''", "${Events.ORIGINAL_ID} IS NULL")
        val args = mutableListOf(original.id.toString())
        val fields = linkedMapOf(Events.CALENDAR_ID to event.calendarId.toString(), Events.DTSTART to event.begin.toString(),
            Events.DTEND to event.end.toString(), Events.ALL_DAY to if (event.allDay) "1" else "0")
        fields.forEach { (key, value) -> clauses += "$key = ?"; args += value }
        val texts = linkedMapOf(Events.TITLE to event.title, Events.EVENT_LOCATION to event.location,
            Events.DESCRIPTION to event.notes, Events.EVENT_TIMEZONE to original.sourceZone.orEmpty())
        texts.forEach { (key, value) -> clauses += "COALESCE($key, '') = ?"; args += value }
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    private fun NewEvent.values() = ContentValues().apply {
        put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, title.trim())
        put(Events.DTSTART, begin)
        put(Events.DTEND, end)
        put(Events.ALL_DAY, if (allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, if (allDay) "UTC" else zone)
        put(Events.EVENT_LOCATION, location)
        put(Events.DESCRIPTION, notes)
    }

    private fun EventReminder.values() = ContentValues().apply {
        put(Reminders.MINUTES, minutes)
        put(Reminders.METHOD, method)
    }

}
