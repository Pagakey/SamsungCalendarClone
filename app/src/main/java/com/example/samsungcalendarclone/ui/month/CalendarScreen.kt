package com.example.samsungcalendarclone.ui.month

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.samsungcalendarclone.domain.model.*
import com.example.samsungcalendarclone.ui.event.EditRequest
import com.example.samsungcalendarclone.ui.event.EditRequestSaver
import com.example.samsungcalendarclone.ui.event.EventEditor
import com.example.samsungcalendarclone.ui.event.OccurrenceSaver
import com.example.samsungcalendarclone.ui.views.AgendaList
import com.example.samsungcalendarclone.ui.views.SearchScreen
import com.example.samsungcalendarclone.ui.views.TimeGrid
import com.example.samsungcalendarclone.ui.event.EventDetailsSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(model: MonthViewModel) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val save by model.save.collectAsState()
    // The open event and an in-progress edit survive process death; details are reloaded from the provider.
    var openedEvent by rememberSaveable(stateSaver = OccurrenceSaver) { mutableStateOf<CalendarEvent?>(null) }
    var details by remember { mutableStateOf<EventDetails?>(null) }
    var editing by rememberSaveable(stateSaver = EditRequestSaver) { mutableStateOf<EditRequest?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var detailsError by remember { mutableStateOf<String?>(null) }
    var detailsRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(openedEvent, state.loading, state.hasReadPermission, detailsRetry) {
        val event = openedEvent ?: return@LaunchedEffect
        if (state.loading) return@LaunchedEffect
        detailsLoading = true
        detailsError = null
        try {
            details = model.details(event)
            if (details == null) detailsError = "This event was removed or is no longer available."
        } catch (e: CancellationException) { throw e }
        catch (e: SecurityException) { details = null; detailsError = "Calendar access is off. Close this screen and allow access in Settings." }
        catch (e: Exception) { detailsError = "Couldn’t load this event. Please try again." }
        finally { detailsLoading = false }
    }
    var calendarsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    val search by model.search.collectAsState()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(save) {
        val done = save as? SaveState.Done ?: return@LaunchedEffect
        editorOpen = false; editing = null; openedEvent = null; details = null
        model.clearSave()
        snackbar.showSnackbar(done.message)
    }
    val saving = save == SaveState.Running
    val saveError = (save as? SaveState.Failed)?.message
    var requestedRead by rememberSaveable { mutableStateOf(false) }
    var pendingAdd by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.refreshPermissions()
        if (pendingAdd && model.state.value.hasWritePermission && model.state.value.hasReadPermission) editorOpen = true
        pendingAdd = false
    }
    fun requestRead() { requestedRead = true; permission.launch(arrayOf(Manifest.permission.READ_CALENDAR)) }
    fun add() {
        if (state.hasReadPermission && state.hasWritePermission) editorOpen = true
        else {
            pendingAdd = true; requestedRead = true
            permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }
    fun settings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
    fun openExternalEvent(event: CalendarEvent) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Install or enable a calendar app to open event details.") }
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = ::add, shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(60.dp).semantics { contentDescription = "Add event" }) {
                Text("+", fontSize = 34.sp, fontWeight = FontWeight.Light)
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val compact = maxHeight < 600.dp
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(maxOf(maxHeight, 620.dp))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { calendarsOpen = true }) { Text("Calendars", color = MaterialTheme.colorScheme.onSurface) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { searchOpen = true }) { Text("Search") }
                    Box {
                        TextButton(onClick = { viewMenuOpen = true }, modifier = Modifier.semantics { contentDescription = "Change view, ${state.view.label} shown" }) {
                            Text("${state.view.label} ▾")
                        }
                        DropdownMenu(viewMenuOpen, onDismissRequest = { viewMenuOpen = false }) {
                            CalendarView.entries.forEach { view ->
                                DropdownMenuItem(text = { Text(view.label) }, onClick = { viewMenuOpen = false; model.showView(view) })
                            }
                        }
                    }
                    TextButton(onClick = model::today) { Text("Today") }
                }
                ViewHeader(state, compact, onStep = model::step)
                if (!state.hasReadPermission) {
                    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("See your plans here", Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { if (requestedRead) settings() else requestRead() }) { Text(if (requestedRead) "Settings" else "Connect") }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (state.error != null && state.view != CalendarView.MONTH) Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error!!, Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = model::refresh) { Text("Retry") }
                }
                val open: (CalendarEvent) -> Unit = { event -> details = null; openedEvent = event }
                when (state.view) {
                    CalendarView.MONTH -> MonthContent(state, compact, model, open, Modifier.weight(1f))
                    CalendarView.WEEK, CalendarView.DAY -> {
                        Box(Modifier.fillMaxWidth().height(2.dp)) {
                            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .45f))
                        }
                        TimeGrid(if (state.view == CalendarView.WEEK) weekDates(state.selectedDate) else listOf(state.selectedDate),
                            state.events, state.today, state.selectedDate, state.zone, onOpenEvent = open, onStep = model::step,
                            onOpenDay = { day: LocalDate -> model.select(day); model.showView(CalendarView.DAY) }.takeIf { state.view == CalendarView.WEEK },
                            modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                    CalendarView.AGENDA -> {
                        Box(Modifier.fillMaxWidth().height(2.dp)) {
                            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .45f))
                        }
                        val dates = state.loadedDates
                        AgendaList(dates, state.events, state.today, state.zone,
                            canShowMore = state.hasReadPermission && state.agendaDays < AGENDA_MAX_DAYS,
                            emptyText = if (!state.hasReadPermission) "Connect your calendars to see upcoming events."
                                else if (state.loading) "Loading…"
                                else "No events from ${dates.first().format(DateTimeFormatter.ofPattern("d MMM"))} to ${dates.last().format(DateTimeFormatter.ofPattern("d MMM yyyy"))}.",
                            onOpenEvent = open, onShowMore = model::showMoreAgenda, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
    if (calendarsOpen) ModalBottomSheet(onDismissRequest = { calendarsOpen = false }) {
        Text("Your calendars", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 26.sp, fontWeight = FontWeight.Light)
        Text("Synced by your device and accounts", Modifier.padding(horizontal = 24.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!state.hasReadPermission) {
            Text("Calendar access is off. You can still browse dates.", Modifier.padding(24.dp))
            TextButton(onClick = { if (requestedRead) settings() else requestRead() }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(if (requestedRead) "Open app settings" else "Allow calendar access") }
        } else if (state.calendars.isEmpty()) {
            Text("No visible calendars found. Enable a calendar in your device’s calendar app, or add an account in Android Settings.", Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(state.calendars, key = { it.id }) { calendar ->
                Row(Modifier.fillMaxWidth().clickable { model.toggleCalendar(calendar.id) }.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(eventColor(calendar.color), CircleShape))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(calendar.name, fontSize = 16.sp)
                        Text(calendar.account + if (!calendar.writable) " · Read only" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(checked = calendar.id !in state.hiddenCalendars, onCheckedChange = { model.toggleCalendar(calendar.id) })
                }
            }
        }
        if (!state.hasWritePermission && state.hasReadPermission) TextButton(onClick = ::settings, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Manage calendar permissions") }
        Spacer(Modifier.height(28.dp))
    }
    openedEvent?.let { occurrence ->
        EventDetailsSheet(occurrence, details, detailsLoading, detailsError, state.hasWritePermission,
            onDismiss = { openedEvent = null; details = null; model.clearSave() }, onRetry = { detailsRetry++ },
            onAllowEditing = { permission.launch(arrayOf(Manifest.permission.WRITE_CALENDAR)) },
            onEdit = { original, recurrenceScope -> editing = EditRequest(original, occurrence, recurrenceScope) },
            onDelete = { original, recurrenceScope ->
                if (recurrenceScope == null) model.delete(original) else model.deleteOccurrences(original, occurrence, recurrenceScope)
            }, onOpenExternal = { openExternalEvent(occurrence) },
            deleting = saving && editing == null && !editorOpen,
            deleteError = saveError.takeIf { editing == null && !editorOpen })
    }
    editing?.let { (original, occurrence, recurrenceScope) ->
        key(original.id, occurrence.begin, recurrenceScope) {
            // A series opens at the tapped occurrence; its stored DTSTART is the first occurrence.
            val initial = if (recurrenceScope == null || original.isException) original.event
                else original.event.copy(begin = occurrence.begin, end = occurrence.end)
            EventEditor(state.selectedDate, listOfNotNull(original.calendar),
                onDismiss = { editing = null; model.clearSave() }, initial = initial,
                allDayLocked = recurrenceScope != null && recurrenceScope != RecurrenceScope.THIS,
                saving = saving, saveError = saveError,
                onSave = { event ->
                    if (recurrenceScope == null) model.update(original, event)
                    else model.updateOccurrences(original, occurrence, event, recurrenceScope)
                })
        }
    }
    if (searchOpen) SearchScreen(search, state.hasReadPermission, state.zone, onQuery = model::search,
        onOpenEvent = { event ->
            // Show the result's day behind the sheet, so closing it leaves the user where the event is.
            model.select(event.firstDate(state.zone))
            searchOpen = false
            details = null; openedEvent = event
        },
        onConnect = { searchOpen = false; if (requestedRead) settings() else requestRead() },
        onDismiss = { searchOpen = false })
    if (editorOpen) EventEditor(state.selectedDate, state.calendars.filter { it.writable },
        onDismiss = { editorOpen = false; model.clearSave() }, onSave = model::create, saving = saving, saveError = saveError)
}

/** Title row: year and the visible month, week or day, with page arrows. */
@Composable
private fun ViewHeader(state: MonthUiState, compact: Boolean, onStep: (Boolean) -> Unit) {
    val locale = Locale.getDefault()
    val (overline, title, unit) = when (state.view) {
        CalendarView.MONTH -> Triple(state.month.year.toString(), state.month.month.getDisplayName(TextStyle.FULL, locale), "month")
        CalendarView.WEEK -> weekDates(state.selectedDate).let { days ->
            Triple(days.first().year.toString(), weekLabel(days, locale), "week")
        }
        CalendarView.DAY -> Triple(state.selectedDate.year.toString(),
            state.selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)), "day")
        CalendarView.AGENDA -> Triple("From ${state.selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))}", "Agenda", "week")
    }
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, bottom = if (compact) 4.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(overline, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, fontSize = if (compact || state.view != CalendarView.MONTH) 32.sp else 40.sp, fontWeight = FontWeight.Light,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onStep(false) }, modifier = Modifier.semantics { contentDescription = "Previous $unit" }) { Text("‹", fontSize = 32.sp) }
        IconButton(onClick = { onStep(true) }, modifier = Modifier.semantics { contentDescription = "Next $unit" }) { Text("›", fontSize = 32.sp) }
    }
}

/** Swipeable six-week grid with the selected day's events underneath. */
@Composable
private fun MonthContent(state: MonthUiState, compact: Boolean, model: MonthViewModel, onOpenEvent: (CalendarEvent) -> Unit, modifier: Modifier) {
    val anchor = remember { YearMonth.now() }
    fun page(month: YearMonth) = (1200 + ChronoUnit.MONTHS.between(anchor, month).toInt()).coerceIn(0, 2400)
    // Created when the month view appears, so it opens on the month the other views left selected.
    val pager = rememberPagerState(initialPage = page(state.month), pageCount = { 2401 })
    // Arrows, Today and taps move the state first and the pager follows. While it animates, an
    // intermediate settled page (e.g. after rapid taps) must not be reported back as a swipe.
    var target by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.month) {
        val wanted = page(state.month)
        if (pager.currentPage == wanted) return@LaunchedEffect
        target = wanted
        try { pager.animateScrollToPage(wanted) } finally { if (target == wanted) target = null }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { settled ->
            if (target == null || target == settled) model.showMonth(anchor.plusMonths((settled - 1200).toLong()))
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            (1..7).forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                        fontSize = 11.sp, color = dayColor(day))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .45f))
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth().testTag("month-pager"), beyondViewportPageCount = 0) { page ->
            val month = anchor.plusMonths((page - 1200).toLong())
            MonthGrid(month, state, Modifier.fillMaxSize()) { date -> model.select(date) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().height(if (compact) 115.dp else 194.dp).padding(top = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (state.selectedDate == state.today) Text("  ·  Today", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            when {
                state.error != null -> Row(Modifier.padding(horizontal = 24.dp).padding(end = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error, Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = model::refresh) { Text("Retry") }
                }
                state.events[state.selectedDate].isNullOrEmpty() -> Column(Modifier.padding(start = 24.dp, top = 18.dp, end = 94.dp)) {
                    Text(if (state.hasReadPermission) "No events" else "Your day, at a glance", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!compact) Text(if (state.hasReadPermission) "Tap + to make a plan." else "Connect your calendars to see events.",
                        Modifier.padding(top = 5.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.padding(top = 10.dp, end = 88.dp)) {
                    items(state.events[state.selectedDate].orEmpty(), key = { "${it.id}:${it.begin}" }) { event ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenEvent(event) }.padding(horizontal = 24.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(3.dp).height(32.dp).clip(CircleShape).background(eventColor(event.color)))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(event.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (event.allDay) "All day" else Instant.ofEpochMilli(event.begin).atZone(state.zone).format(DateTimeFormatter.ofPattern("HH:mm")) +
                                    if (event.location.isBlank()) "" else " · ${event.location}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dayColor(day: Int): Color = when (day) {
    7 -> Color(0xFFCC6464)
    6 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun eventColor(raw: Int): Color = if (raw == 0) Color(0xFF5E8BC8) else Color(raw).copy(alpha = 1f)

@Composable
private fun MonthGrid(month: YearMonth, state: MonthUiState, modifier: Modifier, onSelect: (LocalDate) -> Unit) {
    val dates = remember(month) { monthDates(month) }
    Column(modifier.padding(horizontal = 12.dp)) {
        dates.chunked(7).forEach { week ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                week.forEach { date ->
                    val events = state.events[date].orEmpty()
                    val selected = date == state.selectedDate
                    val today = date == state.today
                    val currentMonth = YearMonth.from(date) == month
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(date) }.semantics(mergeDescendants = true) {
                            contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")) +
                                (if (today) ", today" else "") + ", ${events.size} events"
                            this.selected = selected
                        }) {
                        val pillCount = if (maxHeight >= 92.dp) 2 else if (maxHeight >= 66.dp) 1 else 0
                        Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(30.dp)
                                .then(if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .padding(3.dp)
                                .background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                Text(date.dayOfMonth.toString(), fontSize = 13.sp,
                                    color = if (today) MaterialTheme.colorScheme.onPrimary else
                                        (if (date.dayOfWeek.value >= 6) dayColor(date.dayOfWeek.value) else MaterialTheme.colorScheme.onSurface).copy(alpha = if (currentMonth) 1f else .35f),
                                    fontWeight = if (today || selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                            Spacer(Modifier.height(3.dp))
                            events.take(pillCount).forEach { event ->
                                val color = eventColor(event.color)
                                Box(Modifier.fillMaxWidth().padding(bottom = 2.dp).clip(RoundedCornerShape(3.dp))
                                    .background(color.copy(alpha = .19f)).padding(horizontal = 3.dp, vertical = 1.dp)) {
                                    Text(event.title, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            if (events.size > pillCount) {
                                if (pillCount == 0) Box(Modifier.size(4.dp).background(eventColor(events.first().color), CircleShape))
                                else Text("+${events.size - pillCount}", fontSize = 9.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
