package com.example.samsungcalendarclone

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.samsungcalendarclone.domain.model.*
import com.example.samsungcalendarclone.ui.theme.SamsungCalendarCloneTheme
import com.example.samsungcalendarclone.ui.views.AgendaList
import com.example.samsungcalendarclone.ui.views.TimeGrid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Renders the time grid and agenda with fictional events, checks taps, and saves light/dark captures. */
@RunWith(AndroidJUnit4::class)
class ViewsRenderTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("Europe/Athens")
    private val monday = LocalDate.of(2031, 2, 10)
    private var opened: CalendarEvent? = null

    private fun event(id: Long, day: LocalDate, from: Int, to: Int, title: String, color: Int, place: String = "") =
        CalendarEvent(id, 1, title, day.atTime(from / 60, from % 60).atZone(zone).toInstant().toEpochMilli(),
            day.atTime(to / 60, to % 60).atZone(zone).toInstant().toEpochMilli(), false, color, place)

    private val events = listOf(
        event(1, monday, 9 * 60, 10 * 60, "Design review", 0xFF5E8BC8.toInt(), "Studio"),
        event(2, monday, 9 * 60 + 30, 11 * 60, "Pairing session", 0xFF4CAF7A.toInt()),
        event(3, monday.plusDays(2), 13 * 60, 14 * 60, "Lunch with Alex", 0xFFE0A030.toInt(), "Harbour cafe"),
        event(4, monday.plusDays(4), 17 * 60, 19 * 60, "Climbing", 0xFFCC6464.toInt()),
        CalendarEvent(5, 1, "Conference", monday.plusDays(3).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            monday.plusDays(5).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), true, 0xFF8E6CC8.toInt(), "Fictional Hall"),
    )
    private val days = weekDates(monday)
    private val byDate = eventsByDate(events, days, zone)

    private fun capture(name: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun weekGridPlacesAndOpensEvents() {
        var dark by mutableStateOf(false)
        compose.setContent {
            SamsungCalendarCloneTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TimeGrid(days, byDate, monday, monday, zone, onOpenEvent = { opened = it }, onStep = {}, onOpenDay = {}, modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithContentDescription("Pairing session", substring = true).assertIsDisplayed()
        val tree = compose.onRoot(useUnmergedTree = true).printToString()
        assertTrue(tree, compose.onAllNodesWithContentDescription("Conference, All day", substring = true).fetchSemanticsNodes().isNotEmpty())
        capture("week-light.png")
        compose.onNodeWithContentDescription("Design review", substring = true).performClick()
        compose.runOnIdle { assertEquals(1L, opened?.id) }
        dark = true
        compose.waitForIdle()
        capture("week-dark.png")
    }

    @Test fun dayGridShowsTimesAndAgendaListsDays() {
        var agenda by mutableStateOf(false)
        compose.setContent {
            SamsungCalendarCloneTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (agenda) AgendaList(days, byDate, monday, zone, canShowMore = true, emptyText = "None", onOpenEvent = { opened = it }, onShowMore = {})
                    else TimeGrid(listOf(monday), byDate, monday, monday, zone, onOpenEvent = { opened = it }, onStep = {}, onOpenDay = null, modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNodeWithContentDescription("Design review, 09:00 – 10:00", substring = true).assertIsDisplayed()
        capture("day-light.png")
        agenda = true
        compose.onNodeWithText("Lunch with Alex").assertIsDisplayed()
        capture("agenda-light.png")
        // Lazy rows past the screen aren't composed until scrolled to.
        compose.onNodeWithTag("agenda").performScrollToNode(hasText("Show more"))
        compose.onNodeWithText("Show more").assertIsDisplayed()
    }
}
