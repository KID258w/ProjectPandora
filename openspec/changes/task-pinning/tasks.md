## 1. Requirements and API

- [x] 1.1 Document account-scoped pinning, display rules, and long-press behavior in task requirements.
- [x] 1.2 Document pin APIs and the `tasks_task_pin` database entity.
- [x] 1.3 Add this OpenSpec proposal, capability spec, design, and implementation checklist.

## 2. Backend

- [x] 2.1 Add the unique per-user task-pin model and database migration.
- [x] 2.2 Add the pinned-task list API with task-center visibility filtering and most-recently-pinned ordering.
- [x] 2.3 Add idempotent pin and unpin APIs with server-side authorization.

## 3. Android

- [x] 3.1 Fetch and display the pinned section under the Task Center title only when pins exist.
- [x] 3.2 Keep pinned tasks independent of ordinary filters and remove them from matching ordinary rows.
- [x] 3.3 Add long-press pin/unpin actions to the user's ordinary task rows; preserve tap-to-detail.
- [x] 3.4 Keep approval and read-only subordinate task screens free of pin controls.

## 4. Verification

- [ ] 4.1 Add automated coverage for API authorization, idempotency, persistence, ordering, and exclusion of pending tasks.
- [x] 4.2 Compile Android Kotlin and check Django configuration and migration consistency.
- [ ] 4.3 Verify long-press, tap, empty-section, and filter behavior on an App device or emulator.
