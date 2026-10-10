package com.example.samsungcalendarclone.ui.month

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsungcalendarclone.data.calendar.AndroidCalendarRepository
import com.example.samsungcalendarclone.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

class MonthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AndroidCalendarRepository(application)
    private val preferences = application.getSharedPreferences("calendar_preferences", 0)
    private val _state = MutableStateFlow(MonthUiState(hiddenCalendars = preferences.getStringSet("hidden", emptySet())
        .orEmpty().mapNotNull { it.toLongOrNull() }.toSet()))
    val state = _state.asStateFlow()
    private var observer: Job? = null
    private var query: Job? = null

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
        refresh()
    }

    fun select(date: LocalDate) {
        showMonth(YearMonth.from(date))
        _state.update { it.copy(selectedDate = date) }
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
    suspend fun update(original: EventDetails, event: NewEvent) { repository.update(original, event); refresh() }
    suspend fun delete(original: EventDetails) { repository.delete(original); refresh() }
    suspend fun updateOccurrences(original: EventDetails, occurrence: CalendarEvent, event: NewEvent, scope: RecurrenceScope) {
        repository.updateOccurrences(original, occurrence, event, scope); refresh()
    }
    suspend fun deleteOccurrences(original: EventDetails, occurrence: CalendarEvent, scope: RecurrenceScope) {
        repository.deleteOccurrences(original, occurrence, scope); refresh()
    }

    suspend fun create(event: NewEvent) { repository.create(event); refresh() }
}
