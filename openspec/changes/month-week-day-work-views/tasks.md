## 0. Android demo prototype

- [x] 0.1 Update the Android demo for month spans, a compact seven-day strip without horizontal scrolling, fixed date/all-day areas, complete wrapping event cards in dynamically expanded hour rows on a vertically scrollable timeline, owner-scoped events, and task-detail navigation.
- [x] 0.2 Update the Android demo so month bars show title only with source-category colors, and provide fast period navigation across month, week, and day views.
- [x] 0.3 Add direct date selection for week/day views and compact the work-view header so the week/day timeline receives more viewport height while the date strip and all-day tasks remain pinned.

## 1. Query and view scope

- [ ] 1.1 Implement read-only view queries over task creation/deadline data, binary completion state, responsible-person text updates, completion submissions, and acceptance events.
- [ ] 1.2 Enforce task-person scope and manager access to authorized subordinate trees at the server.
- [ ] 1.3 Exclude all DailyLog tables, summaries, drafts, and task-log association data from Work View responses.

## 2. Android work views

- [ ] 2.1 Build the current-month default, month/week/day switches, Task Center entry, period navigation, return-to-current-period control, month quick-picker, and direct date picker for week/day headers.
- [ ] 2.2 Build month spans from task creation date to deadline, with title-only bars colored by task source and a visible legend, a three-bars-per-date cap, exact date-specific overflow, and task-detail navigation.
- [ ] 2.3 Build a compact, responsive week date strip that fits all seven days without horizontal scrolling; pin it and cross-day all-day unfinished task bars above a vertically scrollable 00:00–23:00 timeline. Dynamically expand each hour row to fit complete, wrapped event cards sorted by timestamp while preserving equal row heights across all seven date columns.
- [ ] 2.4 Build the matching compact fixed day date strip and today's pinned all-day unfinished task bars above a vertically scrollable full 00:00–23:00 timeline; dynamically expand hour rows to fit complete, wrapped event cards without crossing hour boundaries, and do not fabricate future events.
- [ ] 2.5 Open task detail from every task bar; enforce read-only details while viewing a subordinate.
- [ ] 2.6 Build manager object selection for all authorized subordinate levels and preserve the selection across all view modes.

## 3. Verification

- [ ] 3.1 Test month creation/deadline spans, source-color legend, title-only bars, month/year quick selection, direct date selection for week/day, date overflow, week/day date strips, cross-day all-day bars, full 24-hour axes, current/past/future unfinished areas, complete event text, dynamic hour-row expansion, and event ordering/boundaries.
- [ ] 3.2 Test owner-based event visibility, including acceptance events in the responsible person's view but not the dispatcher's own view.
- [ ] 3.3 Test manager organization-tree scope, selected-object preservation, and denial of out-of-scope access.
- [ ] 3.4 Verify all bars open the correct detail, subordinate details remain read-only, and no view reveals or mutates log/task data.
- [ ] 3.5 Run end-to-end personal and manager-filtered scenarios across month, week, and day.
