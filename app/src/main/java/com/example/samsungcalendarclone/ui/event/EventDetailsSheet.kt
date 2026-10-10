package com.example.samsungcalendarclone.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.samsungcalendarclone.domain.model.CalendarEvent
import com.example.samsungcalendarclone.domain.model.EventDetails
import com.example.samsungcalendarclone.domain.model.RecurrenceScope
import com.example.samsungcalendarclone.domain.model.reminderLabel
import com.example.samsungcalendarclone.domain.model.reminderMethodLabel
import com.example.samsungcalendarclone.ui.month.eventColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsSheet(
    occurrence: CalendarEvent,
    details: EventDetails?,
    loading: Boolean,
    loadError: String?,
    hasWritePermission: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAllowEditing: () -> Unit,
    onEdit: (EventDetails, RecurrenceScope?) -> Unit,
    onDelete: suspend (EventDetails, RecurrenceScope?) -> Unit,
    onOpenExternal: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var chooseEditScope by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(RecurrenceScope.THIS) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val coroutines = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { if (!deleting) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row {
                Box(Modifier.width(4.dp).height(36.dp).background(eventColor(occurrence.color)))
                Text(details?.event?.title?.ifBlank { "Untitled event" } ?: occurrence.title,
                    Modifier.padding(start = 14.dp).weight(1f), fontSize = 27.sp, fontWeight = FontWeight.Light)
                TextButton(onClick = onDismiss, enabled = !deleting) { Text("Close") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (loadError != null) {
                Text(loadError, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            if (details != null) {
                val event = details.event
                val zone = if (event.allDay) ZoneOffset.UTC else runCatching { ZoneId.of(event.zone) }.getOrDefault(ZoneId.systemDefault())
                val begin = if (details.repeating) occurrence.begin else event.begin
                val end = if (details.repeating) occurrence.end else event.end
                val format = DateTimeFormatter.ofPattern(if (event.allDay) "EEE, d MMM yyyy" else "EEE, d MMM yyyy · HH:mm")
                val startText = Instant.ofEpochMilli(begin).atZone(zone).format(format)
                val endText = Instant.ofEpochMilli(if (event.allDay) maxOf(begin, end - 1) else end).atZone(zone).format(format)
                DetailValue(if (event.allDay) "All day" else "When", if (startText == endText) startText else "$startText\n$endText")
                if (!event.allDay) DetailValue("Time zone", event.zone.replace('_', ' '))
                DetailValue("Calendar", details.calendar?.let { "${it.name}\n${it.account}" } ?: "Calendar unavailable")
                if (event.location.isNotBlank()) DetailValue("Location", event.location)
                if (event.notes.isNotBlank()) DetailValue("Notes", event.notes)
                if (details.reminders.isNotEmpty()) DetailValue("Reminders", details.reminders.joinToString("\n") {
                    reminderLabel(it.minutes, event.allDay) + (reminderMethodLabel(it.method)?.let { method -> " · $method" } ?: "")
                })
                HorizontalDivider()
                val scopes = details.recurrenceScopes
                when {
                    details.calendar?.writable != true -> Text("This calendar is read-only or no longer available.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    !details.hasSupportedZone -> Text("This event uses an unrecognized time zone. Open it in your calendar app to edit it.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    details.repeating && scopes.isEmpty() -> Text("This is part of a repeating event. Use your calendar app to choose which occurrences to change.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    !hasWritePermission -> TextButton(onClick = onAllowEditing) { Text("Allow editing") }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = {
                            scope = RecurrenceScope.THIS
                            if (scopes.size > 1) chooseEditScope = true else onEdit(details, scopes.firstOrNull())
                        }, enabled = !loading && !deleting && loadError == null) { Text("Edit") }
                        TextButton(onClick = { scope = RecurrenceScope.THIS; confirmDelete = true }, enabled = !loading && !deleting && loadError == null) {
                            Text(if (deleting) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
            TextButton(onClick = onOpenExternal, enabled = !deleting) { Text("Open in calendar app") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete && details != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(if (details.recurrenceScopes.size > 1) "Delete repeating event?" else "Delete this event?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (details.recurrenceScopes.size > 1) ScopeChoices(details.recurrenceScopes, scope) { scope = it }
                Text("“${details.event.title.ifBlank { "Untitled event" }}” will be removed from ${details.calendar?.name ?: "its calendar"}. This change may sync to your other devices.")
            }
        },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            deleting = true
            deleteError = null
            coroutines.launch {
                try { onDelete(details, if (details.repeating) scope else null) }
                catch (e: CancellationException) { throw e }
                catch (e: android.content.OperationApplicationException) { deleteError = "This event changed elsewhere. Close and reopen it before deleting." }
                catch (e: Exception) { deleteError = "Couldn’t delete this event. Check calendar access and try again." }
                finally { deleting = false }
            }
        }) { Text("Delete event", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep event") } },
    )
    if (chooseEditScope && details != null) AlertDialog(
        onDismissRequest = { chooseEditScope = false }, title = { Text("Edit repeating event") },
        text = { ScopeChoices(details.recurrenceScopes, scope) { scope = it } },
        confirmButton = { TextButton(onClick = { chooseEditScope = false; onEdit(details, scope) }) { Text("Edit") } },
        dismissButton = { TextButton(onClick = { chooseEditScope = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ScopeChoices(scopes: List<RecurrenceScope>, selected: RecurrenceScope, onSelect: (RecurrenceScope) -> Unit) {
    Column(Modifier.selectableGroup()) {
        scopes.forEach { option ->
            Row(Modifier.fillMaxWidth().selectable(option == selected, role = Role.RadioButton) { onSelect(option) }
                .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(option == selected, onClick = null)
                Text(option.label, Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, fontSize = 15.sp) }
    }
}
