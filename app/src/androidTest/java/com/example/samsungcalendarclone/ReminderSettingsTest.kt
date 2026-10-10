package com.example.samsungcalendarclone

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.samsungcalendarclone.ui.month.MonthUiState
import com.example.samsungcalendarclone.ui.month.ReminderSettings
import com.example.samsungcalendarclone.ui.theme.SamsungCalendarCloneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Only the settings block is shown here, so no system permission prompt or stored preference is touched. */
@RunWith(AndroidJUnit4::class)
class ReminderSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val toggles = mutableListOf<Boolean>()
    private var appInfo = 0

    private fun show(state: MonthUiState, xiaomi: Boolean) = compose.setContent {
        SamsungCalendarCloneTheme { ReminderSettings(state, xiaomi, onToggle = { toggles += it }, onNotificationSettings = {}, onAppInfo = { appInfo++ }) }
    }

    @Test fun offByDefaultExplainsTheDeviceCalendarAlerts() {
        show(MonthUiState(), xiaomi = true)
        compose.onNodeWithContentDescription("Reminder alerts from this app").assertIsOff().performClick()
        compose.onNodeWithText("Your device calendar app shows reminders.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("App info").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(true), toggles) }
    }

    @Test fun onWithBlockedNotificationsOnXiaomiShowsWhatToFix() {
        show(MonthUiState(hasReadPermission = true, appReminders = true, notificationsAllowed = false), xiaomi = true)
        compose.onNodeWithText("Turn off its notifications to avoid duplicates.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Notifications are blocked for this app.").assertIsDisplayed()
        compose.onNodeWithText("allow Autostart", substring = true).assertIsDisplayed()
        compose.onNodeWithText("App info").performClick()
        compose.runOnIdle { assertTrue(appInfo == 1) }
    }

    @Test fun otherPhonesOnlyMentionBatteryWhenRestricted() {
        show(MonthUiState(hasReadPermission = true, appReminders = true, notificationsAllowed = true, batteryUnrestricted = true), xiaomi = false)
        compose.onNodeWithText("App info").assertDoesNotExist()
    }
}
