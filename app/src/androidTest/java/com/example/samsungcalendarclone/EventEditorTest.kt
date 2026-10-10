package com.example.samsungcalendarclone

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.samsungcalendarclone.domain.model.DeviceCalendar
import com.example.samsungcalendarclone.domain.model.NewEvent
import com.example.samsungcalendarclone.ui.event.EventEditor
import com.example.samsungcalendarclone.ui.theme.SamsungCalendarCloneTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EventEditorTest {
    @get:Rule val compose = createComposeRule()
    private var saved: NewEvent? = null
    private fun editor(initial: NewEvent? = null, calendars: List<DeviceCalendar> = listOf(DeviceCalendar(1, "Test calendar", "Test account", 0, true))) {
        compose.setContent {
            SamsungCalendarCloneTheme {
                EventEditor(LocalDate.of(2026, 10, 10), calendars, onDismiss = {}, onSave = { saved = it }, initial = initial)
            }
        }
    }
    @Test fun savesAllDayWithExclusiveUtcEnd() {
        editor()
        compose.onNodeWithText("Event title").performTextInput("A day off")
        compose.onNodeWithContentDescription("All day").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals("A day off", saved?.title)
            assertEquals(Instant.parse("2026-10-10T00:00:00Z").toEpochMilli(), saved?.begin)
            assertEquals(Instant.parse("2026-10-11T00:00:00Z").toEpochMilli(), saved?.end)
            assertEquals(true, saved?.allDay)
        }
    }
    @Test fun blankTitleCannotBeSaved() {
        editor()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Give your event a title.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertNull(saved) }
    }
    @Test fun noWritableCalendarDisablesSave() {
        editor(calendars = emptyList())
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("No writable calendars found.", substring = true).assertIsDisplayed()
    }
    @Test fun editsExistingEventWithoutShiftingItsTimestamp() {
        val initial = NewEvent("Original title", 1, Instant.parse("2026-10-25T01:30:24.123Z").toEpochMilli(),
            Instant.parse("2026-10-25T02:30:24.123Z").toEpochMilli(), false, "Europe/Athens", "Room", "Notes")
        editor(initial)
        compose.onNodeWithText("Edit event").assertIsDisplayed()
        compose.onNodeWithText("Original title").performTextReplacement("Changed title")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(initial.copy(title = "Changed title"), saved) }
    }
    @Test fun changingOnlyAllDayRequiresDiscardConfirmation() {
        editor()
        compose.onNodeWithContentDescription("All day").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard this event?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithContentDescription("All day").assertIsOn()
    }

}
