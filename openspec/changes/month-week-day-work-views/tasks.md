## 0. Android demo prototype

- [x] 0.1 Update the Android demo for month spans, a compact seven-day strip without horizontal scrolling, fixed date/all-day areas, event cards in dynamically expanded hour rows on a vertically scrollable timeline, and task-detail navigation.
- [x] 0.2 Update the Android demo so month bars show title only with the first associated tag color, and provide fast period navigation across month, week, and day views.
- [x] 0.3 Add direct date selection for week/day views and compact the work-view header so the week/day timeline receives more viewport height while the date strip and all-day tasks remain pinned.
- [x] 0.4 Update the requirement, API, database, and OpenSpec documents to separate assignee-scoped task bars from actor-scoped events and specify tag-colored day cards.
- [x] 0.5 Update the Android demo so work-view events belong to the actual actor, the Organization Relationships button opens the member tree/profile flow, and day events show tag color with only time, title, and event type.
- [x] 0.6 Align week event cards with day cards: show the same tag-colored time, title, and event type, without summaries, in the docs, API contract, OpenSpec, and Android demo.

## 1. Query and view scope

- [x] 1.1 Implement read-only view queries over estimated-start/original-creation and deadline data, task bars scoped to the assignee, and timestamped create/edit/resubmit, approval, cancellation, progress, completion, and acceptance events scoped to their actual actors.
- [x] 1.2 Enforce task-person scope and manager access to authorized subordinate trees at the server.
- [x] 1.3 Exclude all DailyLog tables, summaries, drafts, and task-log association data from Work View responses.
- [x] 1.4 Update month/week/day task-bar queries to include approved, non-cancelled assigned tasks by date-span overlap regardless of completion or overdue status; keep pending and cancelled tasks excluded.

## 2. Android work views

- [x] 2.1 Build the current-month default, month/week/day switches, Task Center entry, period navigation, return-to-current-period control, month quick-picker, and calendar date selectors for week/day headers.
- [x] 2.2 Build month spans from estimated-start date, falling back to original task creation date, through deadline; use title-only bars colored by the first associated tag, a three-bars-per-date cap, exact date-specific overflow, and task-detail navigation.
- [x] 2.3 Build a compact, responsive week date strip that fits all seven days without horizontal scrolling; pin cross-day all-day task bars above a vertically scrollable 00:00–23:00 timeline. Dynamically expand each hour row to fit complete, wrapped event cards sorted by timestamp while preserving equal row heights across all seven date columns.
- [x] 2.4 Build the matching compact fixed day date strip and the selected date's pinned all-day task bars above a vertically scrollable full 00:00–23:00 timeline; dynamically expand hour rows to fit complete, wrapped event cards without crossing hour boundaries, and do not fabricate future events.
- [x] 2.5 Open task detail from every task bar; enforce read-only details while viewing a subordinate.
- [x] 2.6 Remove subordinate selection from the Views page; enter a member's view from that authorized member's Organization Relationships profile, display the selected member, provide a route back to the profile, and preserve the member across all view modes.
- [x] 2.7 Show period-matching all-day task bars for every selected week/day, clip week bars to their actual start/end columns, and provide calendar date selection for week/day headers.

> Android 2.1–2.6 证据：`:app:testDebugUnitTest` 33 项通过（含 `WorkViewTimeTest`、基于 PR #31 真实响应的 `WorkViewContractTest`）；模拟器 API 37 上 `WorkViewsUiTest` 9/9、`AccountUiTest` 10/10 通过；说明见 `docs/android/work-views-ui.md`。

## 3. Verification

- [ ] 3.1 Test month estimated-start/deadline spans and original-creation fallback, tag colors, title-only bars, month/year quick selection, direct date selection for week/day, date overflow, week/day date strips, cross-day all-day bars, full 24-hour axes, current/past/future task areas, complete event text, dynamic hour-row expansion, and event ordering/boundaries.
- [ ] 3.2 Test assignee-scoped task bars separately from actor-scoped events, including creator/approver activity and acceptance events in the acceptor's view.
- [ ] 3.3 Test entry through Organization Relationships, selected-member preservation across month/week/day, return navigation to the member profile, and denial of out-of-scope access.
- [ ] 3.4 Verify all bars open the correct detail, subordinate details remain read-only, and no view reveals or mutates log/task data.
- [ ] 3.5 Run end-to-end personal and manager-filtered scenarios across month, week, and day.
- [x] 3.6 Verify completed and unfinished tasks, including overdue unfinished tasks, appear when their date spans overlap each selected period; verify pending/cancelled tasks and non-overlapping spans remain absent while historical events retain their existing rules.
- [x] 3.7 Verify all-day task-span matching in past/current/future selections, week-bar start/end columns, calendar confirmation/cancellation and business-time-zone date conversion.

## Follow-up verification (2026-11-05)

- Backend baseline before this requirement update: `python manage.py test --noinput` passed all 91 tests; `makemigrations --check --dry-run` reported no changes. Those work-view regression cases covered inclusive date spans, cross-week clipping, business dates across UTC midnight, the earlier overdue-filter rule, completed month history, and retained actual events. The updated task-bar eligibility and replacement verification are recorded below.
- Android: `testDebugUnitTest assembleDebug` and androidTest compilation passed; 34 unit tests include UTC calendar-picker selection conversion across device time zones and leap-day/year boundaries.
- Android `lintDebug` and `git diff --check` passed.
- Emulator API 37: the 10 other `WorkViewsUiTest` cases passed in the suite run, including Thursday-to-Friday task-bar positioning; the calendar-confirmation/cancellation case passed its targeted run for both week and day modes. All 11 cases passed across these runs.
- Both `month-week-day-work-views` and `task-dispatch-and-acceptance` passed OpenSpec strict validation.

## Follow-up verification (2026-11-06)

- Backend: `python manage.py test apps.tasks.test_work_views --noinput` passed all 6 work-view tests. Coverage verifies completed and overdue tasks appear in overlapping month/week/day periods, while pending, cancelled, and non-overlapping tasks remain absent; real task events keep their existing period and visibility rules.
- `git diff --check` and `npx.cmd -y @fission-ai/openspec@1.14.1 validate month-week-day-work-views --strict` passed.
