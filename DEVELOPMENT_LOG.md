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

## 2026-10-10 — Week, day and agenda views, and search (Claude Code)

### Changes

- `CalendarView` (Month/Week/Day/Agenda) in `MonthViewModel`, saved with the month and date.
  All views share `selectedDate` and the calendar filters; `step()` pages by month, week or day.
  Month/week/day load the six-week month grid (which always contains the selected week);
  the agenda loads 56 days from the selected date, extendable to 366.
- `ui/views/TimeGrid.kt`: all-day strip plus a 24-hour grid; `domain/model/Views.kt`
  `layoutDay` clips events to the day and splits overlapping groups into columns.
  Current-time line, swipe/arrow paging, week day headers open the day view.
- `ui/views/AgendaList.kt` (days with events, "Show more") and `ui/views/SearchScreen.kt`.
  Search uses `Instances.CONTENT_SEARCH_URI` over a bounded window (1 year back, 2 ahead),
  debounced 250 ms, cancelled by newer queries, capped at 200, with hidden calendars excluded
  in the provider query. Opening a result selects its day first.
- `CalendarScreen` was split into a shared header (`ViewHeader`), `MonthContent` (the pager
  now follows state, with a guard so intermediate pages during animation are ignored) and the
  other views. Top bar: Calendars, Search, a view menu and Today.

### Verification

- Build/lint: 0 errors, 19 warnings (one new state-backed offset warning fixed).
  35 JVM tests passed (5 new layout/week-label tests; the label test pins Locale.US because
  current CLDR spells September "Sept" in en-GB).
- Xiaomi: **OK (44 tests)** including new tests for switching views with a shared date,
  week/day paging, agenda/search without permission, and provider search (title, place,
  date window, limit, hidden calendars) on the temporary LOCAL calendar.
- Added `ViewsRenderTest` (fictional events, light/dark captures). Its first runs failed
  with "No compose hierarchies found" because the phone had locked and dozed (`mWakefulness=Dozing`,
  keyguard showing); this also explains the earlier transient UI failures. It needs an
  unlocked screen and remains unchecked. One earlier capture confirmed overlap columns and labels.

Next: run `ViewsRenderTest` with the phone unlocked and review the captures; then Milestone 5.

## 2026-10-10 — View captures, tap-to-create and landscape (Claude Code)

- After the owner unlocked the phone, `ViewsRenderTest` passed. Reviewed the fictional
  week (light/dark), day and agenda captures (kept outside the repo): overlap columns, the
  all-day strip, today's marker and the current-time line render as intended. Hour labels
  were nudged up to centre on their lines; event chips gained a solid calendar-colour edge.
  Narrow overlapping chips in week view still break titles mid-word (new unchecked task).
- Tapping an empty slot in Day/Week view opens the editor at that half hour for one hour
  (`EventEditor(startAt = ...)`, ending after midnight when needed).
- The phone was in landscape during one full run: 7 UI tests failed because content below
  the fold was not displayed, and one all-day switch tap missed, saving a timed event.
  Fixes: tests now scroll to off-screen nodes, and wide landscape windows (>= 720 dp and
  wider than tall) show the month grid and the selected day side by side without scrolling.
- Verification: build/lint 0 errors, 19 warnings; 35 JVM tests; Xiaomi portrait run
  **OK (47 tests)**. The two-pane layout has not yet been seen on the device in landscape.

Next: Milestone 5 (reminders and notifications).

## 2026-10-10 — Opt-in reminder notifications (Claude Code)

### Decision

- The device calendar app already alerts for every provider reminder. Asked the owner how
  this app should behave, recommending an opt-in setting (default off), and built that while
  waiting for an answer.

### Changes

- `data/reminders/ReminderAlerts.kt`: `ReminderReceiver` handles the provider's
  `ACTION_EVENT_REMINDER` (manifest receiver; the action is an implicit-broadcast exception).
  It reads `CalendarAlerts` rows for the alarm time (any state, so another app marking them
  fired doesn't hide them), merges duplicate reminders per occurrence, skips calendars hidden
  in this app, and posts one high-importance notification per occurrence on the "reminders"
  channel. Nothing is written back to `CalendarAlerts`.
- No AlarmManager or exact-alarm permission: the provider schedules, and reschedules after
  edits, reboots and clock changes. `POST_NOTIFICATIONS` is declared and requested only when
  the user turns the setting on (with calendar access in the same prompt if needed).
- Calendars sheet: "Reminder alerts from this app" switch, duplicate-alert explanation, and a
  link to the app's notification settings when notifications are blocked.
- Tapping a notification (`MainActivity` is now `singleTop`) selects the day and opens the
  occurrence's details; a removed event shows a snackbar.
- `domain/model/ReminderAlert.kt`: notification text ("Today, 09:00 – 10:00 · Studio") and a
  stable per-occurrence notification ID. New original monochrome icon `ic_notification_event`.

### Verification

- Build/lint 0 errors, 19 warnings (two new ones fixed); 38 JVM tests (3 alert-text tests).
- Xiaomi **OK (49 tests)**: new tests insert `CalendarAlerts` rows for a fictional event in a
  temporary LOCAL calendar, check the query and the built notification (never posted), and
  check that nothing happens while the setting is off. No real alarm fired.
- Not yet observed: a real provider broadcast on the Xiaomi (HyperOS may differ), reboot and
  time-zone rescheduling, and behaviour with denied permissions.

Next: with the owner's OK, an end-to-end reminder test on the Xiaomi; then HyperOS help.

## 2026-10-10 — Xiaomi/HyperOS reminder setup help (Claude Code)

- HyperOS can stop background broadcasts reaching apps without Autostart and with battery
  restrictions, which would silently drop this app's reminder alerts. Once the reminder
  setting is on, the Calendars sheet now explains Autostart and "No restrictions" on
  Xiaomi/Redmi/POCO (or battery optimisation elsewhere, when it applies) with an App info
  link. `DeviceSetup` only reads state; it never changes settings or uses private intents.
- `ReminderSettingsTest` (3 tests) renders just the settings block, so no permission prompt or
  stored preference is touched. Xiaomi **OK (52 tests)**; lint 0 errors, 19 warnings.

Next: the end-to-end reminder check once the owner agrees.

## 2026-10-10 — Agenda and month widgets (Claude Code)

### Changes

- Added Glance 1.1.1 (`androidx.glance:glance-appwidget`) and declared WorkManager 2.7.1, the
  version Glance already pulls in. `ui/widget/CalendarWidgets.kt`:
  - `AgendaWidget`: two weeks of days with events (`agendaDays` drops events that already
    ended today; 40-event cap); tapping an event opens its details, a day header opens that date.
  - `MonthWidget`: six-week grid (`monthCells`), today highlighted, dots for days with events.
  - Both honour the app's hidden calendars and show "open to connect" without calendar access.
  - Refresh: a WorkManager content-URI trigger on the calendar provider (re-enqueued after
    each update; WorkManager instead of a raw JobScheduler job so IDs can't collide with
    Glance's own work), an inexact alarm at the next local midnight, and a receiver for
    time/time-zone/date/locale changes. Hidden-calendar and permission changes also refresh.
  - `MainActivity` handles `OPEN_DATE` from the widgets.
- Provider XML with resize ranges and `targetCell*` (API 31+, marked with tools:targetApi).

### Verification

- Build OK; lint 0 errors, 21 warnings (the two new ones are dependency-update notices for
  Glance and WorkManager). 41 JVM tests (3 new widget-data tests).
- `WidgetRenderTest` on the Xiaomi: **OK (4 tests)**. The month widget composed to RemoteViews
  and inflated correctly (dynamic colours from the wallpaper). The agenda's list is filled by a
  launcher's RemoteViews service, so its offscreen capture shows only the header; its content is
  checked with Glance's test host (`glance-appwidget-testing`, test-only).
- Full suite: 55 of 56 passed. `newTimedEventDefaultsToTenMinuteReminderThatCanBeChanged`
  reported "not displayed"; a rerun of the editor class then failed with "No compose
  hierarchies found", and the phone was found dozing and locked. Rerun pending an unlocked phone.
- Not yet done: placing the widgets on the real home screen.

## 2026-10-10 — Real reminders, release build and cleanup (Claude Code)

### Reminder delivery on the Xiaomi (owner approved: "Run that check and anything else you want")

- Granted the app READ_CALENDAR and POST_NOTIFICATIONS with `pm grant` (they remain granted;
  the owner can revoke them in App info). `ReminderEndToEndTest` (opt-in, `-e e2e true`) put a
  fictional event with a one-minute reminder in a temporary LOCAL calendar: this app's
  notification appeared **3 s after the alarm time**. The calendar was deleted afterwards.
- App-not-running check over adb (`content insert` as shell, app process killed but not
  force-stopped): the provider's alarm fired, but HyperOS logged
  `Unable to launch app com.example.samsungcalendarclone ... for broadcast EVENT_REMINDER:
  process is not permitted to auto start`, so only Xiaomi Calendar alerted. This confirms the
  in-app Autostart guidance. Temporary calendar and its event deleted; switch set back off.

### Release build

- Built `assembleRelease` (R8), aligned and signed it locally with the debug key outside the
  repo, and installed it. It crashed at startup: WorkManager 2.7 (via Glance) could not create
  `WorkDatabase` because R8 full mode removed the generated Room class's constructor (Room 2.2's
  rule keeps only the class name). Added `-keep class * extends androidx.room.RoomDatabase { <init>(); }`
  to `keepRules/rules.keep`. The rebuilt release launched (316 ms cold start) and survived
  switching Month/Week/Day/Agenda and opening search. The debug build was reinstalled after.

### Cleanup

- Removed unused starter colours, the starter unit test and the redundant activity label; two
  KTX suggestions applied. Lint: 0 errors, 12 warnings (all version notices).
- `AppSmokeTest` replaces the starter instrumented test.
- Backup rules now exclude `calendar_preferences.xml` (device-specific calendar IDs).

### Verification

- 40 JVM tests passed. Xiaomi full suite **OK (59 tests)**, with the 2 opt-in reminder classes
  skipped by assumption.

## 2026-10-10 — Autostart confirmed, smoother month swiping (Claude Code)

### Reminders with the app closed

- The owner had set battery to unrestricted (`deviceidle whitelist` lists the app) but could not
  find Autostart. `appops` showed `MIUIOP(10008): ignore`. Opened HyperOS's own Autostart screen
  (`com.miui.securitycenter/...AutoStartManagementActivity`) on the phone; the owner switched it on
  (`MIUIOP(10008): allow`). Rerun over adb: ActivityManager logged
  `Start proc ... for broadcast ReminderReceiver caller=com.android.providers.calendar`, and the
  app's notification appeared within the 5 s polling interval of the alarm time. Temporary calendar
  deleted, switch set back off.

### Month swiping

- Owner reported the month swipe was not smooth. Measured with 16 adb swipes and `dumpsys gfxinfo`
  (phone at 60 Hz; it had been warm). Debug build: p50/p90/p99 9/150/450 ms before the changes and
  13/150/250 ms after — debug Compose is slow regardless. Optimized (R8) build after the changes:
  7/24/46 ms; with an app-wide baseline profile compiled as on install: 8/19/26 ms; fully AOT: 8/14/24 ms.
- Changes: pager keeps neighbouring months composed (`beyondViewportPageCount = 1`); the
  ViewModel loads the previous, current and next month grids and no longer blanks events on a
  month change; `MonthGrid` takes only the events/selection/today it draws (unrelated updates
  skip it), measures row height once per page instead of a `BoxWithConstraints` per cell, and
  reuses a date formatter; event grouping runs on `Dispatchers.Default`;
  `app/src/main/baseline-prof.txt` adds the app's own code to the bundled library profiles.
- Left the optimized build (signed locally with the debug key, profile-compiled) installed for
  the owner's daily use; permissions and Autostart carried over.

### Verification

- Build OK; lint 0 errors, 12 warnings (version notices); JVM tests passed; Xiaomi full suite
  **OK (59 tests)** (57 run, 2 opt-in reminder checks skipped).
