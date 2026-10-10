package com.example.samsungcalendarclone.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.samsungcalendarclone.domain.model.*
import com.example.samsungcalendarclone.ui.month.eventColor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val gutter = 44.dp

/**
 * Day and week layout: an all-day strip above a scrollable 24-hour grid. A horizontal swipe
 * moves a page ([onStep]); tapping a week's day header opens that day ([onOpenDay]).
 */
@Composable
fun TimeGrid(
    days: List<LocalDate>,
    events: Map<LocalDate, List<CalendarEvent>>,
    today: LocalDate,
    selected: LocalDate,
    zone: ZoneId,
    onOpenEvent: (CalendarEvent) -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onOpenDay: ((LocalDate) -> Unit)?,
    modifier: Modifier = Modifier,
    onCreateAt: ((LocalDate, Int) -> Unit)? = null,
) {
    val week = days.size > 1
    val hourHeight = if (week) 48.dp else 60.dp
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val scroll = rememberScrollState()
    val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
    // Open near the first timed event, or at 07:00 on an empty page.
    LaunchedEffect(days.first()) {
        val first = days.flatMap { day -> layoutDay(events[day].orEmpty(), day, zone) }.minOfOrNull { it.startMinute }
        scroll.scrollTo((((first ?: (7 * 60)) / 60 - 1).coerceAtLeast(0) * hourPx).toInt())
    }
    Column(modifier.testTag(if (week) "week-grid" else "day-grid").pointerInput(days.first()) {
        var drag = 0f
        detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
            if (abs(drag) > swipeThreshold) onStep(drag < 0)
        }) { _, amount -> drag += amount }
    }) {
        if (week) Row(Modifier.fillMaxWidth().padding(start = gutter)) {
            days.forEach { day ->
                val isToday = day == today
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = onOpenDay != null) { onOpenDay?.invoke(day) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")) + if (isToday) ", today" else ""
                        this.selected = day == selected
                    }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(28.dp).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(day.dayOfMonth.toString(), fontSize = 14.sp, fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        AllDayStrip(days, events, week, onOpenEvent)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Column(Modifier.width(gutter)) {
                (0 until 24).forEach { hour ->
                    Box(Modifier.height(hourHeight).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        if (hour > 0) Text("%02d:00".format(hour), Modifier.padding(end = 6.dp).offset(y = (-9).dp), fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            days.forEach { day ->
                DayColumn(day, events[day].orEmpty(), today, zone, hourHeight, week, onOpenEvent, Modifier.weight(1f).then(
                    if (onCreateAt == null) Modifier else Modifier.pointerInput(day) {
                        // Taps on events are consumed by their chips; an empty slot starts a new event on the half hour.
                        detectTapGestures { offset -> onCreateAt(day, ((offset.y / hourHeight.toPx() * 60).toInt() / 30 * 30).coerceIn(0, MINUTES_PER_DAY - 30)) }
                    }))
            }
        }
    }
}

@Composable
private fun AllDayStrip(days: List<LocalDate>, events: Map<LocalDate, List<CalendarEvent>>, week: Boolean, onOpenEvent: (CalendarEvent) -> Unit) {
    val allDay = days.map { day -> events[day].orEmpty().filter { it.allDay } }
    if (allDay.all { it.isEmpty() }) return
    val shown = if (week) 2 else 3
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("All day", Modifier.width(gutter).padding(top = 2.dp, end = 4.dp), fontSize = 9.sp, lineHeight = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        allDay.forEach { list ->
            Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                list.take(shown).forEach { event -> EventChip(event, null, week, onOpenEvent, Modifier.fillMaxWidth()) }
                if (list.size > shown) Text("+${list.size - shown}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayColumn(day: LocalDate, events: List<CalendarEvent>, today: LocalDate, zone: ZoneId, hourHeight: Dp, week: Boolean,
                      onOpenEvent: (CalendarEvent) -> Unit, modifier: Modifier) {
    val blocks = remember(events, day, zone) { layoutDay(events, day, zone) }
    val line = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(modifier.height(hourHeight * 24)) {
        Column { repeat(24) { Box(Modifier.height(hourHeight).fillMaxWidth()) { HorizontalDivider(color = line.copy(alpha = .6f)) } } }
        blocks.forEach { block ->
            val width = maxWidth / block.columns
            val time = "${minuteLabel(block.event.begin, zone)} – ${minuteLabel(block.event.end, zone)}"
            EventChip(block.event, if (week) null else time, week, onOpenEvent,
                Modifier.offset(x = width * block.column, y = hourHeight * block.startMinute / 60)
                    .width(width).height(hourHeight * (block.endMinute - block.startMinute) / 60).padding(1.dp))
        }
        if (day == today) {
            var now by remember { mutableStateOf(LocalTime.now(zone)) }
            LaunchedEffect(day) { while (true) { delay(60_000); now = LocalTime.now(zone) } }
            Box(Modifier.offset { IntOffset(0, (hourHeight * (now.hour * 60 + now.minute) / 60).roundToPx()) }.fillMaxWidth().height(2.dp)
                .background(MaterialTheme.colorScheme.error).semantics { contentDescription = "Current time" })
        }
    }
}

@Composable
private fun EventChip(event: CalendarEvent, time: String?, week: Boolean, onOpenEvent: (CalendarEvent) -> Unit, modifier: Modifier) {
    val color = eventColor(event.color)
    // A solid edge keeps the calendar color readable on the translucent fill in dark mode.
    Column(modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = .22f))
        .drawBehind { drawRect(color, size = size.copy(width = 3.dp.toPx())) }.clickable { onOpenEvent(event) }
        .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(event.title, time ?: if (event.allDay) "All day" else null).joinToString(", ") }
        .padding(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)) {
        Text(event.title, fontSize = if (week) 10.sp else 13.sp, lineHeight = if (week) 12.sp else 15.sp,
            maxLines = if (week) 3 else 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        if (time != null) Text(time + if (event.location.isBlank()) "" else " · ${event.location}", fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun minuteLabel(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).format(timeFormat)
