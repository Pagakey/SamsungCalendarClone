package com.example.samsungcalendarclone.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.samsungcalendarclone.domain.model.CalendarEvent
import com.example.samsungcalendarclone.ui.month.SEARCH_LIMIT
import com.example.samsungcalendarclone.ui.month.SearchState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full-screen search. Results open the event; the query lives in the ViewModel and survives restoration. */
@Composable
fun SearchScreen(search: SearchState, hasPermission: Boolean, zone: ZoneId, onQuery: (String) -> Unit,
                 onOpenEvent: (CalendarEvent) -> Unit, onConnect: () -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    OutlinedTextField(search.query, onQuery, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                        label = { Text("Search events") })
                }
                val range = "${search.from.format(DateTimeFormatter.ofPattern("MMM yyyy"))} – ${search.until.format(DateTimeFormatter.ofPattern("MMM yyyy"))}"
                when {
                    !hasPermission -> Column(Modifier.padding(24.dp)) {
                        Text("Connect your calendars to search your events.", fontSize = 14.sp)
                        TextButton(onClick = onConnect) { Text("Connect") }
                    }
                    search.query.isBlank() -> Text("Search titles, places, notes and guests from $range.", Modifier.padding(24.dp),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    search.loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp))
                    search.error != null -> Text(search.error, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                    search.results.isEmpty() -> Text("No events match “${search.query.trim()}” from $range.", Modifier.padding(24.dp),
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (search.results.size >= SEARCH_LIMIT) item {
                            Text("Showing the first $SEARCH_LIMIT matches. Add words to narrow the search.", Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(search.results, key = { "${it.id}:${it.begin}" }) { event -> EventRow(event, zone, onOpenEvent, showDate = true) }
                    }
                }
            }
        }
    }
}
