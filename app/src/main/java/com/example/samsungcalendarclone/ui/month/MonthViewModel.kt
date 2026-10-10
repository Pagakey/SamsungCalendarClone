package com.example.samsungcalendarclone.ui.month

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.OperationApplicationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.example.samsungcalendarclone.data.calendar.CalendarChangeRejected
import androidx.lifecycle.viewModelScope
import com.example.samsungcalendarclone.data.calendar.AndroidCalendarRepository
import com.example.samsungcalendarclone.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class MonthUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val hasReadPermission: Boolean = false,
    val hasWritePermission: Boolean = false,
    val calendars: List<DeviceCalendar> = emptyList(),
    val events: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val hiddenCalendars: Set<Long> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Progress of the one calendar write the UI can have in flight. */
sealed interface SaveState {
    data object Idle : SaveState
    data object Running : SaveState
    data class Done(val message: String) : SaveState
    data class Failed(val message: String) : SaveState
}

class MonthViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = AndroidCalendarRepository(application)
    private val preferences = application.getSharedPreferences("calendar_preferences", 0)
    private val _state = MutableStateFlow(restoredState(MonthUiState(hiddenCalendars = preferences.getStringSet("hidden", emptySet())
        .orEmpty().mapNotNull { it.toLongOrNull() }.toSet())))
    val state = _state.asStateFlow()
    private val _save = MutableStateFlow<SaveState>(SaveState.Idle)
    val save = _save.asStateFlow()

    /** The visible month and selected date survive process death; "today" is always recomputed. */
    private fun restoredState(initial: MonthUiState): MonthUiState {
        val month = saved.get<String>(KEY_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: return initial
        val selected = saved.get<Long>(KEY_SELECTED)?.let { LocalDate.ofEpochDay(it) }?.takeIf { YearMonth.from(it) == month }
        return initial.copy(month = month, selectedDate = selected ?: month.atDay(1))
    }

    private fun remember(state: MonthUiState) {
        saved[KEY_MONTH] = state.month.toString()
        saved[KEY_SELECTED] = state.selectedDate.toEpochDay()
    }
    private var observer: Job? = null
    private var query: Job? = null
    private var clock: Job? = null

    /** Keeps "today" current while the screen is visible: at each local midnight, and on demand after clock changes. */
    fun startClock() {
        clock?.cancel()
        clock = viewModelScope.launch {
            while (true) {
                delay(untilNextDay(ZonedDateTime.now()).toMillis() + 500)
                onClockChanged()
            }
        }
    }

    fun stopClock() { clock?.cancel(); clock = null }

    /** A selection resting on the old "today" follows the date forward; any other selection stays put. */
    fun onClockChanged(now: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val before = _state.value
        if (before.today == now && before.zone == zone) return
        _state.update { it.copy(today = now, zone = zone) }
        if (before.selectedDate == before.today) select(now)
        // Timed events are grouped by local date, so a new zone regroups them.
        refresh()
    }

    fun refreshPermissions() {
        fun allowed(permission: String) = ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED
        val read = allowed(Manifest.permission.READ_CALENDAR)
        _state.update { it.copy(hasReadPermission = read, hasWritePermission = allowed(Manifest.permission.WRITE_CALENDAR),
            today = LocalDate.now(), zone = ZoneId.systemDefault()) }
        if (read) {
            if (observer == null) observer = viewModelScope.launch {
                try { repository.changes().collect { refresh() } }
                catch (e: SecurityException) { calendarAccessFailed() }
            }
            refresh()
        } else {
            observer?.cancel(); observer = null
            query?.cancel()
            _state.update { it.copy(calendars = emptyList(), events = emptyMap(), loading = false, error = null) }
        }
    }

    private fun calendarAccessFailed() {
        observer?.cancel(); observer = null
        _state.update { it.copy(events = emptyMap(), calendars = emptyList(), loading = false,
            error = "Calendar access is unavailable. Check app permissions in Settings, then retry.") }
    }

    fun showMonth(month: YearMonth) {
        if (month == _state.value.month) return
        _state.update { it.copy(month = month, selectedDate = month.atDay(minOf(it.selectedDate.dayOfMonth, month.lengthOfMonth())), events = emptyMap()) }
        remember(_state.value)
        refresh()
    }

    fun select(date: LocalDate) {
        showMonth(YearMonth.from(date))
        _state.update { it.copy(selectedDate = date) }
        remember(_state.value)
    }

    fun today() = select(LocalDate.now())
    fun toggleCalendar(id: Long) {
        _state.update { it.copy(hiddenCalendars = if (id in it.hiddenCalendars) it.hiddenCalendars - id else it.hiddenCalendars + id) }
        preferences.edit().putStringSet("hidden", _state.value.hiddenCalendars.map { it.toString() }.toSet()).apply()
        refresh()
    }

    fun refresh() {
        if (!_state.value.hasReadPermission) return
        query?.cancel()
        query = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val snapshot = _state.value
                val dates = monthDates(snapshot.month)
                val calendars = repository.calendars()
                val visible = calendars.map { it.id }.toSet() - snapshot.hiddenCalendars
                val events = repository.events(dates.first(), dates.last().plusDays(1), snapshot.zone)
                _state.update { it.copy(calendars = calendars, events = eventsByDate(events.filter { event -> event.calendarId in visible }, dates, snapshot.zone), loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: SecurityException) { calendarAccessFailed() }
            catch (e: Exception) { _state.update { it.copy(loading = false, error = "Couldn’t load calendars. Tap Retry to try again.") } }
        }
    }

    suspend fun details(event: CalendarEvent) = repository.details(event.id, event)

    // Writes run in the ViewModel so a rotation or theme change mid-save keeps its result.
    fun create(event: NewEvent) = write("Event saved", SAVE_FAILED) { repository.create(event) }
    fun update(original: EventDetails, event: NewEvent) = write("Event updated", SAVE_FAILED) { repository.update(original, event) }
    fun delete(original: EventDetails) = write("Event deleted", DELETE_FAILED) { repository.delete(original) }
    fun updateOccurrences(original: EventDetails, occurrence: CalendarEvent, event: NewEvent, scope: RecurrenceScope) =
        write("Event updated", SAVE_FAILED) { repository.updateOccurrences(original, occurrence, event, scope) }
    fun deleteOccurrences(original: EventDetails, occurrence: CalendarEvent, scope: RecurrenceScope) =
        write("Event deleted", DELETE_FAILED) { repository.deleteOccurrences(original, occurrence, scope) }

    /** Called once the UI has shown a result, or when the user dismisses a failed form. */
    fun clearSave() { if (_save.value != SaveState.Running) _save.value = SaveState.Idle }

    private fun write(done: String, failed: String, block: suspend () -> Unit) {
        if (_save.value == SaveState.Running) return
        _save.value = SaveState.Running
        viewModelScope.launch {
            _save.value = try {
                block()
                refresh()
                SaveState.Done(done)
            } catch (e: CancellationException) { throw e }
            catch (e: SecurityException) { SaveState.Failed("Calendar permission changed. Close this and allow access in app settings.") }
            catch (e: OperationApplicationException) { SaveState.Failed("This event changed elsewhere. Close it and open it again.") }
            catch (e: CalendarChangeRejected) { SaveState.Failed(e.message ?: failed) }
            catch (e: Exception) { SaveState.Failed(failed) }
        }
    }

    private companion object {
        const val KEY_MONTH = "month"
        const val KEY_SELECTED = "selected_date"
        const val SAVE_FAILED = "Couldn’t save this event. Check calendar access and try again."
        const val DELETE_FAILED = "Couldn’t delete this event. Check calendar access and try again."
    }
}
