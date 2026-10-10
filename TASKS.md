# Project task checklist

Last updated: 2026-10-10 (Europe/Athens).

**Source of truth for remaining work.** `[x]` means the stated task is done;
`[ ]` means work remains. An implemented feature and its device verification
are separate tasks where verification is incomplete. Do not check a task because
it was planned, partially scaffolded, or delegated to the device calendar app.

Current checkpoint: **repeating-event scopes and native reminder controls** on top of
native details and editing, verified with isolated provider fixtures on Xiaomi.
Next: editor/navigation recovery across process death, then Milestone 4 views. Advanced recurrence remains pending. See [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md) for evidence and history.

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
- [ ] Verify landscape, split-screen and tablet layouts on devices/emulators.
- [ ] Verify large fonts, TalkBack, contrast and touch-target accessibility.
- [ ] Preserve visible month/selected date through process death.
- [ ] Refresh today automatically at midnight while the app stays foregrounded.
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
- [ ] Preserve editing/navigation state across process recreation and make in-flight saves lifecycle-safe.
- [ ] Support moving existing events to another calendar without losing attendee/reminder data.

## Milestone 4 — More views and navigation

- [ ] Day view.
- [ ] Week view.
- [ ] Agenda/schedule view.
- [ ] Search with bounded/asynchronous queries and useful empty results.
- [ ] Consistent navigation among views while preserving date and filters.

## Milestone 5 — Reminders and notifications

- [ ] Define reminder ownership and prevent duplicate alerts with the device calendar app.
- [ ] Implement notification channels and contextual notification-permission requests.
- [ ] Schedule reminders with legitimate Android APIs; define exact-alarm behavior/fallback.
- [ ] Reschedule after event/reminder edits, deletion and calendar filter changes as appropriate.
- [ ] Reschedule after reboot and time/time-zone changes.
- [ ] Open the correct event occurrence from a notification.
- [ ] Test reminders under denied notification/alarm permissions, idle and reboot.

## Milestone 6 — Xiaomi / HyperOS reliability

- [ ] Add concise setup/help for notifications and battery “No restrictions”.
- [ ] Explain background autostart and exact alarms only when applicable.
- [ ] Offer supported system-settings links; never silently change settings or bypass restrictions.
- [ ] Test reminder delivery on the Xiaomi with screen off and after prolonged idle.

## Milestone 7 — Widgets

- [ ] Month widget.
- [ ] Agenda widget.
- [ ] Light/dark appearance and resize support.
- [ ] Calendar filtering and transparency options where practical.
- [ ] Refresh widgets after event/date/time-zone changes and open the relevant app date/event.

## Verification and release readiness

- [x] Add leap-February, Sunday/Monday starts and both year-boundary date tests.
- [x] Test six-week continuity across 132 months.
- [x] Test all-day time zones, midnight-exclusive ends, DST and empty date mappings.
- [x] Run all 27 JVM tests successfully (10 calendar-date, 4 editor-time, 5 reminder, 7 recurrence/EXDATE and the starter test).
- [x] Run all 31 Xiaomi instrumented tests successfully (15 provider, 3 month, 4 details, 8 editor and the starter test).
- [x] Instrumented coverage: no-permission UI, arrows/Today, swipe, selection, light/dark rendering.
- [x] Instrumented editor coverage: blank title, no writable calendar, all-day save payload, prefilled timestamp preservation and discard confirmation.
- [x] Instrumented details coverage: delete confirmation/cancel, repeating-event handoff and read-only actions.
- [x] Run `assembleDebug` and `lintDebug`: successful, zero lint errors.
- [x] Install final debug APK and confirm a cold launch on Xiaomi Android 16 / API 36.
- [x] Visually verify the final installed app launches with READ/WRITE_CALENDAR ungranted.
- [ ] Reduce the remaining 19 lint warnings without unnecessary toolchain upgrades.
- [ ] Replace starter assertions with meaningful package/build smoke coverage if useful.
- [ ] Complete a full permission-denial/recovery and no-account device regression checklist.
- [ ] Run a signed release build and verify optimized/minified behavior.
- [ ] Review backup/privacy behavior for preferences and future stored event data.

## GitHub portfolio preparation (publication is for later)

- [x] Write a repository README with truthful current scope, setup, architecture and limitations.
- [x] Keep source history/handoff notes distinct from public-facing project overview.
- [ ] Choose final original app name, launcher icon and portfolio branding.
- [ ] Choose a license with the owner; document third-party licenses as needed.
- [ ] Capture polished light/dark screenshots and a short demo using fictional calendar data.
- [ ] Write an architecture/design case study with actual engineering tradeoffs.
- [x] Initialize Git and inspect the initial staged files for generated files, account data and credentials.
- [ ] Add CI for unit tests, lint and builds with the project's supported toolchain.
- [ ] Push to a private GitHub repository (owner requested 2026-10-10; waiting on which GitHub account owns it).
- [ ] Make the GitHub repository public when the owner requests it.
- [ ] Add release notes, known limitations and reproducible installation instructions for a portfolio release.
