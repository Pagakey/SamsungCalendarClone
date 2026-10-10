package com.example.samsungcalendarclone.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.samsungcalendarclone.domain.model.CalendarEvent
import com.example.samsungcalendarclone.ui.month.eventColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Upcoming days that have events, from the first loaded date onward. */
@Composable
fun AgendaList(
    dates: List<LocalDate>,
    events: Map<LocalDate, List<CalendarEvent>>,
    today: LocalDate,
    zone: ZoneId,
    canShowMore: Boolean,
    emptyText: String,
    onOpenEvent: (CalendarEvent) -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = dates.filter { !events[it].isNullOrEmpty() }
    LazyColumn(modifier.testTag("agenda"), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (days.isEmpty()) item {
            Text(emptyText, Modifier.padding(horizontal = 24.dp, vertical = 20.dp), fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        days.forEach { day ->
            item(key = "day:$day") {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (day == today) Text("  ·  Today", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(events[day].orEmpty(), key = { "$day:${it.id}:${it.begin}" }) { event -> EventRow(event, zone, onOpenEvent) }
        }
        if (canShowMore) item {
            TextButton(onClick = onShowMore, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { Text("Show more") }
        }
    }
}

/** One event line shared by the agenda and search results. */
@Composable
fun EventRow(event: CalendarEvent, zone: ZoneId, onOpenEvent: (CalendarEvent) -> Unit, showDate: Boolean = false) {
    val time = if (event.allDay) "All day" else {
        val start = Instant.ofEpochMilli(event.begin).atZone(zone)
        val end = Instant.ofEpochMilli(event.end).atZone(zone)
        "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} – ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }
    val date = if (showDate) Instant.ofEpochMilli(event.begin).atZone(if (event.allDay) java.time.ZoneOffset.UTC else zone)
        .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) + " · " else ""
    Row(Modifier.fillMaxWidth().clickable { onOpenEvent(event) }.padding(horizontal = 24.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(32.dp).clip(CircleShape).background(eventColor(event.color)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(event.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date + time + if (event.location.isBlank()) "" else " · ${event.location}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
