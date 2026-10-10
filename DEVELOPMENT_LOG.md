# Development log

Append dated entries after meaningful work. Record concrete changes, commands and
results, device side effects, limitations and the next action. Keep `TASKS.md`
current in the same change. Never replace a failed/pending verification with a
claim that it passed.

## 2026-10-10 — First working month/provider pass (Codex)

### Starting point

- Read `CODEX_HANDOFF.md` and inspected the existing Android Studio starter.
- No Git repository existed in the project directory.
- Existing package: `com.example.samsungcalendarclone`; min SDK 28;
  compile/target SDK 37; AGP 9.4.1; Kotlin Compose plugin 2.2.10;
  Compose BOM 2026.02.01. Preserved these settings.
- Gradle uses its configured Java 25 daemon toolchain; source/target compatibility
  remains Java 11. The Android Studio JBR supplied the local daemon.
- Baseline command `./gradlew assembleDebug testDebugUnitTest` passed.
- ADB found the connected Xiaomi 14, reporting Android API 36.

### Implementation

- Replaced the greeting with a Compose month calendar, 42 Monday-first cells,
  month swiping/arrows, adjacent dates, Today, selected date and a daily event list.
- Added a neutral light/dark palette, safe insets, event pills and overflow counts.
- Added repository/model/ViewModel layers and a lifecycle ViewModel dependency
  using the existing lifecycle version through the version catalog.
- Added visible device calendars, persisted filters, real `Instances` loading,
  background IO, query cancellation, content observation and resume refresh.
- Added contextual READ/WRITE_CALENDAR requests and graceful no-access/no-calendar UI.
- Added native basic event creation with title/calendar, start/end/all-day,
  location/notes, validation, writability checks and discard confirmation.
- Existing event details/editing currently route to another device calendar app.
- Added 10 date-model JVM tests and 6 feature instrumentation tests.
- Added short-window scrolling and a labelled all-day switch during validation.

### Verification and fixes

- First instrumentation compile found a test-only extra parenthesis; fixed it.
- An install attempt used the stale Android Studio `intermediates/apk` artifact
  and failed with `INSTALL_FAILED_TEST_ONLY`. Corrected the installation source
  to `app/build/outputs/apk/debug/app-debug.apk`.
- Final command:
  `./gradlew assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug`.
- Result: BUILD SUCCESSFUL; 11 JVM tests and 7 instrumented tests passed;
  lint: 0 errors, 19 warnings (mostly dependency-update notices, starter resources
  and Kotlin style suggestions). No version upgrades were made merely for lint.
- Visually verified the dark UI showing actual device events and a selected-day list.
- User independently confirmed the app opened and that they tried it.
- After the test runner cleaned up, reinstalled the final APK. Final cold launch
  succeeded (ADB status `ok`). READ_CALENDAR and WRITE_CALENDAR were ungranted;
  the visible app correctly showed Connect and a usable month, without crashing.

### Device side effects and data handling

- Instrumented tests install app/test packages, bring up test screens, explicitly
  render both light and dark themes, and clean up the installation afterward.
  This explains the install prompts and theme flashes the user observed.
- The final app was reinstalled and launched after testing. Permissions need to
  be granted again using Connect / +. Device-wide theme settings were not changed.
- No existing calendar events were edited/deleted and no test events were inserted.
  Editor tests intercept the save callback; actual provider insertion remains
  unchecked in `TASKS.md` until an isolated test calendar is available.
- Development screenshots with real account/event content stayed outside the repo
  in temporary files. Do not use them for public portfolio material.

### Remaining work at this checkpoint

- Full native event details/edit/delete/recurrence/reminders are pending.
- Day/week/agenda/search, notification scheduling, HyperOS help and widgets are pending.
- Process-death restoration, foreground-midnight refresh, localization and broad
  accessibility/adaptive-layout verification are pending.
- See `IMPLEMENTATION_NOTES.md` for the source-file inventory and `TASKS.md` for
  the complete checked/unchecked roadmap.

## 2026-10-10 — Persistent handoff and portfolio records (Codex)

- User requested a checked task list, an ongoing development log, and handoff
  support for Claude Code, with eventual GitHub portfolio publication.
- Added `TASKS.md`, this log, `README.md`, `AGENTS.md` and `CLAUDE.md`.
- `TASKS.md` is the authoritative completion checklist; implementation and unverified
  integration behavior are tracked separately.
- `AGENTS.md` requires future coding sessions to read and update the records;
  `CLAUDE.md` points Claude Code to those shared instructions.
- Expanded `.gitignore` for nested build outputs, Kotlin caches, local signing
  material/configuration and device captures before future Git initialization.
- No Git repository, remote, license choice or public publication was created.
- Documentation/ignore-only changes after the successful test run did not require
  another device test cycle.

Next action: start with unchecked native event-detail/editor tasks. Before testing
writes, arrange isolated test data rather than creating fixtures in personal
synced calendars. Announce device test cycles so installation/theme changes are expected.

## 2026-10-10 — Native details, non-repeating edits and isolated provider tests (Codex)

### Problem and changes

- Continued from the first-pass checklist. Event taps previously depended on another
  calendar app, and actual provider writes had not been tested.
- Added native details with occurrence times, time zone, calendar/account, location,
  notes, and stored reminders. Existing event taps now stay in this app.
- Added prefilled editing and explicitly confirmed deletion of writable non-repeating
  events. Existing calendar assignment/reminders and unedited provider fields are preserved.
- Conditional provider mutations match the exact event ID and editable snapshot with
  an expected row count of one, rejecting external changes rather than overwriting them.
- Recurring series and exception events stay read-only in the native mutation flow;
  the details screen explicitly offers the device-calendar handoff for recurrence scopes.
- Added DST-gap rejection and preservation of unchanged subminute timestamps and
  overlap offsets. Invalid stored zones have a readable external-edit fallback.
- Expanded unsaved-change detection beyond text to date/time/all-day/calendar changes
  and guarded repeated Save taps. Full process-death recovery is still pending.
- Stopped automatic permission-error query loops in the month ViewModel.

### Device testing and fixes

- Built the app and test APK. The first test-helper installation was rejected by
  Xiaomi with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`.
  Paused the dependent tests, explained the device message, and retried only after
  the user replied “Retry; I’ll accept the prompt”. The retry succeeded.
- Used manual `adb install -r` plus `am instrument` instead of the Gradle connected
  runner, preserving the app installation/runtime permissions after this test cycle.
- Added UUID-named temporary LOCAL calendars via the documented CalendarContract
  API. Mutations were scoped to those calendars. `@After` deleted the exact test
  calendar and asserted its removal, including after a failing test.
- Instrumentation temporarily adopted READ/WRITE_CALENDAR shell identity, then
  dropped it. It did not persistently grant the main app calendar permissions.
- The first full device run found 5 failures: 2 provider tests exposed that a conditional
  selection is not allowed on an event-specific mutation URI; 3 details UI tests exposed
  lower content hidden by the half-expanded sheet.
- Fixed provider operations to use `Events.CONTENT_URI` with an explicit `_ID = ?`
  AND snapshot selection. Fixed details to open fully expanded and remain scrollable.
- Reran the complete 17-test device suite: **OK (17 tests)**. All provider mutations,
  cleanup assertions, UI tests and recurring/read-only guards passed.
- Final local command:
  `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`.
  Result: BUILD SUCCESSFUL, 15 JVM tests passed, lint 0 errors / 19 warnings.
- Raw instrumentation outputs remain under the ignored
  `app/build/reports/manual-device/` directory, including the initial failure report.
- No personal synced calendar events were created, edited or deleted. Only the temporary
  local fixture calendars and their fictional events were mutated and removed.

### Handoff

- Updated `TASKS.md`, `README.md`, `CLAUDE.md` and the historical implementation-note link.
- Added `docs/NATIVE_EVENT_CHECKPOINT.md` with changed-file inventory, fixture safety,
  manual device-test commands, results and known limitations.
- Next: explicit recurrence mutation scopes, native reminder controls, selectable zones,
  and lifecycle-safe saves/navigation restoration. The larger views/reminders/widgets
  roadmap remains unchecked; native single-event CRUD is no longer a pending task.

## 2026-10-10 — Native reminder controls and local Git (Claude Code)

### Problem and changes

- The editor could only preserve reminders; adding or changing them required the
  device calendar app.
- Added `domain/model/Reminders.kt`: reminder method constants, presets, defaults,
  order-insensitive comparison and labels (all-day reminders count back from midnight).
- `DeviceCalendar` now carries `MAX_REMINDERS` and `ALLOWED_REMINDERS`; `NewEvent`
  carries its reminder list and `details()` fills it.
- Editor: reminders section with Remove buttons and an Add reminder preset menu,
  limit-aware; untouched defaults on a new event follow the all-day switch; reminder
  changes count as unsaved changes and survive configuration changes.
- Repository: create inserts the event and reminders in one batch. Update replaces
  reminder rows only when changed, after assert-query checks that the loaded rows are
  still current, in the same transaction as the conditional event update. Limits and
  methods are validated; existing methods such as email are kept.
- Details sheet uses the shared reminder labels.

### Verification

- `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`:
  BUILD SUCCESSFUL; 20 JVM tests passed; lint 0 errors, 19 warnings (unchanged).
- Manual ADB instrumentation on the Xiaomi (app installation and permissions preserved):
  **OK (23 tests)**. New provider tests cover create/replace/clear reminders, a stale
  reminder edit rolling back the title change too, and the 5-reminder / allowed-method
  limits. New editor tests cover the default, remove/add, all-day default and limit.
- All mutations used the temporary UUID LOCAL calendars, which the tests deleted and
  asserted gone. No personal calendar data was touched. The app was relaunched afterward.
- Raw output: `app/build/reports/manual-device/reminders-run.txt` (ignored).

### Git

- The owner asked for a private GitHub repository. Initialized Git on `main`, reviewed
  the 60 staged files (no build output, `local.properties`, keystores, APKs, logs or
  local paths) and committed locally with repo-local author `Pagakey` and the GitHub
  no-reply email (the machine's global identity was unrelated).
- Not pushed yet: `gh` is signed in as a different account (`gmaridakis`), so the owner
  is choosing which account owns the repository.

Next: push once the account is confirmed; then recurrence mutation scopes and
lifecycle-safe editor/navigation restoration.

## 2026-10-10 — Repeating-event scopes (Claude Code)

### Problem and changes

- Repeating events were read-only in the app. Edit/Delete now offer This event, This and
  following events, and All events, implemented in `AndroidCalendarRepository`
  (`updateOccurrences` / `deleteOccurrences`) with rule helpers in `domain/model/Recurrence.kt`.
- Details shows a scope choice (radio list) for edit and inside the delete confirmation;
  the editor locks all-day for series-wide scopes. RDATE series keep the handoff.
- See `docs/NATIVE_EVENT_CHECKPOINT.md` for the per-scope provider operations.

### Device findings and fixes

- First Xiaomi run: 5 of 30 failed. The provider rejects DTEND and CALENDAR_ID on exception
  inserts; partial series updates (RRULE alone, or DTSTART without RRULE) left instances
  stale or collapsed to one. Fixed by sending a duration on exceptions and the full series
  timing on every series update.
- Exceptions on device-only (unsynced) series made every occurrence disappear: the platform
  provider re-expands an unsynced exception without its series. Synced series (with
  `_SYNC_ID`) work. Device-only series now use EXDATE plus a standalone event instead.
  Provider tests cover both, using a fictional sync ID on the temporary LOCAL calendar.

### Verification

- `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`: BUILD
  SUCCESSFUL; 27 JVM tests passed; lint 0 errors, 19 warnings (unchanged).
- Full manual ADB instrumentation on the Xiaomi: **OK (31 tests)**. Outputs are in the
  ignored `app/build/reports/manual-device/` (`recurrence-run*.txt` hold the failing runs).
- All mutations were confined to temporary UUID LOCAL calendars, deleted and asserted
  gone after each test. No personal calendar data was touched; the app was relaunched.

### Still open

- (Resolved) The owner confirmed Pagakey should own the repository. Created private
  `https://github.com/Pagakey/SamsungCalendarClone` and pushed `main` with all three commits.
- Next: editor/navigation recovery across process death, then day/week/agenda views.

## 2026-10-10 — Process-death recovery and lifecycle-safe saves (Claude Code)

### Problem and changes

- The month, the open event and the editor lived in `remember`, and saves ran in the
  editor's composition scope: a rotation mid-save lost the result, and process death lost
  the navigation. A retried create could duplicate an event.
- Added `SavedStateHandle` month/date to `MonthViewModel`; Bundle savers for the open
  occurrence and `EditRequest` (`ui/event/EventSaving.kt`); writes moved to
  `viewModelScope` behind a `SaveState` flow consumed by the screen, editor and details sheet.
- Repository: draft `UID_2445` dedupe for create, idempotent retry of single update/delete,
  refusal of a duplicate single-occurrence exception, and `CalendarChangeRejected` for
  user-facing validation messages (a subclass of IllegalArgumentException).

### Verification

- `./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`: BUILD
  SUCCESSFUL; 27 JVM tests; lint 0 errors, 19 warnings (unchanged).
- First full Xiaomi run: 33 of 39 passed; 6 consecutive Compose UI tests reported "No
  compose hierarchies found" (the test activity didn't draw, likely while the phone was in
  use or dozing). The same 9 screen/details tests then passed alone, and a second full run
  was **OK (39 tests)**. Outputs: `app/build/reports/manual-device/restore-*.txt` (ignored).
- Real process death: navigated two months ahead, pressed Home, `am kill`, relaunched;
  a new process showed December 2026 with Thursday 10 Dec selected. Calendar permissions
  were ungranted at the time, so no personal data was displayed; UI dumps were deleted.
- Provider tests used only temporary UUID LOCAL calendars, deleted and asserted gone.

Next: refresh "today" at midnight while the app is open, then the day/week/agenda views.

## 2026-10-10 — Midnight and clock-change refresh (Claude Code)

- "Today" was only recomputed on resume, so an app left open past midnight kept the old
  today marker. `MonthViewModel.startClock()` now waits until the next local midnight
  (`domain/model/Clock.kt`, DST-aware) and calls `onClockChanged()`; `MainActivity`
  starts it in `onStart`, stops it in `onStop`, and also listens for date, time and
  time-zone change broadcasts while visible. A selection resting on the old today follows
  it; other selections stay. A zone change regroups events.
- Verification: build/lint as before (0 errors, 19 warnings); 30 JVM tests (3 new clock
  tests incl. 23/25-hour DST days); Xiaomi **OK (41 tests)** including 2 new UI tests that
  drive `onClockChanged`. A real midnight crossing on the device is still unchecked.

Next: Milestone 4 — day, week and agenda views with shared navigation, then search.
