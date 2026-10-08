## 1. Query and historical state

- [ ] 1.1 Implement read-only view queries over task schedules, progress events, and status events for a selected user/date range.
- [ ] 1.2 Enforce task-person scope and manager subordinate-tree authorization server-side.
- [ ] 1.3 Reconstruct historical day-end task state from event timestamps using the configured business time zone.
- [ ] 1.4 Exclude all DailyLog tables, summaries, and task-log association data from Work View query responses.

## 2. Android work views

- [ ] 2.1 Build Work View tab with current-week default, month/week/day switches, previous/next navigation, and return-to-current-period control.
- [ ] 2.2 Build month task spans, deadlines, overdue/priority markers, and date-to-day navigation.
- [ ] 2.3 Build week daily-event list and current-week unfinished-task area without repeating unchanged tasks.
- [ ] 2.4 Build day task/event view with today-only unfinished-task area and future planned-task rendering.
- [ ] 2.5 Build manager object filter with visible selected object/path, clear-filter action, and preserved period/view selection.

## 3. Verification

- [ ] 3.1 Test current/past/future period rules, no-event days, task spans, deadline-only tasks, and day-end freezing.
- [ ] 3.2 Test manager organization-scope filtering and out-of-scope API denial.
- [ ] 3.3 Verify no view screen or API reveals log data or provides task mutation/detail navigation.
- [ ] 3.4 Run end-to-end personal and manager-filtered view scenarios across month, week, and day.
