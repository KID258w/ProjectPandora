## 0. Android demo prototype

- [x] 0.1 Update the Android demo for month spans, a compact seven-day strip without horizontal scrolling, fixed date/all-day areas, event cards in dynamically expanded hour rows on a vertically scrollable timeline, and task-detail navigation.
- [x] 0.2 Update the Android demo so month bars show title only with the first associated tag color, and provide fast period navigation across month, week, and day views.
- [x] 0.3 Add direct date selection for week/day views and compact the work-view header so the week/day timeline receives more viewport height while the date strip and all-day tasks remain pinned.
- [x] 0.4 Update the requirement, API, database, and OpenSpec documents to separate assignee-scoped task bars from actor-scoped events and specify tag-colored day cards.
- [x] 0.5 Update the Android demo so work-view events belong to the actual actor, the Organization Relationships button opens the member tree/profile flow, and day events show tag color with only time, title, and event type.
- [x] 0.6 Align week event cards with day cards: show the same tag-colored time, title, and event type, without summaries, in the docs, API contract, OpenSpec, and Android demo.

## 1. Query and view scope

- [ ] 1.1 Implement read-only view queries over estimated-start/original-creation and deadline data, task bars scoped to the assignee, and timestamped create/edit/resubmit, approval, cancellation, progress, completion, and acceptance events scoped to their actual actors.
- [ ] 1.2 Enforce task-person scope and manager access to authorized subordinate trees at the server.
- [ ] 1.3 Exclude all DailyLog tables, summaries, drafts, and task-log association data from Work View responses.

## 2. Android work views

- [x] 2.1 Build the current-month default, month/week/day switches, Task Center entry, period navigation, return-to-current-period control, month quick-picker, and direct date picker for week/day headers.
- [x] 2.2 Build month spans from estimated-start date, falling back to original task creation date, through deadline; use title-only bars colored by the first associated tag, a three-bars-per-date cap, exact date-specific overflow, and task-detail navigation.
- [x] 2.3 Build a compact, responsive week date strip that fits all seven days without horizontal scrolling; pin it and cross-day all-day unfinished task bars above a vertically scrollable 00:00–23:00 timeline. Dynamically expand each hour row to fit complete, wrapped event cards sorted by timestamp while preserving equal row heights across all seven date columns.
- [x] 2.4 Build the matching compact fixed day date strip and today's pinned all-day unfinished task bars above a vertically scrollable full 00:00–23:00 timeline; dynamically expand hour rows to fit complete, wrapped event cards without crossing hour boundaries, and do not fabricate future events.
- [x] 2.5 Open task detail from every task bar; enforce read-only details while viewing a subordinate.
- [x] 2.6 Remove subordinate selection from the Views page; enter a member's view from that authorized member's Organization Relationships profile, display the selected member, provide a route back to the profile, and preserve the member across all view modes.

> Android 2.1–2.6 证据：`:app:testDebugUnitTest` 33 项通过（含 `WorkViewTimeTest`、基于 PR #31 真实响应的 `WorkViewContractTest`）；模拟器 API 37 上 `WorkViewsUiTest` 9/9、`AccountUiTest` 10/10 通过；说明见 `docs/android/work-views-ui.md`。

## 3. Verification

- [ ] 3.1 Test month estimated-start/deadline spans and original-creation fallback, tag colors, title-only bars, month/year quick selection, direct date selection for week/day, date overflow, week/day date strips, cross-day all-day bars, full 24-hour axes, current/past/future unfinished areas, complete event text, dynamic hour-row expansion, and event ordering/boundaries.
- [ ] 3.2 Test assignee-scoped task bars separately from actor-scoped events, including creator/approver activity and acceptance events in the acceptor's view.
- [ ] 3.3 Test entry through Organization Relationships, selected-member preservation across month/week/day, return navigation to the member profile, and denial of out-of-scope access.
- [ ] 3.4 Verify all bars open the correct detail, subordinate details remain read-only, and no view reveals or mutates log/task data.
- [ ] 3.5 Run end-to-end personal and manager-filtered scenarios across month, week, and day.
