package com.example.samsungcalendarclone

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.OperationApplicationException
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as Calendars
import android.provider.CalendarContract.Events as Events
import android.provider.CalendarContract.Reminders as Reminders
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.samsungcalendarclone.data.calendar.AndroidCalendarRepository
import com.example.samsungcalendarclone.data.calendar.CalendarChangeRejected
import com.example.samsungcalendarclone.domain.model.EventReminder
import com.example.samsungcalendarclone.domain.model.NewEvent
import com.example.samsungcalendarclone.domain.model.RecurrenceScope
import com.example.samsungcalendarclone.domain.model.eventsByDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Every mutation is scoped to a newly created LOCAL calendar; never touches a personal account. */
@RunWith(AndroidJUnit4::class)
class CalendarRepositoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val repository = AndroidCalendarRepository(context)
    private val account = "calendar-test-${UUID.randomUUID()}"
    private var calendarUri: Uri? = null
    private var calendarId = -1L

    private fun syncUri(uri: Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()

    @Before fun createIsolatedCalendar() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, account)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, account)
            put(Calendars.CALENDAR_DISPLAY_NAME, "Temporary calendar test")
            put(Calendars.OWNER_ACCOUNT, account)
            put(Calendars.CALENDAR_COLOR, 0xFF527BCC.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC")
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.ALLOWED_REMINDERS, "1")
            put(Calendars.MAX_REMINDERS, 5)
        }
        calendarUri = requireNotNull(resolver.insert(syncUri(Calendars.CONTENT_URI), values))
        calendarId = ContentUris.parseId(calendarUri!!)
    }

    @After fun removeOnlyOurCalendar() {
        try {
            calendarUri?.let { uri ->
                assertEquals("Temporary calendar cleanup", 1, resolver.delete(syncUri(uri), null, null))
                resolver.query(uri, arrayOf(Calendars._ID), null, null, null)!!.use { assertFalse("Test calendar must be removed", it.moveToFirst()) }
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    private fun draft(allDay: Boolean = false) = NewEvent("Fictional test event", calendarId,
        Instant.parse("2030-04-10T09:00:00Z").toEpochMilli(), Instant.parse("2030-04-10T10:00:00Z").toEpochMilli(),
        allDay, "UTC", "Test room", "Fictional notes")

    @Test fun createReadUpdateDeletePreservesReminders() = runBlocking {
        val id = ContentUris.parseId(repository.create(draft()))
        resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, id); put(Reminders.MINUTES, 10); put(Reminders.METHOD, Reminders.METHOD_ALERT)
        })
        val original = requireNotNull(repository.details(id))
        assertEquals(draft().copy(reminders = original.reminders), original.event)
        assertTrue(original.canEdit)
        assertEquals(10, original.reminders.single().minutes)
        repository.update(original, original.event.copy(title = "Updated fictional event", notes = "Updated notes"))
        val updated = requireNotNull(repository.details(id))
        assertEquals("Updated fictional event", updated.event.title)
        assertEquals(original.reminders, updated.reminders)
        assertEquals(original.event.begin, updated.event.begin)
        val instances = repository.events(LocalDate.of(2030, 4, 10), LocalDate.of(2030, 4, 11), ZoneOffset.UTC).filter { it.calendarId == calendarId }
        assertEquals(listOf(id), instances.map { it.id })
        repository.delete(updated)
        assertNull(repository.details(id))
        assertTrue(repository.events(LocalDate.of(2030, 4, 10), LocalDate.of(2030, 4, 11), ZoneOffset.UTC).none { it.calendarId == calendarId })
    }

    @Test fun allDayRoundTripRetainsExclusiveUtcDates() = runBlocking {
        val event = draft(true).copy(begin = Instant.parse("2030-04-10T00:00:00Z").toEpochMilli(),
            end = Instant.parse("2030-04-12T00:00:00Z").toEpochMilli())
        val id = ContentUris.parseId(repository.create(event))
        assertEquals(event, repository.details(id)!!.event)
        val dates = (9L..12L).map { LocalDate.of(2030, 4, it.toInt()) }
        val events = repository.events(dates.first(), dates.last().plusDays(1), ZoneOffset.UTC).filter { it.calendarId == calendarId }
        val grouped = eventsByDate(events, dates, ZoneOffset.UTC)
        assertTrue(grouped[dates[0]]!!.isEmpty())
        assertEquals(1, grouped[dates[1]]!!.size)
        assertEquals(1, grouped[dates[2]]!!.size)
        assertTrue(grouped[dates[3]]!!.isEmpty())
    }

    @Test fun expandedRecurrenceCannotBeAccidentallyEditedOrDeleted() = runBlocking {
        val uri = resolver.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId); put(Events.TITLE, "Fictional repeating event")
            put(Events.DTSTART, draft().begin); put(Events.DURATION, "PT3600S")
            put(Events.EVENT_TIMEZONE, "UTC"); put(Events.RRULE, "FREQ=DAILY;COUNT=3")
        })!!
        val id = ContentUris.parseId(uri)
        val occurrences = repository.events(LocalDate.of(2030, 4, 10), LocalDate.of(2030, 4, 14), ZoneOffset.UTC).filter { it.id == id }
        assertEquals(3, occurrences.size)
        val details = requireNotNull(repository.details(id, occurrences[1]))
        assertTrue(details.repeating)
        assertFalse(details.canEdit)
        try { repository.update(details, details.event.copy(title = "Must not save")); fail("Recurring edit must be rejected") }
        catch (_: IllegalArgumentException) { }
        try { repository.delete(details); fail("Recurring delete must be rejected") }
        catch (_: IllegalArgumentException) { }
        assertEquals(3, repository.events(LocalDate.of(2030, 4, 10), LocalDate.of(2030, 4, 14), ZoneOffset.UTC).count { it.id == id })
    }

    @Test fun staleEditsAndDeletesAreRejected() = runBlocking {
        val uri = repository.create(draft())
        val original = repository.details(ContentUris.parseId(uri))!!
        resolver.update(uri, ContentValues().apply { put(Events.TITLE, "External update") }, null, null)
        try { repository.update(original, original.event.copy(title = "Stale overwrite")); fail("Stale save must fail") }
        catch (_: OperationApplicationException) { }
        try { repository.delete(original); fail("Stale delete must fail") }
        catch (_: OperationApplicationException) { }
        assertEquals("External update", repository.details(original.id)!!.event.title)
    }

    private fun reminderRows(id: Long): List<Pair<Int, Int>> = resolver.query(Reminders.CONTENT_URI,
        arrayOf(Reminders.MINUTES, Reminders.METHOD), "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()), "${Reminders.MINUTES} ASC")!!
        .use { c -> buildList { while (c.moveToNext()) add(c.getInt(0) to c.getInt(1)) } }

    @Test fun remindersAreCreatedAndReplacedWithTheEvent() = runBlocking {
        val alert = Reminders.METHOD_ALERT
        val id = ContentUris.parseId(repository.create(draft().copy(reminders = listOf(EventReminder(30, alert), EventReminder(10, alert)))))
        assertEquals(listOf(10 to alert, 30 to alert), reminderRows(id))
        val original = repository.details(id)!!
        assertEquals(listOf(EventReminder(10, alert), EventReminder(30, alert)), original.reminders)
        repository.update(original, original.event.copy(title = "Retimed", reminders = listOf(EventReminder(60, alert))))
        assertEquals(listOf(60 to alert), reminderRows(id))
        assertEquals("Retimed", repository.details(id)!!.event.title)
        val single = repository.details(id)!!
        repository.update(single, single.event.copy(reminders = emptyList()))
        assertTrue(reminderRows(id).isEmpty())
    }

    @Test fun staleReminderEditRollsBackTheWholeSave() = runBlocking {
        val alert = Reminders.METHOD_ALERT
        val id = ContentUris.parseId(repository.create(draft().copy(reminders = listOf(EventReminder(10, alert)))))
        val original = repository.details(id)!!
        resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, id); put(Reminders.MINUTES, 45); put(Reminders.METHOD, alert)
        })
        try {
            repository.update(original, original.event.copy(title = "Must not save", reminders = listOf(EventReminder(5, alert))))
            fail("Stale reminder replacement must fail")
        } catch (_: OperationApplicationException) { }
        assertEquals(listOf(10 to alert, 45 to alert), reminderRows(id))
        assertEquals(draft().title, repository.details(id)!!.event.title)
    }

    @Test fun reminderLimitsAndMethodsAreEnforced() = runBlocking {
        val tooMany = (1..6).map { EventReminder(it * 5, Reminders.METHOD_ALERT) }
        try { repository.create(draft().copy(reminders = tooMany)); fail("Six reminders exceed the calendar's limit of five") }
        catch (_: IllegalArgumentException) { }
        try { repository.create(draft().copy(reminders = listOf(EventReminder(10, Reminders.METHOD_EMAIL)))); fail("Email is not allowed here") }
        catch (_: IllegalArgumentException) { }
        assertTrue(repository.events(LocalDate.of(2030, 4, 10), LocalDate.of(2030, 4, 11), ZoneOffset.UTC).none { it.calendarId == calendarId })
    }

    /** [synced] gives the series a sync ID, as an account's sync adapter would. */
    private fun series(rule: String, reminderMinutes: Int? = 15, synced: Boolean = false): Long {
        val uri = if (synced) syncUri(Events.CONTENT_URI) else Events.CONTENT_URI
        val id = ContentUris.parseId(resolver.insert(uri, ContentValues().apply {
            if (synced) put(Events._SYNC_ID, "fictional-${UUID.randomUUID()}")
            put(Events.CALENDAR_ID, calendarId); put(Events.TITLE, "Fictional standup")
            put(Events.DTSTART, draft().begin); put(Events.DURATION, "P3600S")
            put(Events.EVENT_TIMEZONE, "UTC"); put(Events.RRULE, rule)
        })!!)
        reminderMinutes?.let { minutes -> resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, id); put(Reminders.MINUTES, minutes); put(Reminders.METHOD, Reminders.METHOD_ALERT)
        }) }
        return id
    }

    private suspend fun ours() = repository.events(LocalDate.of(2030, 4, 9), LocalDate.of(2030, 4, 20), ZoneOffset.UTC)
        .filter { it.calendarId == calendarId }.sortedBy { it.begin }

    /** Raw rows of the test calendar, attached to assertion messages for provider differences. */
    private fun dump(): String = resolver.query(Events.CONTENT_URI, arrayOf(Events._ID, Events.TITLE, Events.DTSTART, Events.DTEND,
        Events.DURATION, Events.RRULE, Events.STATUS, Events.ORIGINAL_ID, Events.ORIGINAL_INSTANCE_TIME, Events.DELETED, Events.LAST_DATE),
        "${Events.CALENDAR_ID} = ?", arrayOf(calendarId.toString()), null)!!.use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString(",") { "${c.getColumnName(it)}=${c.getString(it)}" }) }
    }.joinToString("\n")

    private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).dayOfMonth

    @Test fun thisOccurrenceEditBecomesExceptionWithSeriesReminders() = runBlocking {
        val id = series("FREQ=DAILY;COUNT=3", synced = true)
        val occurrence = ours()[1]
        val original = repository.details(id, occurrence)!!
        assertEquals(RecurrenceScope.entries, original.recurrenceScopes)
        val moved = original.event.copy(title = "Moved standup", begin = occurrence.begin + 3_600_000, end = occurrence.end + 3_600_000)
        repository.updateOccurrences(original, occurrence, moved, RecurrenceScope.THIS)
        val after = ours()
        assertEquals(dump(), listOf("Fictional standup", "Moved standup", "Fictional standup"), after.map { it.title })
        assertEquals(occurrence.begin + 3_600_000, after[1].begin)
        val exception = repository.details(after[1].id, after[1])!!
        assertTrue(exception.isException)
        assertEquals(listOf(RecurrenceScope.THIS), exception.recurrenceScopes)
        assertEquals(listOf(EventReminder(15, Reminders.METHOD_ALERT)), exception.reminders)
        assertEquals("Fictional standup", repository.details(id)!!.event.title)
        // Deleting the changed occurrence cancels it and leaves the rest of the series.
        repository.deleteOccurrences(exception, after[1], RecurrenceScope.THIS)
        assertEquals(listOf(10, 12), ours().map { day(it.begin) })
    }

    @Test fun thisOccurrenceDeleteHidesOnlyThatDay() = runBlocking {
        for (synced in listOf(true, false)) {
            val id = series("FREQ=DAILY;COUNT=3", synced = synced)
            val occurrence = ours()[1]
            repository.deleteOccurrences(repository.details(id, occurrence)!!, occurrence, RecurrenceScope.THIS)
            assertEquals(dump(), listOf(10, 12), ours().map { day(it.begin) })
            repository.deleteOccurrences(repository.details(id, ours()[0])!!, ours()[0], RecurrenceScope.ALL)
            assertTrue(dump(), ours().isEmpty())
        }
    }

    @Test fun deviceOnlySeriesEditDetachesOneOccurrenceThenCountsIt() = runBlocking {
        val id = series("FREQ=DAILY;COUNT=4")
        val occurrence = ours()[1]
        val original = repository.details(id, occurrence)!!
        assertFalse(original.synced)
        repository.updateOccurrences(original, occurrence, original.event.copy(title = "Moved standup",
            begin = occurrence.begin + 3_600_000, end = occurrence.end + 3_600_000), RecurrenceScope.THIS)
        val after = ours()
        assertEquals(dump(), listOf("Fictional standup", "Moved standup", "Fictional standup", "Fictional standup"), after.map { it.title })
        val detached = repository.details(after[1].id, after[1])!!
        assertFalse(detached.repeating)
        assertEquals(listOf(EventReminder(15, Reminders.METHOD_ALERT)), detached.reminders)
        // A later split must still count the detached date toward COUNT=4.
        val third = after[2]
        val series = repository.details(id, third)!!
        repository.updateOccurrences(series, third, series.event.copy(title = "Later standup", begin = third.begin, end = third.end),
            RecurrenceScope.FOLLOWING)
        assertEquals(dump(), listOf("Fictional standup", "Moved standup", "Later standup", "Later standup"), ours().map { it.title })
    }

    @Test fun followingEditSplitsSeriesAndKeepsTotalCount() = runBlocking {
        val id = series("FREQ=DAILY;COUNT=4")
        val occurrence = ours()[2]
        val original = repository.details(id, occurrence)!!
        repository.updateOccurrences(original, occurrence, original.event.copy(title = "Later standup",
            begin = occurrence.begin, end = occurrence.end, reminders = emptyList()), RecurrenceScope.FOLLOWING)
        val after = ours()
        assertEquals(dump(), listOf(10, 11, 12, 13), after.map { day(it.begin) })
        assertEquals(listOf("Fictional standup", "Fictional standup", "Later standup", "Later standup"), after.map { it.title })
        assertEquals(setOf(id), after.take(2).map { it.id }.toSet())
        val newSeries = repository.details(after[2].id, after[2])!!
        assertEquals("FREQ=DAILY;COUNT=2", newSeries.recurrence)
        assertTrue(newSeries.reminders.isEmpty())
        assertEquals(1, repository.details(id)!!.reminders.size)
    }

    @Test fun followingDeleteEndsSeriesBeforeOccurrence() = runBlocking {
        val id = series("FREQ=DAILY")
        val occurrence = ours()[3]
        repository.deleteOccurrences(repository.details(id, occurrence)!!, occurrence, RecurrenceScope.FOLLOWING)
        assertEquals(dump(), listOf(10, 11, 12), ours().map { day(it.begin) })
    }

    @Test fun allEventsEditShiftsWholeSeries() = runBlocking {
        val id = series("FREQ=DAILY;COUNT=3")
        val occurrence = ours()[1]
        val original = repository.details(id, occurrence)!!
        repository.updateOccurrences(original, occurrence, original.event.copy(title = "Later standup",
            begin = occurrence.begin + 1_800_000, end = occurrence.end + 3_600_000), RecurrenceScope.ALL)
        val after = ours()
        assertEquals(setOf(id), after.map { it.id }.toSet())
        assertEquals(dump(), listOf(Instant.parse("2030-04-10T09:30:00Z"), Instant.parse("2030-04-11T09:30:00Z"), Instant.parse("2030-04-12T09:30:00Z")),
            after.map { Instant.ofEpochMilli(it.begin) })
        assertTrue(after.all { it.end - it.begin == 5_400_000L && it.title == "Later standup" })
        repository.deleteOccurrences(repository.details(id, after[0])!!, after[0], RecurrenceScope.ALL)
        assertTrue(ours().isEmpty())
    }

    @Test fun staleSeriesChangesAreRejected() = runBlocking {
        val id = series("FREQ=DAILY;COUNT=3")
        val occurrence = ours()[1]
        val original = repository.details(id, occurrence)!!
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), ContentValues().apply { put(Events.TITLE, "External update") }, null, null)
        for (scope in RecurrenceScope.entries) {
            try { repository.updateOccurrences(original, occurrence, original.event.copy(title = "Stale"), scope); fail("Stale $scope edit must fail") }
            catch (_: OperationApplicationException) { }
            try { repository.deleteOccurrences(original, occurrence, scope); fail("Stale $scope delete must fail") }
            catch (_: OperationApplicationException) { }
        }
        assertEquals(List(3) { "External update" }, ours().map { it.title })
    }

    @Test fun retriedSavesDoNotDuplicateOrFail() = runBlocking {
        val draft = draft().copy(uid = "fictional-${UUID.randomUUID()}", reminders = listOf(EventReminder(10, Reminders.METHOD_ALERT)))
        val first = repository.create(draft)
        assertEquals(first, repository.create(draft))
        assertEquals(1, ours().size)
        assertEquals(listOf(10 to Reminders.METHOD_ALERT), reminderRows(ContentUris.parseId(first)))
        // An update retried from the same snapshot after it already landed succeeds without changes.
        val original = repository.details(ContentUris.parseId(first))!!
        val changed = original.event.copy(title = "Retried title", reminders = emptyList())
        repository.update(original, changed)
        repository.update(original, changed)
        assertEquals("Retried title", repository.details(original.id)!!.event.title)
        // A delete retried after it landed is not an error.
        repository.delete(original.copy(event = changed.copy(title = "Retried title"), reminders = emptyList()))
        repository.delete(original.copy(event = changed.copy(title = "Retried title"), reminders = emptyList()))
        assertTrue(ours().isEmpty())
    }

    @Test fun retriedSingleOccurrenceChangeIsRejectedInsteadOfDuplicated() = runBlocking {
        val id = series("FREQ=DAILY;COUNT=3", synced = true)
        val occurrence = ours()[1]
        val original = repository.details(id, occurrence)!!
        val moved = original.event.copy(title = "Moved standup", begin = occurrence.begin, end = occurrence.end)
        repository.updateOccurrences(original, occurrence, moved, RecurrenceScope.THIS)
        try { repository.updateOccurrences(original, occurrence, moved, RecurrenceScope.THIS); fail("A second exception must be refused") }
        catch (_: CalendarChangeRejected) { }
        assertEquals(dump(), 3, ours().size)
    }

    @Test fun readOnlyCalendarRejectsWrites() = runBlocking {
        val id = ContentUris.parseId(repository.create(draft()))
        resolver.update(syncUri(calendarUri!!), ContentValues().apply { put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ) }, null, null)
        val details = repository.details(id)!!
        assertFalse(details.canEdit)
        try { repository.create(draft()); fail("Read-only create must fail") } catch (_: IllegalArgumentException) { }
        try { repository.update(details, details.event.copy(notes = "Must not save")); fail("Read-only edit must fail") } catch (_: IllegalArgumentException) { }
        try { repository.delete(details); fail("Read-only delete must fail") } catch (_: IllegalArgumentException) { }
    }
}
