# Samsung Calendar–style Android calendar

A native Kotlin and Jetpack Compose calendar exploring the restrained layout and
interaction patterns of Samsung Calendar, with an original implementation and assets.
This is an independent learning/portfolio project, not an official Samsung product.

**Status:** working month/provider integration and native non-repeating event editing. Built and launched on a Xiaomi 14
running Android 16. The larger feature roadmap is still in progress.

## What works

- Monday-first month view with six stable rows, month swiping, navigation arrows,
  adjacent-month dates, Today and separate selected-date/today markers.
- Light/dark appearance, event pills, overflow counts and a selected-day event list.
- Device calendar integration, recurring occurrences through Android's Calendar
  Provider, calendar colors and persistent visibility filters.
- Graceful browsing without calendar permission, and background data refresh.
- Basic native event creation: title, calendar, start/end, all-day, location and notes.
- Native event details with dates, calendar/account, notes, location and stored reminders.
- Native editing and confirmed deletion of writable, non-repeating events, with stale-change protection.
- Reminder controls in the editor (preset times, per-calendar limits), saved atomically with the event.
- Repeating events: edit or delete this event, this and following events, or all events.
- The visible month, open event and unsaved edits survive the app being closed in the background;
  saves keep running through rotation and are safe to retry.
- Explicit device-calendar handoff for repeat-rule changes and series this app can't change safely.

Editing the repeat rule itself, additional views, search, notifications and widgets
remain future work. Reminder rows are stored in the Calendar Provider, so the
device calendar app delivers the alerts; this app does not yet schedule its own notifications.

## Build and run

Open the project in Android Studio and let Gradle resolve the configured toolchain.
Use Android Studio SDK Manager to supply the SDK selected by the existing build,
and let Studio create your machine's `local.properties`.

| Setting | Current value |
| --- | --- |
| Package/application ID | `com.example.samsungcalendarclone` |
| Minimum Android API | 28 |
| Compile / target API | 37 / 37 |
| Android Gradle Plugin | 9.4.1 |
| Gradle wrapper | 9.6.0 |
| Gradle daemon Java toolchain | 25 |
| Kotlin Compose plugin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| Verified physical device | Xiaomi 14, Android 16 / API 36 |

These are the original project's settings, preserved through the first implementation.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Install the generated debug APK on an authorized connected device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.samsungcalendarclone/.MainActivity
```

Tap **Connect** to display your device calendars. Tap **+** to grant write access
and create an event. A writable device calendar must already exist. Account sync
is handled by Android and the installed account adapters.

## Tests

```sh
./gradlew connectedDebugAndroidTest
```

Device tests install/clean up test packages and show light/dark test screens.
They may remove the app installation, so reinstall the debug APK afterward and
grant calendar permission again. Provider tests create and delete their own temporary
local calendars; they do not create events in personal accounts.

For a test run that preserves the installation, see the manual ADB workflow in
[the native event checkpoint](docs/NATIVE_EVENT_CHECKPOINT.md).

Last verified on 2026-10-10: **27 JVM tests and 39 device tests passed**;
`assembleDebug` and `lintDebug` passed with zero lint errors and 19 warnings.

Reports are generated under `app/build/reports/`; machine outputs are ignored by Git.

## Structure

Source root: `app/src/main/java/com/example/samsungcalendarclone/`.

| Area | Responsibility |
| --- | --- |
| `MainActivity.kt` | Compose entry point and resume-time refresh |
| `domain/model/` | Six-week date grid, calendar/event models and date mapping |
| `data/calendar/` | Provider queries/observation, creation and guarded update/delete |
| `ui/month/` | Screen state, permissions, calendar filters and month/day-summary UI |
| `ui/event/` | Native details, creation/editing form and delete confirmation |
| `ui/theme/` | Light/dark visual theme |

Queries run on IO and UI state is exposed through StateFlow. No custom cloud sync,
root access, hidden APIs or proprietary Samsung resources are used.

## Development records

- [TASKS.md](TASKS.md): authoritative checklist; unchecked items are unfinished.
- [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md): changes, validation and device side effects.
- [IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md): historical first-pass inventory.
- [Native event checkpoint](docs/NATIVE_EVENT_CHECKPOINT.md): current event flow, test fixture design and limitations.
- [CODEX_HANDOFF.md](CODEX_HANDOFF.md): original product brief and milestone requirements.
- [AGENTS.md](AGENTS.md): shared instructions for continuing coding work.
- [CLAUDE.md](CLAUDE.md): Claude Code entry point into the shared handoff.

Public screenshots/demo data, final branding, a license, CI and GitHub publication
are tracked as pending portfolio tasks. No real account data belongs in those assets.
