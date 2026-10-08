## ADDED Requirements

### Requirement: Work views are read-only task projections
The system SHALL provide month, week, and day work views derived from the selected user's formal tasks, progress history, and status history. The view SHALL NOT display logs, log summaries, drafts, or log associations and SHALL NOT offer task editing, progress mutation, acceptance, or task-detail navigation.

#### Scenario: User opens the Work View tab
- **WHEN** a user enters the Work View tab
- **THEN** the system opens the current week by default and allows switching among month, week, and day views

#### Scenario: View contains no log content
- **WHEN** a user views any month, week, or day
- **THEN** the system displays task information only and provides no log content or log-related markers

#### Scenario: User attempts a write action from a view
- **WHEN** a user attempts to edit a task, progress, or acceptance from a work view
- **THEN** the system provides no such operation and leaves task data unchanged

### Requirement: Month view shows task schedule spans
The system SHALL show the selected month's task start/end spans, deadlines, overdue state, and priority markers without repeating a long-running task as a separate daily task. A date selection SHALL open its day view.

#### Scenario: User views a task spanning multiple dates
- **WHEN** a task has a start date and a later deadline in the selected month
- **THEN** the system displays one continuous task span with its deadline marker

#### Scenario: Task has only a deadline
- **WHEN** a task has no start date and has a deadline in the selected month
- **THEN** the system displays it only on its deadline date

### Requirement: Week and day views show task changes and current unfinished work
The system SHALL show actual task progress/status changes on the dates they occurred. The current week SHALL include a separate unfinished-task area for unfinished tasks already started, overdue, or planned to start that week. Today SHALL include an unfinished-task area. Past weeks/days and future periods SHALL NOT show these current unfinished areas; future dates show planned tasks only.

#### Scenario: Current week has unfinished tasks
- **WHEN** a user views the current week with eligible unfinished tasks
- **THEN** the system lists each task once in the unfinished area and shows daily progress/status events only on dates they occurred

#### Scenario: Past week is viewed
- **WHEN** a user views a past week
- **THEN** the system omits the unfinished-task area and shows daily events that occurred in that week

#### Scenario: No task event occurred on a day
- **WHEN** a week contains a day with no task progress or status change
- **THEN** the system shows the date without an empty progress section or repeated unchanged tasks

#### Scenario: User views today or a future day
- **WHEN** the selected day is today or in the future
- **THEN** today shows its unfinished-task area and future days show only planned tasks without fabricated progress events

### Requirement: Managers filter only within authorized subordinate scope
The system SHALL default every user to their own work view and SHALL allow a manager to select only a direct or indirect subordinate within their authorized organization scope. The selected person and organization path SHALL be visible; clearing the filter SHALL return to the manager's own view.

#### Scenario: Manager selects an in-scope subordinate
- **WHEN** a manager selects an authorized subordinate
- **THEN** the same month/week/day view displays only that person's tasks while preserving current period and view selection

#### Scenario: Manager selects an out-of-scope user
- **WHEN** a manager requests another organization's user's work view
- **THEN** the system denies the request and returns no task data

### Requirement: Historical task state freezes at natural-day end
The system SHALL calculate each past date's task state from events recorded by the end of that natural day. Later progress or status changes SHALL NOT rewrite the historical day's displayed state.

#### Scenario: Task changes after a past day ended
- **WHEN** a task changes after the end of a previously selected day
- **THEN** the past day continues to show the state and events as of its own end
