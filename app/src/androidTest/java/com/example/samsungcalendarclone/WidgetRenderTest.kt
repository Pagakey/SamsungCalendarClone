package com.example.samsungcalendarclone

import android.app.Application
import android.graphics.Bitmap
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.samsungcalendarclone.domain.model.*
import com.example.samsungcalendarclone.ui.widget.AgendaContent
import com.example.samsungcalendarclone.ui.widget.MonthContent
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Composes the widgets into RemoteViews with fictional events and inflates them, as a launcher would. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("Europe/Athens")
    private val today = LocalDate.of(2031, 2, 10)

    private fun event(id: Long, day: LocalDate, from: Int, to: Int, title: String, color: Int, place: String = "") =
        CalendarEvent(id, 1, title, day.atTime(from, 0).atZone(zone).toInstant().toEpochMilli(),
            day.atTime(to, 0).atZone(zone).toInstant().toEpochMilli(), false, color, place)
    private val events = listOf(
        event(1, today, 9, 10, "Design review", 0xFF5E8BC8.toInt(), "Studio"),
        event(2, today, 13, 14, "Lunch with Alex", 0xFFE0A030.toInt(), "Harbour cafe"),
        event(3, today.plusDays(2), 17, 19, "Climbing", 0xFFCC6464.toInt()),
    )

    private fun show(views: RemoteViews, name: String, size: DpSize) {
        compose.setContent { AndroidView(factory = { context -> views.apply(context, FrameLayout(context)) }, modifier = Modifier.size(size.width, size.height)) }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun agendaWidgetInflates() = runBlocking {
        val days = agendaDays(events, today, 14, zone, now = today.atStartOfDay(zone).toInstant().toEpochMilli(), limit = 40)
        val size = DpSize(320.dp, 260.dp)
        val result = GlanceRemoteViews().compose(app, size) { GlanceTheme { AgendaContent(app, today, days, zone) } }
        assertNotNull(result.remoteViews)
        show(result.remoteViews, "widget-agenda.png", size)
    }

    /**
     * A launcher fills the agenda's list through a RemoteViews service, so an inflated capture shows
     * only the header. The Glance test host checks the list content itself.
     */
    @Test fun agendaWidgetListsUpcomingEvents() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 260.dp))
        setContext(app)
        val days = agendaDays(events, today, 14, zone, now = today.atStartOfDay(zone).toInstant().toEpochMilli(), limit = 40)
        provideComposable { GlanceTheme { AgendaContent(app, today, days, zone) } }
        onNode(hasText("Design review")).assertExists()
        onNode(hasText("09:00 – 10:00 · Studio")).assertExists()
        onNode(hasText("Today")).assertExists()
        onNode(hasText("Climbing")).assertExists()
    }

    @Test fun agendaWidgetExplainsMissingAccess() = runGlanceAppWidgetUnitTest {
        setContext(app)
        provideComposable { GlanceTheme { AgendaContent(app, today, null, zone) } }
        onNode(hasText("Open the app to allow calendar access.")).assertExists()
    }

    @Test fun monthWidgetInflates() = runBlocking {
        val month = YearMonth.from(today)
        val cells = monthCells(month, today, eventsByDate(events, monthDates(month), zone))
        val size = DpSize(320.dp, 320.dp)
        val result = GlanceRemoteViews().compose(app, size) { GlanceTheme { MonthContent(app, month, cells, connected = true) } }
        show(result.remoteViews, "widget-month.png", size)
    }
}
