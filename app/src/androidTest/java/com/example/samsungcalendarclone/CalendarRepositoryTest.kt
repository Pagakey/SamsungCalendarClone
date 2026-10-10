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
import com.example.samsungcalendarclone.domain.model.NewEvent
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
        assertEquals(draft(), original.event)
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
