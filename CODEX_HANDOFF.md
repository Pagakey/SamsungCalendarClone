# CODEX HANDOFF — Samsung Calendar–Style Android App

## Mission

You are working **inside an already-created Android Studio project** that successfully builds and runs on a **Xiaomi 14** running **Android 16 / HyperOS 3**.

Your job is to inspect the existing project, preserve its working build/package setup, and implement a polished native Android calendar app inspired by the Samsung Calendar experience.

Do not ask the user to copy/paste code manually. Make the code changes directly in the project, run builds, fix errors, and leave the project in a runnable state.

---

## Device / Environment

- Phone: Xiaomi 14
- Android: Android 16
- HyperOS: 3.x
- Chip: Snapdragon 8 Gen 3
- RAM: 12 GB
- Storage: 512 GB
- Development machine: Apple Silicon MacBook Pro (M5)
- Android Studio: installed and working
- Physical device debugging: working over USB
- The stock starter app has already launched successfully on the Xiaomi

---

## Core Product Goal

Build a **Samsung Calendar–style clone** for Android, with very close interaction patterns, layout structure, density, and behavior, while using **original implementation and original assets**.

Do **not** copy proprietary Samsung icons, images, source code, or bundled resources.

The experience should feel recognizably similar to Samsung Calendar, but be implemented from scratch with Android/Jetpack Compose.

---

## Technical Requirements

Use:

- Kotlin
- Jetpack Compose
- Material 3 where useful, but do not let stock Material styling dominate the visual design
- Android Calendar Provider / `CalendarContract`
- Modern Android APIs appropriate for Android 16
- Existing Gradle setup unless a change is clearly needed
- Existing package name unless there is a strong reason to change it
- Existing project structure where reasonable

Target:

- Target / Compile SDK: Android 16 / API 36 if already available in project
- Minimum SDK: ideally API 28 unless current project differs
- Physical-device testing on Xiaomi 14

Avoid:

- XML layouts unless truly justified
- Root
- Bootloader unlocking
- Security bypasses
- Hidden APIs
- Fragile reflection hacks
- Reimplementing Google Calendar sync from scratch
- Proprietary Samsung assets/code

---

## FIRST TASK: Inspect Before Editing

Before making major changes:

1. Inspect:
   - `settings.gradle(.kts)`
   - root `build.gradle(.kts)`
   - `app/build.gradle(.kts)`
   - `AndroidManifest.xml`
   - current package name
   - current `MainActivity`
   - theme files
   - Compose/compiler versions
   - min/target/compile SDK
2. Confirm the project builds as-is.
3. Preserve the working configuration.
4. Then begin implementation.

Do not regenerate the whole project unless the existing project is genuinely broken.

---

# Milestone 1 — Polished Month View

Implement a high-quality month screen inspired by Samsung Calendar.

## Layout

The screen should include:

- Large month title
- Year indicator
- Weekday header
- 6-row month grid
- Monday-first week layout
- Previous/next month navigation
- Horizontal swipe between months
- Today action
- Floating add button
- Selected-date state
- Distinct today state
- Adjacent-month dates
- Event indicators / event pills inside date cells
- Smooth transitions
- Proper edge-to-edge behavior

## Visual Direction

Aim for:

- Spacious, restrained Samsung-like layout
- Clean typography
- Thin visual hierarchy
- Rounded event bars
- Compact event labels
- Neutral surfaces
- Good dark mode
- Minimal “stock Material app” appearance

Do not overdecorate.

## Behavior

- Swipe left/right between months
- Tap a date to select it
- Tapping adjacent-month dates should move to that month
- “Today” returns to current month and selects today
- Current date should remain visually identifiable even when another date is selected
- Handle leap years correctly
- Handle 5-week and 6-week calendar arrangements correctly
- Avoid layout jumps

---

# Milestone 2 — Calendar Provider Integration

After the Month View is stable, connect it to Android’s calendar database.

Use:

- `CalendarContract.Calendars`
- `CalendarContract.Events`
- `CalendarContract.Instances`
- `CalendarContract.Reminders`

Request only appropriate permissions.

Manifest/runtime permissions:

- `READ_CALENDAR`
- `WRITE_CALENDAR`

The app should:

- List device calendars
- Read events for the visible date range
- Display real events in the month grid
- Support multiple calendars
- Respect calendar colors where practical
- Refresh when calendar data changes
- Handle permission denied gracefully
- Never crash when no calendars exist

Prefer `Instances` for expanded recurring-event occurrences when displaying date ranges.

Do not implement cloud sync yourself. Let the device/account sync adapters handle synchronization.

---

# Milestone 3 — Event Details / Editor

Implement event creation and editing.

Fields:

- Title
- Calendar/account selector
- Start date/time
- End date/time
- All-day
- Time zone
- Location
- Notes
- Color where supported
- Reminder(s)
- Recurrence
- Availability where supported

Support:

- Create
- Edit
- Delete
- Recurring event handling
- Confirmation where destructive
- Proper validation

The floating add button should open this editor.

---

# Milestone 4 — Additional Views

Add:

- Day view
- Week view
- Agenda / schedule view
- Search

Keep navigation and interaction patterns Samsung-like.

---

# Milestone 5 — Reminders / Notifications

Implement reliable reminders.

Use modern Android notification APIs.

Handle:

- Notification permission
- Alarm scheduling where required
- Device reboot / rescheduling
- Time zone changes
- Event changes

For exact reminders, use only legitimate Android APIs and only where appropriate for calendar functionality.

Do not bypass HyperOS restrictions.

---

# Milestone 6 — Xiaomi / HyperOS Reliability

Add a small setup/help screen for Xiaomi users explaining how to improve reminder reliability.

Possible guidance:

- Notification permission
- Battery optimization / “No restrictions”
- Background autostart if needed
- Exact alarm permission if required

Never silently change system settings.

Never use hacks to evade HyperOS battery/security controls.

---

# Milestone 7 — Widgets

After core functionality is solid, add:

- Month widget
- Agenda widget
- Light/dark support
- Transparency options if practical
- Resize support
- Calendar filtering if practical

---

# Suggested Architecture

Use a clean but not overengineered architecture.

Suggested packages:

```text
ui/
  month/
  day/
  week/
  agenda/
  event/
  settings/

data/
  calendar/
  repository/

domain/
  model/
  usecase/

util/
```

Possible classes:

```text
CalendarRepository
AndroidCalendarRepository
CalendarPermissionManager
MonthViewModel
EventEditorViewModel
CalendarUiModel
EventUiModel
```

Prefer coroutines / Flow where useful.

Avoid introducing a heavyweight architecture framework unless there is a clear benefit.

---

# UI State

Prefer immutable UI state objects.

Example concepts:

```text
MonthUiState
SelectedDate
VisibleMonth
CalendarFilters
PermissionState
LoadingState
```

Keep Compose state predictable and testable.

---

# Performance Requirements

The app should feel smooth on the Xiaomi 14.

Avoid:

- Querying Calendar Provider on every recomposition
- Blocking the main thread
- Excessive object creation in day cells
- Loading huge date ranges unnecessarily
- Full-screen recompositions for tiny state changes

Cache/derive month-grid calculations sensibly.

---

# Testing

At minimum:

1. Build the project with Gradle.
2. Fix all compile errors.
3. Run available unit tests.
4. Add tests for date-grid calculations.
5. Test:
   - leap year February
   - month starting Sunday
   - month starting Monday
   - December → January
   - January → December
6. Verify runtime permissions.
7. Verify app launches with calendar permission denied.
8. Verify no events case.
9. Verify dark mode.

If ADB/device access is available from the Codex environment, install and launch on the connected Xiaomi 14.

---

# Definition of Done for First Working Pass

Do not stop after scaffolding.

The first meaningful deliverable should:

- Build successfully
- Launch successfully
- Show a polished month calendar
- Support month swiping
- Support date selection
- Show current day
- Show adjacent-month dates
- Have a working add button entry point
- Read real Android calendar events after permission is granted
- Fail gracefully without permission
- Contain no proprietary Samsung assets/code
- Have no obvious placeholder TODOs in the main user flow

---

# Working Style

You have permission to:

- Inspect files
- Edit files
- Create files
- Refactor
- Add dependencies where justified
- Run Gradle builds/tests
- Use ADB if available
- Fix issues iteratively

Do not ask the user to manually paste code into Android Studio.

When uncertain, inspect the project and choose a reasonable implementation.

Do not stop to ask minor implementation questions. Use good defaults and keep moving.

Only ask the user when a decision materially affects product behavior or requires their personal preference.

---

# Final Output to User

When you reach a stable checkpoint, report:

1. What you changed
2. Which files were created/modified
3. Whether the build passes
4. Whether device launch was tested
5. What currently works
6. What remains for the next milestone
7. Any action the user must take on the Xiaomi

The next coding priority is:

**Inspect project → make Month View polished → connect real Calendar Provider events → build/test on Xiaomi 14.**
