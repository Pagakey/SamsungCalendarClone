package com.example.samsungcalendarclone.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.*
import java.util.concurrent.TimeUnit
import com.example.samsungcalendarclone.MainActivity
import com.example.samsungcalendarclone.data.calendar.AndroidCalendarRepository
import com.example.samsungcalendarclone.data.reminders.ReminderAlerts
import com.example.samsungcalendarclone.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

const val ACTION_OPEN_DATE = "com.example.samsungcalendarclone.OPEN_DATE"
const val EXTRA_DATE = "epoch_day"
private const val AGENDA_DAYS = 14
private const val AGENDA_LIMIT = 40

/** Events for the widgets, honouring the app's hidden calendars; null without calendar access. */
private suspend fun loadEvents(context: Context, start: LocalDate, endExclusive: LocalDate, zone: ZoneId): List<CalendarEvent>? {
    if (!ReminderAlerts.canRead(context)) return null
    val hidden = context.getSharedPreferences("calendar_preferences", 0).getStringSet("hidden", emptySet())
        .orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    return try {
        AndroidCalendarRepository(context).events(start, endExclusive, zone).filter { it.calendarId !in hidden }
    } catch (_: SecurityException) { null }
}

private fun openApp(context: Context, date: LocalDate? = null): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).apply {
        if (date != null) { action = ACTION_OPEN_DATE; putExtra(EXTRA_DATE, date.toEpochDay()) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    })

private fun openEvent(context: Context, event: CalendarEvent): Action =
    actionStartActivity(Intent(context, MainActivity::class.java).setAction(ReminderAlerts.ACTION_OPEN_EVENT)
        .putExtra(ReminderAlerts.EXTRA_EVENT_ID, event.id).putExtra(ReminderAlerts.EXTRA_BEGIN, event.begin)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))

private fun eventColor(raw: Int) = ColorProvider(if (raw == 0) Color(0xFF5E8BC8) else Color(raw).copy(alpha = 1f))

/** Upcoming two weeks, grouped by day. */
class AgendaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = loadEvents(context, today, today.plusDays(AGENDA_DAYS.toLong()), zone)
            ?.let { agendaDays(it, today, AGENDA_DAYS, zone, System.currentTimeMillis(), AGENDA_LIMIT) }
        // provideContent never returns, so follow-ups are scheduled first.
        WidgetUpdates.scheduleFollowUps(context)
        provideContent { GlanceTheme { AgendaContent(context, today, days, zone) } }
    }
}

@Composable
fun AgendaContent(context: Context, today: LocalDate, days: List<AgendaDay>?, zone: ZoneId) {
    val colors = GlanceTheme.colors
    Column(GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(20.dp).padding(14.dp)) {
        Row(GlanceModifier.fillMaxWidth().clickable(openApp(context, today)), verticalAlignment = Alignment.CenterVertically) {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), GlanceModifier.defaultWeight(),
                style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            Text("Agenda", style = TextStyle(color = colors.primary, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        when {
            days == null -> Text("Open the app to allow calendar access.", GlanceModifier.clickable(openApp(context)),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
            days.isEmpty() -> Text("No events in the next two weeks.", GlanceModifier.clickable(openApp(context)),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
            else -> LazyColumn {
                days.forEach { day ->
                    item {
                        Text(if (day.date == today) "Today" else if (day.date == today.plusDays(1)) "Tomorrow"
                            else day.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                            GlanceModifier.padding(top = 6.dp, bottom = 2.dp).clickable(openApp(context, day.date)),
                            style = TextStyle(color = if (day.date == today) colors.primary else colors.onSurfaceVariant, fontSize = 12.sp,
                                fontWeight = FontWeight.Medium))
                    }
                    items(day.events, itemId = { it.id * 31 + it.begin }) { event ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(openEvent(context, event)),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.width(3.dp).height(28.dp).cornerRadius(2.dp).background(eventColor(event.color))) {}
                            Column(GlanceModifier.padding(start = 8.dp)) {
                                Text(event.title, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 13.sp))
                                Text(if (event.allDay) "All day" else
                                    "${Instant.ofEpochMilli(event.begin).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))} – " +
                                        Instant.ofEpochMilli(event.end).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")) +
                                        if (event.location.isBlank()) "" else " · ${event.location}",
                                    maxLines = 1, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** This month at a glance; a day opens the app on that date. */
class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        val dates = monthDates(month)
        val events = loadEvents(context, dates.first(), dates.last().plusDays(1), zone)
        val cells = monthCells(month, today, events?.let { eventsByDate(it, dates, zone) }.orEmpty())
        // provideContent never returns, so follow-ups are scheduled first.
        WidgetUpdates.scheduleFollowUps(context)
        provideContent { GlanceTheme { MonthContent(context, month, cells, connected = events != null) } }
    }
}

@Composable
fun MonthContent(context: Context, month: YearMonth, cells: List<MonthCell>, connected: Boolean) {
    val colors = GlanceTheme.colors
    Column(GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(20.dp).padding(10.dp)) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp).clickable(openApp(context)), verticalAlignment = Alignment.CenterVertically) {
            Text(month.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()), GlanceModifier.defaultWeight(),
                style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            Text(if (connected) month.year.toString() else "Open to connect", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
        }
        Row(GlanceModifier.fillMaxWidth()) {
            (1..7).forEach { day ->
                Text(DayOfWeek.of(day).getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()), GlanceModifier.defaultWeight(),
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp, textAlign = androidx.glance.text.TextAlign.Center))
            }
        }
        cells.chunked(7).forEach { week ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                week.forEach { cell ->
                    Column(GlanceModifier.defaultWeight().fillMaxHeight().clickable(openApp(context, cell.date)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.size(24.dp).cornerRadius(12.dp).background(if (cell.today) colors.primary else ColorProvider(Color.Transparent)),
                            contentAlignment = Alignment.Center) {
                            Text(cell.date.dayOfMonth.toString(), style = TextStyle(fontSize = 12.sp,
                                color = when { cell.today -> colors.onPrimary; cell.inMonth -> colors.onSurface; else -> colors.outline },
                                fontWeight = if (cell.today) FontWeight.Bold else FontWeight.Normal))
                        }
                        Box(GlanceModifier.size(4.dp).cornerRadius(2.dp)
                            .background(if (cell.events > 0 && cell.inMonth) colors.primary else ColorProvider(Color.Transparent))) {}
                    }
                }
            }
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

/**
 * Keeps widgets current without the app running: a WorkManager content-URI trigger on the
 * calendar provider (edits, sync), an inexact alarm at the next local midnight (no exact-alarm
 * permission), and clock/time-zone broadcasts. WorkManager is used rather than a raw JobScheduler
 * job so its job IDs can't collide with the ones Glance's own WorkManager work uses.
 */
object WidgetUpdates {
    private const val WORK_NAME = "widget-calendar-changes"
    const val ACTION_MIDNIGHT = "com.example.samsungcalendarclone.WIDGET_MIDNIGHT"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun request(context: Context) {
        val app = context.applicationContext
        scope.launch { updateAll(app) }
    }

    suspend fun updateAll(context: Context) {
        AgendaWidget().updateAll(context)
        MonthWidget().updateAll(context)
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        return listOf(AgendaWidgetReceiver::class.java, MonthWidgetReceiver::class.java)
            .any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    fun scheduleFollowUps(context: Context) {
        val work = WorkManager.getInstance(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val midnight = PendingIntent.getBroadcast(context, 0, Intent(context, WidgetClockReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (!hasWidgets(context)) { work.cancelUniqueWork(WORK_NAME); alarms.cancel(midnight); return }
        // Content-trigger work runs once per change, so the widget update it causes schedules the next one.
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(2, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(30, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setConstraints(constraints).build())
        val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 5_000
        alarms.set(AlarmManager.RTC, next, midnight)
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdates.updateAll(applicationContext)
        return Result.success()
    }
}

/** Midnight, clock, locale and time-zone changes move "today" or its wording in the widgets. */
class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { WidgetUpdates.updateAll(context.applicationContext) } finally { pending.finish() }
        }
    }

    private companion object {
        val handled = setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED, WidgetUpdates.ACTION_MIDNIGHT)
    }
}
