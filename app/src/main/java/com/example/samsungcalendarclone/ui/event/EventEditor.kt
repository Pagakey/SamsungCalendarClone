package com.example.samsungcalendarclone.ui.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.samsungcalendarclone.domain.model.DeviceCalendar
import com.example.samsungcalendarclone.domain.model.NewEvent
import com.example.samsungcalendarclone.domain.model.editorInstant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun EventEditor(date: LocalDate, calendars: List<DeviceCalendar>, onDismiss: () -> Unit, onSave: suspend (NewEvent) -> Unit, initial: NewEvent? = null) {
    val zone = remember(initial?.zone) { ZoneId.of(initial?.zone ?: ZoneId.systemDefault().id) }
    val dateZone = if (initial?.allDay == true) ZoneOffset.UTC else zone
    val initialStart = initial?.let { Instant.ofEpochMilli(it.begin).atZone(dateZone) }
    val initialEnd = initial?.let { Instant.ofEpochMilli(if (it.allDay) it.end - 1 else it.end).atZone(dateZone) }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var location by rememberSaveable { mutableStateOf(initial?.location.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var calendarId by rememberSaveable { mutableStateOf(initial?.calendarId ?: calendars.firstOrNull()?.id) }
    var startDay by rememberSaveable { mutableLongStateOf(initialStart?.toLocalDate()?.toEpochDay() ?: date.toEpochDay()) }
    var endDay by rememberSaveable { mutableLongStateOf(initialEnd?.toLocalDate()?.toEpochDay() ?: date.toEpochDay()) }
    var startMinute by rememberSaveable { mutableIntStateOf(initialStart?.let { it.hour * 60 + it.minute } ?: 9 * 60) }
    var endMinute by rememberSaveable { mutableIntStateOf(initialEnd?.let { it.hour * 60 + it.minute } ?: 10 * 60) }
    var allDay by rememberSaveable { mutableStateOf(initial?.allDay ?: false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCalendars by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(calendars) { if (initial == null && calendars.none { it.id == calendarId }) calendarId = calendars.firstOrNull()?.id }
    val dirty = title != initial?.title.orEmpty() || location != initial?.location.orEmpty() || notes != initial?.notes.orEmpty() ||
        calendarId != (initial?.calendarId ?: calendars.firstOrNull()?.id) || allDay != (initial?.allDay ?: false) ||
        startDay != (initialStart?.toLocalDate()?.toEpochDay() ?: date.toEpochDay()) ||
        endDay != (initialEnd?.toLocalDate()?.toEpochDay() ?: date.toEpochDay()) ||
        startMinute != (initialStart?.let { it.hour * 60 + it.minute } ?: 9 * 60) ||
        endMinute != (initialEnd?.let { it.hour * 60 + it.minute } ?: 10 * 60)
    fun close() { if (!saving) { if (dirty) confirmDiscard = true else onDismiss() } }
    fun pickDate(epoch: Long, update: (Long) -> Unit) {
        val current = LocalDate.ofEpochDay(epoch)
        DatePickerDialog(context, { _, y, m, d -> update(LocalDate.of(y, m + 1, d).toEpochDay()) }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }
    fun pickTime(minutes: Int, update: (Int) -> Unit) {
        TimePickerDialog(context, { _, hour, minute -> update(hour * 60 + minute) }, minutes / 60, minutes % 60,
            android.text.format.DateFormat.is24HourFormat(context)).show()
    }
    fun save() {
        if (saving) return
        error = null
        val id = calendarId
        when {
            title.isBlank() -> error = "Give your event a title."
            id == null -> error = "Choose a writable calendar first."
            else -> {
                val startDate = LocalDate.ofEpochDay(startDay)
                val endDate = LocalDate.ofEpochDay(endDay)
                val times = runCatching {
                    val begin = if (allDay) startDate.atStartOfDay(ZoneOffset.UTC).toInstant() else
                        editorInstant(startDate, startMinute, zone, initial?.takeIf { !it.allDay }?.begin)
                    val end = if (allDay) endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() else
                        editorInstant(endDate, endMinute, zone, initial?.takeIf { !it.allDay }?.end)
                    begin to end
                }
                if (times.isFailure) { error = times.exceptionOrNull()?.message; return }
                val (begin, end) = times.getOrThrow()
                if (end <= begin) { error = "End must be after start."; return }
                saving = true
                scope.launch {
                    try { onSave(NewEvent(title, id, begin.toEpochMilli(), end.toEpochMilli(), allDay, zone.id, location, notes)) }
                    catch (e: CancellationException) { throw e }
                    catch (e: SecurityException) { error = "Calendar permission changed. Close this form and allow access in app settings." }
                    catch (e: android.content.OperationApplicationException) { error = "This event changed elsewhere. Close and reopen it before saving." }
                    catch (e: Exception) { error = "Couldn’t save this event. Check calendar access and try again." }
                    finally { saving = false }
                }
            }
        }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::close, enabled = !saving) { Text("Cancel") }
                    Text(if (initial == null) "New event" else "Edit event", Modifier.weight(1f).padding(start = 12.dp), fontSize = 20.sp)
                    TextButton(onClick = ::save, enabled = !saving && calendars.any { it.id == calendarId }) { Text(if (saving) "Saving…" else "Save") }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Event title") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                    if (calendars.isEmpty()) {
                        Text("No writable calendars found. Add an account or enable a calendar in your device’s calendar app, then return here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (initial != null) {
                        Text(calendars.find { it.id == calendarId }?.let { "${it.name} · ${it.account}" } ?: "Calendar unavailable",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    } else {
                        Box {
                            OutlinedButton(onClick = { showCalendars = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(calendars.find { it.id == calendarId }?.name ?: "Choose calendar")
                                    Text(calendars.find { it.id == calendarId }?.account.orEmpty(), fontSize = 11.sp)
                                }
                            }
                            DropdownMenu(showCalendars, onDismissRequest = { showCalendars = false }) {
                                calendars.forEach { calendar -> DropdownMenuItem(text = { Column { Text(calendar.name); Text(calendar.account, fontSize = 11.sp) } },
                                    onClick = { calendarId = calendar.id; showCalendars = false }) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("All day", Modifier.weight(1f))
                        Switch(allDay, { allDay = it }, enabled = !saving, modifier = Modifier.semantics { contentDescription = "All day" })
                    }
                    Text("Starts", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { pickDate(startDay) { val delta = it - startDay; startDay = it; endDay += delta } }, enabled = !saving, modifier = Modifier.weight(1f)) {
                            Text(LocalDate.ofEpochDay(startDay).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), fontSize = 13.sp)
                        }
                        if (!allDay) OutlinedButton(onClick = { pickTime(startMinute) { startMinute = it } }, enabled = !saving) { Text("%02d:%02d".format(startMinute / 60, startMinute % 60)) }
                    }
                    Text(if (allDay) "Ends (inclusive)" else "Ends", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { pickDate(endDay) { endDay = it } }, enabled = !saving, modifier = Modifier.weight(1f)) {
                            Text(LocalDate.ofEpochDay(endDay).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), fontSize = 13.sp)
                        }
                        if (!allDay) OutlinedButton(onClick = { pickTime(endMinute) { endMinute = it } }, enabled = !saving) { Text("%02d:%02d".format(endMinute / 60, endMinute % 60)) }
                    }
                    if (!allDay) Text(zone.id.replace('_', ' '), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 3, enabled = !saving, modifier = Modifier.fillMaxWidth())
                    Text("Repeats and reminder changes are available through “Open in calendar app” on the event details screen. Existing reminders are preserved when you edit.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Discard this event?") },
            text = { Text("Your changes haven’t been saved.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } })
    }
}
