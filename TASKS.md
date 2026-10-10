# Project task checklist

Last updated: 2026-10-10 (Europe/Athens).

**Source of truth for remaining work.** `[x]` means the stated task is done;
`[ ]` means work remains. An implemented feature and its device verification
are separate tasks where verification is incomplete. Do not check a task because
it was planned, partially scaffolded, or delegated to the device calendar app.

Current checkpoint: **process-death recovery and lifecycle-safe saves**, on top of
repeating-event scopes, reminder controls and native details/editing, verified on Xiaomi.
Midnight/clock-change refresh and Milestone 4 views (day, week, agenda, search) are in.
Milestone 5 reminder alerts are implemented (opt-in). Next: an end-to-end reminder
test on the Xiaomi (needs the owner's OK). HyperOS help and agenda/month widgets are in;
next: see the widgets on a real home screen, then the release-readiness items. Advanced recurrence remains pending. See [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md) for evidence and history.

## Project foundation

- [x] Read the original product brief in `CODEX_HANDOFF.md`.
- [x] Inspect Gradle, manifest, package, activity, theme and SDK versions before edits.
- [x] Build and test the original starter before changing it.
- [x] Preserve package `com.example.samsungcalendarclone` and working Gradle setup.
- [x] Preserve existing min SDK 28 and compile/target SDK 37; run on Xiaomi API 36.
- [x] Establish models, Calendar Provider repository and immutable ViewModel state.
- [x] Use original Compose implementation and no proprietary Samsung assets/code.
- [x] Add this persistent checked task list.
- [x] Add a development log, README and continuation instructions for Codex/Claude Code.
- [x] Extend ignore rules for generated outputs and local signing/configuration files.

## Milestone 1 — Month screen

- [x] Large month title and year indicator.
- [x] Monday-first weekday header and stable six-row grid.
- [x] Adjacent-month dates; tapping one navigates to its month.
- [x] Previous/next arrows and animated horizontal month swiping.
- [x] Today action selects today and returns to its month.
- [x] Separate today marker and selected-date ring.
- [x] Event pills, calendar colors and overflow counts.
- [x] Selected-day event list and empty state.
- [x] Floating add button opens the native basic creation flow after permission.
- [x] Restrained light/dark theme and system-bar insets.
- [x] Add scrolling for short windows so all six weeks remain reachable.
- [x] Wide landscape windows show the month grid and the selected day side by side (narrow short windows scroll).
- [ ] Device-check the two-pane landscape layout, split-screen and tablet sizes (portrait suite passes; the earlier landscape run predates the two-pane layout).
- [ ] Verify large fonts, TalkBack, contrast and touch-target accessibility.
- [x] Preserve visible month/selected date through process death (verified with a real `am kill` on Xiaomi).
- [x] Refresh today automatically at midnight while the app stays foregrounded (also on time/zone/date-change broadcasts).
- [ ] Device-check a real midnight crossing or manual clock change (covered by unit/UI tests calling the same entry point).
- [ ] Refine localization, locale time formatting and RTL behavior; extract UI strings.
- [ ] Measure scrolling/swiping performance with a dense calendar.

## Milestone 2 — Calendar Provider

- [x] Declare appropriate calendar permissions; request read on Connect and write on Add.
- [x] Keep date browsing usable without permission and provide a Settings recovery link.
- [x] Query visible device calendars and display names/accounts/colors.
- [x] Support multiple calendars and persistent per-calendar visibility filters.
- [x] Load visible-range occurrences from `CalendarContract.Instances`.
- [x] Handle UTC all-day dates, exclusive end times and local timed event boundaries.
- [x] Run provider queries off the main thread and cancel superseded UI loads.
- [x] Refresh through a ContentObserver and activity resume.
- [x] Display loading/error states and a retry action.
- [x] Handle empty calendar lists in the UI; disable saving with no writable calendar.
- [x] Visually verify real device events in the Xiaomi month grid and selected-day list.
- [ ] Device-test calendar filtering persistence, external edits and sync-driven refresh.
- [ ] Device-test a truly empty provider, read-only calendars and permission revocation.
- [ ] Verify recurring, multi-day and DST-boundary events against controlled provider fixtures.
- [ ] Decide whether the calendar list should expose calendars hidden by other apps (currently only provider-visible calendars are queried).

## Milestone 3 — Native event details and editor

- [x] Create native basic event form with title, calendar/account, start/end and all-day.
- [x] Add location, notes and display of the current device time zone.
- [x] Validate blank titles and end-after-start; store all-day end dates as exclusive UTC.
- [x] Check calendar writability before insertion and handle save errors.
- [x] Implement basic `CalendarContract.Events` insertion off the main thread.
- [x] Confirm before discarding a nonempty unsaved form.
- [x] Provide an explicit “Open in calendar app” action with occurrence timestamps and a missing-handler message.
- [x] Verify actual event insertion and round-trip reading using isolated local test calendars, with asserted cleanup.
- [x] Build an in-app event details screen, including a useful fallback without another calendar app.
- [x] Load and edit existing non-repeating events natively; preserve their existing calendar and reminders.
- [x] Delete non-repeating events natively with explicit confirmation.
- [x] Handle repeating events explicitly: this occurrence, future occurrences and entire series.
- [x] Device-only (unsynced) series: change one occurrence via EXDATE + standalone event, since the platform provider re-expands unsynced exceptions without their series.
- [ ] Series with RDATE, TZID-form EXDATE + COUNT splits, or individually changed later occurrences still hand off to the device calendar app.
- [ ] Edit the repeat rule itself (frequency/end) natively.
- [ ] Add recurrence presets and preserve supported/custom recurrence rules safely.
- [ ] Add a selectable event time zone with correct DST validation.
- [x] Read existing reminder rows through `CalendarContract.Reminders` and display them in details.
- [x] Add native reminder controls and write reminder rows atomically with events.
- [x] Respect per-calendar `MAX_REMINDERS` / `ALLOWED_REMINDERS`; keep existing email/SMS reminder methods.
- [x] Reject stale reminder replacements and roll back the whole event save.
- [ ] Offer custom reminder times beyond the preset list.
- [ ] Expose event colors and availability only where the selected calendar supports them.
- [x] Verify native create/edit/delete, reminder preservation, stale-write rejection and read-only calendar guards on Xiaomi using isolated data.
- [x] Verify recurring-instance expansion and rejection of accidental single-event mutations on a series.
- [x] Verify native recurrence mutation scopes, including synced exceptions, device-only series, splits with COUNT, stale rejection and read-only accounts.
- [x] Guard repeated Save taps and detect unsaved changes to dates/all-day/calendar as well as text.
- [x] Reject stale non-repeating updates/deletes with an atomic snapshot match.
- [x] Preserve subminute timestamps and overlap offsets; reject times in a DST gap.
- [x] Keep repeating events/exception edits and deletes out of the single-event mutation path.
- [x] Preserve editing/navigation state across process recreation and make in-flight saves lifecycle-safe.
- [x] Make retried saves safe: create dedupes by iCalendar UID, a landed update/delete retry succeeds, a second single-occurrence exception is refused.
- [ ] Device-check editor restoration after a real process kill (needs calendar permission granted; covered by StateRestorationTester and Bundle parcel tests).
- [ ] Support moving existing events to another calendar without losing attendee/reminder data.

## Milestone 4 — More views and navigation

- [x] Day view (24-hour grid, overlap columns, all-day strip, current-time line, swipe/arrows).
- [x] Week view (Monday-first, tap a day header to open it in Day view).
- [x] Agenda/schedule view (8 weeks from the selected date, "Show more" up to a year).
- [x] Search with bounded/asynchronous queries and useful empty results (provider instance search, 1 year back to 2 years ahead, debounced, 200-result cap, hidden calendars excluded in the query).
- [x] Consistent navigation among views while preserving date and filters (shared selected date, view saved across process death).
- [x] Visually check week/day/agenda on the Xiaomi with fictional events (`ViewsRenderTest` light/dark captures reviewed).
- [x] Create an event by tapping an empty time slot in Day/Week view (half-hour slots, one-hour default, may end after midnight).
- [ ] Narrow overlapping events in week view break titles mid-word; consider a cascading layout.
- [ ] Optional: page the agenda beyond one year and widen search on request.

## Milestone 5 — Reminders and notifications

- [x] Define reminder ownership and prevent duplicate alerts: the provider schedules; this app's alerts are an opt-in setting (default off) in the Calendars sheet, explaining duplicates (owner asked to confirm the default on 2026-10-10).
- [x] Implement a notification channel and contextual notification-permission requests (asked only when turning the setting on).
- [x] Schedule reminders with legitimate Android APIs: the provider's own alarms deliver `ACTION_EVENT_REMINDER` (an implicit-broadcast exception); no exact-alarm permission or own AlarmManager use.
- [x] Reschedule after edits/deletion: the provider rebuilds alarms from reminder rows; hidden calendars are filtered when an alert fires.
- [ ] Device-verify rescheduling after reboot and time/time-zone changes (provider-owned; not yet observed on the Xiaomi).
- [x] Open the correct event occurrence from a notification (`ACTION_OPEN_EVENT` → day selected, details opened; removed events get a notice).
- [x] End-to-end reminder on the Xiaomi with a fictional event while the app process runs (`ReminderEndToEndTest`, opt-in): notification posted 3 s after the alarm time.
- [ ] Recheck a reminder with the app not running once Autostart is allowed: with Autostart off, HyperOS logged "process is not permitted to auto start" and dropped the broadcast (only the Xiaomi Calendar alerted).
- [ ] Test reminders under denied notification/alarm permissions, idle and reboot.

## Milestone 6 — Xiaomi / HyperOS reliability

- [x] Add concise setup/help for notifications and battery “No restrictions” (shown only once this app's reminder alerts are on).
- [x] Explain background autostart only when applicable (Xiaomi/Redmi/POCO); exact alarms need no explanation because the provider schedules them.
- [x] Offer supported system-settings links (App info, app notification settings); never silently change settings or bypass restrictions.
- [ ] Test reminder delivery on the Xiaomi with screen off and after prolonged idle.

## Milestone 7 — Widgets

- [x] Month widget (six-week grid, today highlighted, dots on days with events; a day opens the app there).
- [x] Agenda widget (next two weeks, ended events of today dropped, 40-event cap; an event opens its details).
- [x] Light/dark appearance and resize support (Glance theme with dynamic colours; resizable providers).
- [x] Calendar filtering (widgets honour the app's hidden calendars).
- [ ] Transparency/background options for the widgets.
- [x] Refresh widgets after event changes (WorkManager content-URI trigger), at midnight (inexact alarm), and on time/time-zone/locale changes; open the relevant app date/event.
- [ ] Check both widgets on the Xiaomi home screen, including resize and refresh after an edit (the agenda list only renders in a launcher).

## Verification and release readiness

- [x] Add leap-February, Sunday/Monday starts and both year-boundary date tests.
- [x] Test six-week continuity across 132 months.
- [x] Test all-day time zones, midnight-exclusive ends, DST and empty date mappings.
- [x] Run all 40 JVM tests successfully (10 calendar-date, 4 editor-time, 5 reminder, 7 recurrence/EXDATE, 3 clock, 5 view-layout, 3 alert-text and 3 widget-data tests; the starter test was removed).
- [x] Run all 52 Xiaomi instrumented tests successfully (18 provider, 9 screen, 4 details, 11 editor, 2 saved-state, 2 view-render, 2 reminder-alert, 3 reminder-settings and the starter test).
- [x] Rerun the full suite on an unlocked phone: **OK (59 tests)**, of which 2 are opt-in reminder checks skipped by default.
- [x] Instrumented coverage: no-permission UI, arrows/Today, swipe, selection, light/dark rendering.
- [x] Instrumented editor coverage: blank title, no writable calendar, all-day save payload, prefilled timestamp preservation and discard confirmation.
- [x] Instrumented details coverage: delete confirmation/cancel, repeating-event handoff and read-only actions.
- [x] Run `assembleDebug` and `lintDebug`: successful, zero lint errors.
- [x] Install final debug APK and confirm a cold launch on Xiaomi Android 16 / API 36.
- [x] Visually verify the final installed app launches with READ/WRITE_CALENDAR ungranted.
- [x] Reduce lint warnings without toolchain upgrades: 12 remain, all dependency/Gradle version notices.
- [x] Replace starter assertions with `AppSmokeTest` (launcher, permissions incl. no exact-alarm/Internet, reminder receiver, both widget providers).
- [ ] Complete a full permission-denial/recovery and no-account device regression checklist.
- [x] Verify the optimized (R8) release build on the Xiaomi, signed locally with the debug key: it crashed at startup until Room's generated database constructor was kept; then launched (316 ms cold) and survived switching every view and opening search.
- [ ] Create a real release signing configuration and key with the owner (kept out of Git).
- [x] Review backup/privacy behavior: calendar data stays in the provider; `calendar_preferences.xml` (device-specific calendar IDs, reminder switch) is excluded from cloud backup and device transfer.

## GitHub portfolio preparation (publication is for later)

- [x] Write a repository README with truthful current scope, setup, architecture and limitations.
- [x] Keep source history/handoff notes distinct from public-facing project overview.
- [ ] Choose final original app name, launcher icon and portfolio branding.
- [ ] Choose a license with the owner; document third-party licenses as needed.
- [ ] Capture polished light/dark screenshots and a short demo using fictional calendar data.
- [ ] Write an architecture/design case study with actual engineering tradeoffs.
- [x] Initialize Git and inspect the initial staged files for generated files, account data and credentials.
- [ ] Add CI for unit tests, lint and builds with the project's supported toolchain.
- [x] Push to the private GitHub repository `Pagakey/SamsungCalendarClone` (branch `main`, 2026-10-10).
- [ ] Make the GitHub repository public when the owner requests it.
- [ ] Add release notes, known limitations and reproducible installation instructions for a portfolio release.
