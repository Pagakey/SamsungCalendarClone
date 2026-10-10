package com.example.samsungcalendarclone

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.samsungcalendarclone.domain.model.DeviceCalendar
import com.example.samsungcalendarclone.domain.model.EventReminder
import com.example.samsungcalendarclone.domain.model.NewEvent
import com.example.samsungcalendarclone.domain.model.REMINDER_ALERT
import com.example.samsungcalendarclone.ui.event.EventEditor
import com.example.samsungcalendarclone.ui.theme.SamsungCalendarCloneTheme
import org.junit.Assert.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EventEditorTest {
    @get:Rule val compose = createComposeRule()
    private var saved: NewEvent? = null
    private fun content(initial: NewEvent? = null, calendars: List<DeviceCalendar> = listOf(DeviceCalendar(1, "Test calendar", "Test account", 0, true)),
                        saving: Boolean = false, saveError: String? = null): @Composable () -> Unit = {
        SamsungCalendarCloneTheme {
            EventEditor(LocalDate.of(2026, 10, 10), calendars, onDismiss = {}, onSave = { saved = it }, initial = initial,
                saving = saving, saveError = saveError)
        }
    }
    private fun editor(initial: NewEvent? = null, calendars: List<DeviceCalendar> = listOf(DeviceCalendar(1, "Test calendar", "Test account", 0, true)),
                       saving: Boolean = false, saveError: String? = null) {
        compose.setContent(content(initial, calendars, saving, saveError))
    }
    @Test fun draftSurvivesStateRestorationWithTheSameEventUid() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content())
        compose.onNodeWithText("Event title").performTextInput("Fictional review")
        compose.onNodeWithContentDescription("Remove reminder 10 minutes before").performScrollTo().performClick()
        compose.onNodeWithText("Add reminder").performScrollTo().performClick()
        compose.onNodeWithText("1 hour before").performClick()
        compose.onNodeWithText("Save").performClick()
        val first = compose.runOnIdle { saved }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Fictional review").assertIsDisplayed()
        compose.onNodeWithText("1 hour before").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertNotNull(first?.uid)
            assertEquals(first, saved)
            assertEquals(listOf(EventReminder(60, REMINDER_ALERT)), saved?.reminders)
        }
    }
    @Test fun inFlightSaveDisablesSaveAndFailuresAreShown() {
        editor(saving = true, saveError = "This event changed elsewhere. Close it and open it again.")
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.onNodeWithText("This event changed elsewhere.", substring = true).performScrollTo().assertIsDisplayed()
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
    @Test fun newTimedEventDefaultsToTenMinuteReminderThatCanBeChanged() {
        editor()
        compose.onNodeWithText("Event title").performTextInput("Planning")
        compose.onNodeWithText("10 minutes before").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove reminder 10 minutes before").performClick()
        compose.onNodeWithText("No reminders").assertIsDisplayed()
        compose.onNodeWithText("Add reminder").performScrollTo().performClick()
        compose.onNodeWithText("1 hour before").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf(EventReminder(60, REMINDER_ALERT)), saved?.reminders) }
    }
    @Test fun allDayNewEventDropsTimedDefaultReminder() {
        editor()
        compose.onNodeWithText("Event title").performTextInput("Holiday")
        compose.onNodeWithContentDescription("All day").performClick()
        compose.onNodeWithText("No reminders").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(emptyList<EventReminder>(), saved?.reminders) }
    }
    @Test fun reminderLimitDisablesAdding() {
        editor(calendars = listOf(DeviceCalendar(1, "Test calendar", "Test account", 0, true, maxReminders = 1)))
        compose.onNodeWithText("Reminder limit reached").performScrollTo().assertIsNotEnabled()
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
