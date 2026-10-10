# Instructions for coding agents

These instructions apply throughout this repository, including Codex and Claude Code.

## Read before continuing

1. `README.md` for the current product and build instructions.
2. `TASKS.md` for the authoritative completion checklist.
3. The latest entries in `DEVELOPMENT_LOG.md` for current evidence and next actions.
4. `IMPLEMENTATION_NOTES.md` for the first-pass source inventory and limitations.
5. `CODEX_HANDOFF.md` for the original product requirements.

Do not assume the original handoff describes the current source. Inspect relevant
files before editing. Preserve the working package/toolchain unless a concrete
issue justifies a change. No Git repository exists as of the first checkpoint;
recheck rather than assuming a remote or branch exists.

## Keep the handoff live

- Update `TASKS.md` in every meaningful change. Check completed tasks; keep partial
  or unverified work unchecked. Split tasks when implementation and verification differ.
- Add newly discovered work to the checklist rather than silently losing it.
- Append a dated entry to `DEVELOPMENT_LOG.md`: problem, changes, verification,
  limitations, any device side effects, and the next useful action.
- Update `README.md` when supported behavior or setup changes.
- Keep a concrete file inventory/checkpoint in `IMPLEMENTATION_NOTES.md` or a new
  linked checkpoint note when the existing note becomes historical.
- Never claim a test passed without running it. Record blocked checks explicitly.
- Before ending a work session, make the checklist and log match the actual files.

## Implementation and verification

- Keep Kotlin/Compose native, provider queries off the main thread, and state predictable.
- Use CalendarContract and account adapters; do not implement cloud sync or use hidden APIs.
- Preserve UTC all-day semantics, exclusive event end times and recurring-occurrence identity.
- Run appropriate build/tests after code changes. Avoid a full device cycle for docs-only edits.
- The verified APK path is `app/build/outputs/apk/debug/app-debug.apk`.
- Connected tests display light/dark screens, may prompt for installation, and may
  uninstall the app at cleanup. Announce device test cycles and reinstall the final
  app afterward if the user is testing it. Explain that permissions may need regranting.
- Do not write test fixtures into personal synced calendars. Use isolated test data
  for integration tests and clearly log cleanup of data created by the tests.
- Never commit credentials, signing keys, local SDK paths, device logs or screenshots
  containing real calendar/account data. Use fictional data for portfolio visuals.
- GitHub publication and license selection remain future tasks until requested by the owner.
