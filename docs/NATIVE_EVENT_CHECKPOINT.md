# Native event details and editing checkpoint

Date: 2026-10-10. The live completion list is [TASKS.md](../TASKS.md).
This extends the historical first pass in [IMPLEMENTATION_NOTES.md](../IMPLEMENTATION_NOTES.md).

## Behavior

Tapping a selected-day event opens native details: title, occurrence dates, time
zone, calendar/account, location, notes and stored reminders. Another calendar
app is optional for viewing these details.

For writable, non-repeating events, the screen offers Edit and a confirmed Delete.
The editor is prefilled and preserves the existing calendar, reminders, and fields
outside the basic editor. It preserves unchanged seconds/milliseconds and DST
overlap offsets. New times in a daylight-saving gap are rejected.

Repeating events and their exceptions are explicitly read-only in this native
mutation flow. “Open in calendar app” carries the occurrence begin/end times so
the device app can offer its supported recurrence scopes. Single/future/series
editing is still pending; do not present the native editor as complete recurrence support.

Updates and deletions match the loaded event's ID and editable fields atomically
using a conditional operation with an expected row count of one. If another app
changes those fields, the user must reopen the event instead of overwriting it.

## Source changes

Under `app/src/main/java/com/example/samsungcalendarclone/`:

- `domain/model/CalendarModels.kt`: detailed event model, reminder model and edit capabilities.
- `domain/model/EventTimes.kt` (new): DST-aware editor time resolution.
- `data/calendar/AndroidCalendarRepository.kt`: details/reminder reads, capability lookup,
  and conditional non-repeating update/delete operations.
- `ui/event/EventDetailsSheet.kt` (new): native details, permission/read-only/repeating states,
  edit entry point and delete confirmation.
- `ui/event/EventEditor.kt`: prefilled editing, robust dirty checks and timestamp preservation.
- `ui/month/CalendarScreen.kt`: native details/editor routing and mutation feedback.
- `ui/month/MonthViewModel.kt`: details/update/delete methods; stop automatic query retry loops
  when the provider denies access.

Tests:

- `app/src/test/java/com/example/samsungcalendarclone/EventTimesTest.kt` (new).
- `app/src/androidTest/java/com/example/samsungcalendarclone/CalendarRepositoryTest.kt` (new).
- `app/src/androidTest/java/com/example/samsungcalendarclone/EventDetailsTest.kt` (new).
- `app/src/androidTest/java/com/example/samsungcalendarclone/EventEditorTest.kt` (extended).

## Isolated device fixtures

Provider tests create a UUID-named calendar with `ACCOUNT_TYPE_LOCAL` using the
public CalendarContract local-calendar insertion contract. Every test mutation
is scoped to that newly created calendar. `@After` deletes that exact calendar
and asserts that it is gone, including when the test body fails.

Tests temporarily adopt the documented instrumentation shell calendar permissions
and release that identity afterward. They do not change the installed app's runtime
permission grants or write fixtures to personal synced accounts.

Build command:

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
```

To preserve the installed app and its data while testing on an authorized device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.example.samsungcalendarclone.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start -n com.example.samsungcalendarclone/.MainActivity
```

Accept any device installation prompts. Inspect the instrumentation output for
failures: its shell exit code alone is not proof of passing tests. This manual
runner invocation avoids Gradle connected-test installation cleanup.

## Verification result

- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` and `lintDebug`: passed.
- 15 JVM tests passed; lint: zero errors, 19 warnings.
- Full manually launched Xiaomi instrumentation run: **OK (17 tests)**.
- Five provider tests cover create/read/update/delete, reminder preservation,
  all-day date spans, recurring expansion/guards, read-only guards and stale-edit rejection.
- Cleanup assertions passed for all temporary calendars, including the initial failed run.
- UI coverage includes native delete confirmation/cancel, repeating/read-only states,
  prefilled editing and unchanged timestamps, plus previous month/light/dark coverage.
- Machine-local raw test evidence: `app/build/reports/manual-device/instrumentation.txt`.
  The initial failed run is retained as `first-run.txt` in the same ignored directory.

## Reminder controls (added later on 2026-10-10)

The editor lists reminders with Remove buttons and an Add reminder menu of presets
(at start to 1 week before for timed events; 09:00 one day, two days and a week
before for all-day events). New timed events default to 10 minutes before; new
all-day events start with none. Reminders added here use a notification when the
calendar allows it, and the calendar's `MAX_REMINDERS` disables adding past the limit.

`AndroidCalendarRepository.create` inserts the event and its reminders in one batch
using a back reference. `update` rewrites reminder rows only when they changed: it
asserts the loaded reminder rows are still present, deletes them and inserts the new
set in the same batch as the conditional event update, so a stale event or reminder
snapshot rolls back everything. Files: `domain/model/Reminders.kt` (new),
`CalendarModels.kt`, `AndroidCalendarRepository.kt`, `EventEditor.kt`,
`EventDetailsSheet.kt`; tests `RemindersTest.kt` (new), `CalendarRepositoryTest.kt`
and `EventEditorTest.kt`. Result: 20 JVM tests and **OK (23 tests)** on the Xiaomi.

## Repeating-event scopes (added later on 2026-10-10)

Edit and Delete on a repeating occurrence ask for **This event**, **This and following
events** or **All events** (`RecurrenceScope`). Changed occurrences stored as their own
rows only offer This event. Series with RDATE stay with the device calendar app.

- This event, synced series: insert through `Events.CONTENT_EXCEPTION_URI` with
  `ORIGINAL_INSTANCE_TIME`, a DURATION and no CALENDAR_ID/DTEND (the provider rejects
  those), then set the exception's reminders explicitly. Deleting inserts a cancelled exception.
- This event, device-only series (no `_SYNC_ID`): the platform provider re-expands an
  unsynced exception without its series, which hid every occurrence on the Xiaomi. So the
  date is added to EXDATE, and an edit becomes a standalone event with the series reminders.
- This and following: the series gets `UNTIL` just before the occurrence; an edit inserts
  a new series from there, with COUNT reduced by earlier instances, exceptions and EXDATEs.
  Deleting also removes changed occurrences after the split.
- All events: the series start moves by the wall-clock change (DST-safe); a time change is
  refused while individually changed occurrences exist.
- Every series update sends the full timing (DTSTART, DURATION, RRULE, EXDATE, ALL_DAY,
  zone). Partial updates such as RRULE alone left the provider's instances stale.
- Each operation matches a snapshot of the series row (including EXDATE) in the same batch,
  so changes made elsewhere cause a rejection instead of an overwrite.

Files: `domain/model/Recurrence.kt` (new), `CalendarModels.kt`, `AndroidCalendarRepository.kt`,
`EventDetailsSheet.kt`, `EventEditor.kt` (all-day locked for series scopes), `CalendarScreen.kt`,
`MonthViewModel.kt`; tests `RecurrenceTest.kt` (new), `CalendarRepositoryTest.kt`, `EventDetailsTest.kt`.
Result: 27 JVM tests and **OK (31 tests)** on the Xiaomi.

## Remaining limitations

- Existing events cannot yet move between calendars.
- Reminder controls write provider reminder rows (see the reminder section below);
  this app's own notification scheduling is still unfinished.
- Selecting a different time zone and controlling ambiguous-time offsets explicitly
  are still pending; existing zones/offsets are preserved where supported.
- Full editor/navigation restoration and in-flight-save recovery across process death
  remain pending.
- No test suite simulates every sync provider or OEM behavior; real cloud sync refresh
  and broader permission-revocation scenarios remain on the checklist.

Reference: [Android Calendar Provider](https://developer.android.com/identity/providers/calendar-provider).
