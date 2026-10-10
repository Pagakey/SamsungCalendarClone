# First working pass

Historical first-pass checkpoint: 2026-10-10. Native details/editing extensions
are recorded in [docs/NATIVE_EVENT_CHECKPOINT.md](docs/NATIVE_EVENT_CHECKPOINT.md). For the live remaining-work list see
[TASKS.md](TASKS.md); ongoing evidence is in [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md).

The original package, minimum API 28, compile/target API 37, AGP 9.4.1,
Kotlin/Compose compiler 2.2.10, and Compose BOM 2026.02.01 are preserved.
The project was built and its original tests passed before editing.

## Working features

- Native Compose month calendar: Monday first, exactly six rows, adjacent dates,
  swipe and arrow navigation, Today, independent today/selection markers.
- Light/dark palettes, edge-to-edge insets, compact event pills, overflow counts,
  a selected-day event list, and a scrollable layout on short displays.
- Device calendar list and persistent visibility filters with calendar colors.
- Calendar Provider `Instances` queries for expanded recurring occurrences.
  UTC all-day boundaries and exclusive end times are handled separately from
  local timed events. Queries run on IO; a ContentObserver and activity resume
  refresh data. No cloud-sync implementation is added.
- Contextual read/write permission requests and usable browsing without access.
- Native creation of basic events: title, writable calendar/account, start/end,
  all-day, device time zone, location, notes, validation and discard confirmation.
- Existing event taps open the device calendar app with the occurrence timestamps.
  That app currently handles details, editing, deletion, repeats, and reminders.

## Changed files

Modified:
- `app/build.gradle.kts`: ViewModel dependency, using the existing lifecycle version.
- `gradle/libs.versions.toml`: catalog entry for that dependency.
- `app/src/main/AndroidManifest.xml`: calendar read/write permissions.
- `app/src/main/java/com/example/samsungcalendarclone/MainActivity.kt`: app entry point and resume refresh.
- `app/src/main/java/com/example/samsungcalendarclone/ui/theme/Theme.kt`: neutral light/dark theme.

Created under `app/src/main/java/com/example/samsungcalendarclone/`:
- `domain/model/CalendarModels.kt`: dates, models, event-to-day mapping.
- `data/calendar/AndroidCalendarRepository.kt`: provider observation, queries and creation.
- `ui/month/MonthViewModel.kt`: immutable screen state, loading and filtering.
- `ui/month/CalendarScreen.kt`: month UI, permissions, calendars sheet and event list.
- `ui/event/EventEditor.kt`: basic event creation.

Created tests:
- `app/src/test/java/com/example/samsungcalendarclone/CalendarDatesTest.kt`
- `app/src/androidTest/java/com/example/samsungcalendarclone/CalendarScreenTest.kt`
- `app/src/androidTest/java/com/example/samsungcalendarclone/EventEditorTest.kt`

## Validation

Commands:

```sh
./gradlew assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug
```

Final result: build successful; 11 JVM tests and 7 physical-device tests passed.
Lint reported 0 errors and 19 warnings.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Use this output, not the pre-existing Android Studio APK in `intermediates/apk`.

Date tests cover leap February, Sunday/Monday starts, both year boundaries,
132 months of consecutive six-week grids, exclusive midnight ends, UTC all-day
spans, DST-localized timed events and empty events. Device tests cover denied-data
UI, selection, navigation, swiping, light/dark rendering and editor validation.
Editor tests intercept the save callback and do not write test events to accounts.

The app was installed and launched on the connected Xiaomi 14 (API 36).
Real calendar events and the dark theme were visually verified on that device.
After the test runner cleanup, the final APK was reinstalled and cold-launched;
its no-permission month screen was also visually verified.
No existing events were edited or deleted. Actual provider event insertion has
not been exercised against a personal account.

## Next milestone

Build the complete in-app details/editor: edit/delete, explicit single-occurrence
versus series behavior, selectable time zones, reminders, recurrence, availability
and supported event colors. Then day/week/agenda/search, notifications and
rescheduling, HyperOS setup help, and widgets. These are not implemented yet.
This pass does not schedule its own notifications or request alarm permissions.

On Xiaomi, open the app and tap Connect to allow calendar access. Tap + to allow
write access and create an event. If access was denied, Calendars offers Settings.
A writable calendar must already exist; accounts and sync remain device-managed.
The instrumented test runner may remove the test installation, so reinstall the
final debug APK after running connected tests.

Provider implementation reference: [Android Calendar Provider documentation](https://developer.android.com/identity/providers/calendar-provider).
