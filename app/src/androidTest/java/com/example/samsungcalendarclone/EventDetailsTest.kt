package com.example.samsungcalendarclone

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.samsungcalendarclone.domain.model.*
import com.example.samsungcalendarclone.ui.event.EventDetailsSheet
import com.example.samsungcalendarclone.ui.theme.SamsungCalendarCloneTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class EventDetailsTest {
    @get:Rule val compose = createComposeRule()
    private var deleted = false
    private var deletedScope: RecurrenceScope? = null
    private val event = NewEvent("Fictional coffee", 1, Instant.parse("2030-04-10T09:00:00Z").toEpochMilli(),
        Instant.parse("2030-04-10T10:00:00Z").toEpochMilli(), false, "UTC", "Cafe", "Bring a notebook")
    private fun show(repeating: Boolean = false, writable: Boolean = true, recurrenceDates: String? = null) {
        val details = EventDetails(1, event, DeviceCalendar(1, "Test calendar", "Local test", 0, writable),
            if (repeating) "FREQ=DAILY" else null, recurrenceDates, null, "UTC")
        compose.setContent { SamsungCalendarCloneTheme {
            EventDetailsSheet(CalendarEvent(1, 1, event.title, event.begin, event.end, false, 0, event.location),
                details, false, null, true, onDismiss = {}, onRetry = {}, onAllowEditing = {},
                onEdit = { _, _ -> }, onDelete = { _, scope -> deleted = true; deletedScope = scope }, onOpenExternal = {})
        } }
    }
    @Test fun deleteRequiresConfirmationAndCanBeCancelled() {
        show()
        compose.onNodeWithText("Bring a notebook").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete this event?").assertIsDisplayed()
        compose.onNodeWithText("Keep event").performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete event").performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }
    @Test fun repeatingDeleteAsksWhichEvents() {
        show(repeating = true)
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete repeating event?").assertIsDisplayed()
        compose.onNodeWithText("This and following events").assertIsDisplayed()
        compose.onNodeWithText("All events").performClick()
        compose.onNodeWithText("Delete event").performClick()
        compose.runOnIdle { assertTrue(deleted); assertEquals(RecurrenceScope.ALL, deletedScope) }
    }
    @Test fun unsupportedRepeatingEventHasExplicitHandoffWithoutDestructiveActions() {
        show(repeating = true, recurrenceDates = "20300415T090000Z")
        compose.onNodeWithText("This is part of a repeating event.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Open in calendar app").performScrollTo().assertIsDisplayed()
    }
    @Test fun readOnlyCalendarHasNoEditOrDeleteAction() {
        show(writable = false)
        compose.onNodeWithText("This calendar is read-only or no longer available.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }
}
