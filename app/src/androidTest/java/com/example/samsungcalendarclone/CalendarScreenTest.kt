package com.example.samsungcalendarclone

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.samsungcalendarclone.ui.month.CalendarScreen
import com.example.samsungcalendarclone.ui.month.MonthViewModel
import com.example.samsungcalendarclone.ui.theme.SamsungCalendarCloneTheme
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun launch(dark: Boolean = false, saved: SavedStateHandle = SavedStateHandle()): MonthViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Default state intentionally has no permission/data, independent of device accounts.
        val model = MonthViewModel(app, saved)
        compose.setContent { SamsungCalendarCloneTheme(darkTheme = dark) { CalendarScreen(model) } }
        return model
    }
    private fun screenshot(name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun deniedPermissionStillShowsUsableMonth() {
        launch()
        compose.onNodeWithText("Connect").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add event").assertIsDisplayed()
        compose.onNodeWithText("Your day, at a glance").assertIsDisplayed()
        screenshot("month-light-denied.png")
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(YearMonth.now().plusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())).assertIsDisplayed()
        compose.onNodeWithText("Today", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(YearMonth.now().month.getDisplayName(TextStyle.FULL, Locale.getDefault())).assertIsDisplayed()
    }
    @Test fun darkModeAndDateSelection() {
        launch(dark = true)
        val date = LocalDate.now().withDayOfMonth(15)
        val description = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
        compose.onNodeWithContentDescription(description, substring = true).performClick().assertIsSelected()
        compose.onNodeWithText(date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))).assertIsDisplayed()
        screenshot("month-dark-denied.png")
        compose.onNodeWithText("Calendars").performClick()
        compose.onNodeWithText("Calendar access is off. You can still browse dates.").assertIsDisplayed()
    }
    @Test fun restoredStateReopensSavedMonthAndDate() {
        val date = LocalDate.of(2031, 2, 14)
        launch(saved = SavedStateHandle(mapOf("month" to "2031-02", "selected_date" to date.toEpochDay())))
        compose.onNodeWithText(date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())).assertIsDisplayed()
        compose.onNodeWithText("2031").assertIsDisplayed()
        compose.onNodeWithText(date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))).assertIsDisplayed()
    }
    @Test fun navigationIsRecordedForRestoration() {
        val saved = SavedStateHandle()
        launch(saved = saved)
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            val next = YearMonth.now().plusMonths(1)
            assertEquals(next.toString(), saved.get<String>("month"))
            assertEquals(next, YearMonth.from(LocalDate.ofEpochDay(saved.get<Long>("selected_date")!!)))
        }
    }
    @Test fun midnightMovesTodayAndASelectionThatWasOnIt() {
        val model = launch()
        val tomorrow = LocalDate.now().plusDays(1)
        compose.runOnIdle { model.onClockChanged(tomorrow) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(tomorrow, model.state.value.today)
            assertEquals(tomorrow, model.state.value.selectedDate)
        }
        compose.onNodeWithText(tomorrow.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))).assertIsDisplayed()
    }
    @Test fun midnightKeepsAnotherSelectedDay() {
        val model = launch()
        val chosen = LocalDate.now().withDayOfMonth(if (LocalDate.now().dayOfMonth == 15) 16 else 15)
        compose.runOnIdle { model.select(chosen); model.onClockChanged(LocalDate.now().plusDays(1)) }
        compose.runOnIdle { assertEquals(chosen, model.state.value.selectedDate) }
    }
    @Test fun horizontalSwipeMovesMonth() {
        launch()
        compose.onNodeWithTag("month-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText(YearMonth.now().plusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())).assertIsDisplayed()
    }
}
