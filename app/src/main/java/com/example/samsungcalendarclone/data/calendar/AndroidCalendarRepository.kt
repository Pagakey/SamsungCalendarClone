package com.example.samsungcalendarclone.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentProviderOperation
import android.provider.CalendarContract.Events as Events
import android.provider.CalendarContract.Reminders as Reminders
import com.example.samsungcalendarclone.domain.model.EventDetails
import com.example.samsungcalendarclone.domain.model.EventReminder
import com.example.samsungcalendarclone.domain.model.RecurrenceScope
import com.example.samsungcalendarclone.domain.model.exdatesBefore
import com.example.samsungcalendarclone.domain.model.exdatesWith
import com.example.samsungcalendarclone.domain.model.reminderOrder
import com.example.samsungcalendarclone.domain.model.ruleCount
import com.example.samsungcalendarclone.domain.model.ruleEndingAt
import com.example.samsungcalendarclone.domain.model.ruleForFollowing
import com.example.samsungcalendarclone.domain.model.seriesDuration
import com.example.samsungcalendarclone.domain.model.shiftedSeriesStart
import com.example.samsungcalendarclone.domain.model.untilBefore
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
            Events.RRULE, Events.RDATE, Events.ORIGINAL_ID, Events.DURATION, Events.EXDATE, Events._SYNC_ID),
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
                cursor.getString(8), cursor.getString(9), if (cursor.isNull(10)) null else cursor.getLong(10), zone, reminders,
                cursor.getString(11), cursor.getString(12), cursor.getString(13))
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

    /** Changes [scope] occurrences of a repeating event, starting from the opened [occurrence]. */
    suspend fun updateOccurrences(original: EventDetails, occurrence: CalendarEvent, event: NewEvent, scope: RecurrenceScope) = withContext(Dispatchers.IO) {
        val calendar = validateRecurring(original, scope)
        require(event.calendarId == original.event.calendarId) { "Moving an existing event to another calendar is not supported yet." }
        require(event.allDay || runCatching { ZoneId.of(event.zone) }.isSuccess) { "Choose a valid event time zone." }
        require(event.title.isNotBlank()) { "Give your event a title." }
        require(event.end > event.begin) { "End must be after start." }
        validateReminders(event.reminders, calendar, original.reminders)
        val effective = if (scope == RecurrenceScope.FOLLOWING && occurrence.begin == original.event.begin) RecurrenceScope.ALL else scope
        val operations = when {
            original.isException -> exceptionRowUpdate(original, event)
            effective == RecurrenceScope.THIS && !original.synced -> detachOccurrence(original, occurrence, event)
            effective == RecurrenceScope.THIS -> newException(original, occurrence, event.values().apply {
                // Exceptions inherit their calendar and take their end from a duration, like their series.
                remove(Events.CALENDAR_ID)
                remove(Events.DTEND)
                put(Events.DURATION, seriesDuration(event.begin, event.end, event.allDay))
            }, event.reminders)
            effective == RecurrenceScope.ALL -> seriesUpdate(original, occurrence, event)
            else -> splitSeries(original, occurrence, event)
        }
        resolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(operations))
    }

    /** Deletes [scope] occurrences of a repeating event, starting from the opened [occurrence]. */
    suspend fun deleteOccurrences(original: EventDetails, occurrence: CalendarEvent, scope: RecurrenceScope) = withContext(Dispatchers.IO) {
        validateRecurring(original, scope)
        val effective = if (scope == RecurrenceScope.FOLLOWING && occurrence.begin == original.event.begin) RecurrenceScope.ALL else scope
        val series = seriesSelection(original)
        val operations = when {
            // An exception row is cancelled rather than removed, so the series instance it replaced stays hidden.
            original.isException -> listOf(ContentProviderOperation.newUpdate(Events.CONTENT_URI)
                .withSelection(exceptionSelection(original).first, exceptionSelection(original).second)
                .withValue(Events.STATUS, Events.STATUS_CANCELED).withExpectedCount(1).build())
            effective == RecurrenceScope.THIS && !original.synced -> detachOccurrence(original, occurrence, null)
            effective == RecurrenceScope.THIS -> newException(original, occurrence,
                ContentValues().apply { put(Events.STATUS, Events.STATUS_CANCELED) }, null)
            effective == RecurrenceScope.ALL -> listOf(ContentProviderOperation.newDelete(Events.CONTENT_URI)
                .withSelection(series.first, series.second).withExpectedCount(1).build())
            else -> listOf(
                ContentProviderOperation.newUpdate(Events.CONTENT_URI).withSelection(series.first, series.second)
                    .withValues(seriesTiming(original, ruleEndingAt(original.recurrence!!, untilBefore(occurrence.begin, original.event.allDay))))
                    .withExpectedCount(1).build(),
                // Changed occurrences after the split would otherwise linger as standalone events.
                ContentProviderOperation.newDelete(Events.CONTENT_URI)
                    .withSelection("${Events.ORIGINAL_ID} = ? AND ${Events.ORIGINAL_INSTANCE_TIME} >= ?",
                        arrayOf(original.id.toString(), occurrence.begin.toString())).build(),
            )
        }
        resolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(operations))
    }

    private suspend fun validateRecurring(original: EventDetails, scope: RecurrenceScope): DeviceCalendar {
        require(scope in original.recurrenceScopes) { "Use the device calendar app to change this repeating event." }
        return requireNotNull(calendars(visibleOnly = false).find { it.id == original.event.calendarId && it.writable }) {
            "This calendar is read-only or no longer available."
        }
    }

    private fun exceptionRowUpdate(original: EventDetails, event: NewEvent): List<ContentProviderOperation> {
        val (selection, args) = exceptionSelection(original)
        val operations = mutableListOf(ContentProviderOperation.newUpdate(Events.CONTENT_URI)
            .withSelection(selection, args).withValues(event.values()).withExpectedCount(1).build())
        if (!sameReminders(event.reminders, original.reminders)) operations += replaceReminders(original, event.reminders)
        return operations
    }

    /** Inserts an exception for one occurrence; [reminders] null leaves a cancellation without reminder rows. */
    private fun newException(original: EventDetails, occurrence: CalendarEvent, values: ContentValues,
                             reminders: List<EventReminder>?): List<ContentProviderOperation> {
        val series = seriesSelection(original)
        val operations = mutableListOf(
            ContentProviderOperation.newAssertQuery(Events.CONTENT_URI).withSelection(series.first, series.second).withExpectedCount(1).build(),
            ContentProviderOperation.newInsert(ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, original.id))
                .withValues(values).withValue(Events.ORIGINAL_INSTANCE_TIME, occurrence.begin).build(),
        )
        if (reminders != null) {
            // Providers differ on copying series reminders to an exception, so set them explicitly.
            operations += ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                .withSelection("${Reminders.EVENT_ID} = ?", arrayOf("")).withSelectionBackReference(0, 1).build()
            reminders.forEach { reminder ->
                operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                    .withValueBackReference(Reminders.EVENT_ID, 1).withValues(reminder.values()).build()
            }
        }
        return operations
    }

    /**
     * Device-only series have no sync ID, and the platform provider then re-expands an exception
     * without its series. Excluding the date with EXDATE and saving any edit as a standalone
     * event keeps the month view correct for those calendars.
     */
    private fun detachOccurrence(original: EventDetails, occurrence: CalendarEvent, event: NewEvent?): List<ContentProviderOperation> {
        val (selection, args) = seriesSelection(original)
        val operations = mutableListOf(ContentProviderOperation.newUpdate(Events.CONTENT_URI).withSelection(selection, args)
            .withValues(seriesTiming(original, original.recurrence!!, exdatesWith(original.exceptionDates, occurrence.begin)))
            .withExpectedCount(1).build())
        if (event != null) {
            operations += ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(seriesExtras(original.id).apply {
                remove(Events.EXDATE)
            }).withValues(event.values()).build()
            event.reminders.forEach { reminder ->
                operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                    .withValueBackReference(Reminders.EVENT_ID, 1).withValues(reminder.values()).build()
            }
        }
        return operations
    }

    private fun seriesUpdate(original: EventDetails, occurrence: CalendarEvent, event: NewEvent): List<ContentProviderOperation> {
        require(event.allDay == original.event.allDay) { "Switch all-day on or off for one event at a time." }
        val timesChanged = event.begin != occurrence.begin || event.end != occurrence.end
        require(!timesChanged || exceptionCount(original.id) == 0) {
            "Some events in this series were changed individually. Change the series times in your calendar app."
        }
        val begin = if (event.allDay) original.event.begin + (event.begin - occurrence.begin)
            else shiftedSeriesStart(original.event.begin, occurrence.begin, event.begin, ZoneId.of(event.zone))
        val values = event.values().apply {
            put(Events.DTSTART, begin)
            putNull(Events.DTEND)
            put(Events.DURATION, if (timesChanged) seriesDuration(event.begin, event.end, event.allDay) else original.duration)
            put(Events.RRULE, original.recurrence)
            put(Events.EXDATE, original.exceptionDates)
        }
        val (selection, args) = seriesSelection(original)
        val operations = mutableListOf(ContentProviderOperation.newUpdate(Events.CONTENT_URI)
            .withSelection(selection, args).withValues(values).withExpectedCount(1).build())
        if (!sameReminders(event.reminders, original.reminders)) operations += replaceReminders(original, event.reminders)
        return operations
    }

    /** Ends the series before [occurrence] and starts a new series there with the edited values. */
    private fun splitSeries(original: EventDetails, occurrence: CalendarEvent, event: NewEvent): List<ContentProviderOperation> {
        require(event.allDay == original.event.allDay) { "Switch all-day on or off for one event at a time." }
        val rule = original.recurrence!!
        require(exceptionCount(original.id, from = occurrence.begin) == 0) {
            "Some later events in this series were changed individually. Change them in your calendar app."
        }
        val remaining = ruleCount(rule)?.let { count ->
            val skipped = requireNotNull(exdatesBefore(original.exceptionDates, occurrence.begin)) {
                "This series skips some dates in a format this app can’t count. Change it in your calendar app."
            }
            count - instancesBefore(original, occurrence.begin) - exceptionCount(original.id, until = occurrence.begin) - skipped
        }
        require(remaining == null || remaining > 0) { "This event is no longer part of its series. Reopen it and try again." }
        val (selection, args) = seriesSelection(original)
        val copied = seriesExtras(original.id)
        val operations = mutableListOf(
            ContentProviderOperation.newUpdate(Events.CONTENT_URI).withSelection(selection, args)
                .withValues(seriesTiming(original, ruleEndingAt(rule, untilBefore(occurrence.begin, original.event.allDay))))
                .withExpectedCount(1).build(),
            ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(copied).withValues(event.values().apply {
                remove(Events.DTEND)
                put(Events.DURATION, seriesDuration(event.begin, event.end, event.allDay))
                put(Events.RRULE, ruleForFollowing(rule, remaining))
            }).build(),
        )
        event.reminders.forEach { reminder ->
            operations += ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                .withValueBackReference(Reminders.EVENT_ID, 1).withValues(reminder.values()).build()
        }
        return operations
    }

    /**
     * The complete timing of a series row with [rule]. Providers re-expand instances from the
     * values in an update, so partial updates (RRULE alone) leave stale or missing occurrences.
     */
    private fun seriesTiming(original: EventDetails, rule: String, exdates: String? = original.exceptionDates) = ContentValues().apply {
        put(Events.EXDATE, exdates)
        put(Events.DTSTART, original.event.begin)
        putNull(Events.DTEND)
        put(Events.DURATION, original.duration)
        put(Events.RRULE, rule)
        put(Events.ALL_DAY, if (original.event.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, original.sourceZone)
    }

    /** Series fields outside the editor that the new half of a split should keep. */
    private fun seriesExtras(id: Long): ContentValues {
        val columns = arrayOf(Events.AVAILABILITY, Events.ACCESS_LEVEL, Events.EVENT_COLOR_KEY, Events.EXDATE)
        val values = ContentValues()
        resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, id), columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) columns.forEachIndexed { index, column ->
                if (!cursor.isNull(index)) values.put(column, cursor.getString(index))
            }
        }
        return values
    }

    private fun exceptionCount(seriesId: Long, from: Long? = null, until: Long? = null): Int {
        val clauses = mutableListOf("${Events.ORIGINAL_ID} = ?", "${Events.DELETED} = 0")
        val args = mutableListOf(seriesId.toString())
        from?.let { clauses += "${Events.ORIGINAL_INSTANCE_TIME} >= ?"; args += it.toString() }
        until?.let { clauses += "${Events.ORIGINAL_INSTANCE_TIME} < ?"; args += it.toString() }
        return resolver.query(Events.CONTENT_URI, arrayOf(Events._ID), clauses.joinToString(" AND "), args.toTypedArray(), null)
            ?.use { it.count } ?: 0
    }

    /** Series occurrences still expanded before [time]; exceptions are counted separately. */
    private fun instancesBefore(original: EventDetails, time: Long): Int {
        if (time <= original.event.begin) return 0
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, original.event.begin)
            ContentUris.appendId(it, time - 1)
        }.build()
        return resolver.query(uri, arrayOf(CalendarContract.Instances.BEGIN),
            "${CalendarContract.Instances.EVENT_ID} = ? AND ${CalendarContract.Instances.BEGIN} < ?",
            arrayOf(original.id.toString(), time.toString()), null)?.use { it.count } ?: 0
    }

    private fun seriesSelection(original: EventDetails): Pair<String, Array<String>> {
        val event = original.event
        val clauses = mutableListOf("${Events._ID} = ?", "${Events.DELETED} = 0", "${Events.RRULE} = ?",
            "COALESCE(${Events.RDATE}, '') = ''", "${Events.ORIGINAL_ID} IS NULL", "${Events.CALENDAR_ID} = ?",
            "${Events.DTSTART} = ?", "COALESCE(${Events.DURATION}, '') = ?", "${Events.ALL_DAY} = ?", "COALESCE(${Events.EXDATE}, '') = ?")
        val args = mutableListOf(original.id.toString(), original.recurrence.orEmpty(), event.calendarId.toString(),
            event.begin.toString(), original.duration.orEmpty(), if (event.allDay) "1" else "0", original.exceptionDates.orEmpty())
        textClauses(original, clauses, args)
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    private fun exceptionSelection(original: EventDetails): Pair<String, Array<String>> {
        val (selection, args) = unchangedSelection(original)
        return selection.replace("${Events.ORIGINAL_ID} IS NULL", "${Events.ORIGINAL_ID} = ?") to
            (listOf(args.first(), original.originalId.toString()) + args.drop(1)).toTypedArray()
    }

    private fun textClauses(original: EventDetails, clauses: MutableList<String>, args: MutableList<String>) {
        val event = original.event
        val texts = linkedMapOf(Events.TITLE to event.title, Events.EVENT_LOCATION to event.location,
            Events.DESCRIPTION to event.notes, Events.EVENT_TIMEZONE to original.sourceZone.orEmpty())
        texts.forEach { (key, value) -> clauses += "COALESCE($key, '') = ?"; args += value }
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
        textClauses(original, clauses, args)
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
