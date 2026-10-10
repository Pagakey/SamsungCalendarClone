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

## Remaining limitations

- Existing events cannot yet move between calendars.
- Reminder rows are displayed/preserved; native reminder writing and notification
  scheduling are separate unfinished tasks.
- Selecting a different time zone and controlling ambiguous-time offsets explicitly
  are still pending; existing zones/offsets are preserved where supported.
- Full editor/navigation restoration and in-flight-save recovery across process death
  remain pending.
- No test suite simulates every sync provider or OEM behavior; real cloud sync refresh
  and broader permission-revocation scenarios remain on the checklist.

Reference: [Android Calendar Provider](https://developer.android.com/identity/providers/calendar-provider).
