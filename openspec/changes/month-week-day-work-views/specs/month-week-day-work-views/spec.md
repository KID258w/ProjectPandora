## ADDED Requirements

### Requirement: Work views are responsibility-scoped and read-only
Month, week, and day views SHALL show tasks for one responsible person. Users SHALL default to their own view; authorized managers MAY select one descendant. Views SHALL be read-only, subordinate details SHALL remain read-only, and no DailyLog data SHALL be shown.

#### Scenario: Manager drills down to a subordinate's views
- **WHEN** a manager selects an authorized subordinate or subordinate of a subordinate and switches between month, week, and day
- **THEN** each view shows only tasks for the selected responsible person, preserves that selected person across view changes, and remains read-only

#### Scenario: User opens a task from any view bar
- **WHEN** a user selects any task bar in a month, week, or day view
- **THEN** the system opens that task's detail and applies the task module's permissions; details for a selected subordinate remain read-only

#### Scenario: View contains no log information
- **WHEN** a user views any month, week, or day
- **THEN** the system displays no log content, summary, draft state, or task-log association

### Requirement: Month view spans task creation through deadline
Month view SHALL draw each task bar from its actual creation date through its deadline. Bars show only the title; color distinguishes other-created assignments from self-created tasks, not status or department. A legend explains colors and bars have no leading dot. Each date shows at most three bars plus an exact “+N项” overflow; bars open task detail and dates/overflow open day view.

#### Scenario: Month view shows creation date and deadline
- **WHEN** a task is created on one date and has a later deadline in the selected month
- **THEN** its bar starts at its actual creation date and ends at its deadline without implying a planned start date

#### Scenario: Month bars show title and ordinary assignment source color only
- **WHEN** a user views tasks in the month calendar
- **THEN** each bar displays its title only, its color distinguishes other-created assignments from self-created tasks according to the legend, and neither text nor color indicates completion status or a special cross-department type

#### Scenario: Month view caps visible bars
- **WHEN** more than three task bars overlap a calendar date
- **THEN** no more than three bars are shown directly and the date displays an exact “+N项” count for the remaining bars

### Requirement: Week and day views use a date strip and full-day time axis
Week view SHALL show a Monday–Sunday date strip, all-day task area, and full 24-hour timeline in that order; seven columns fit without horizontal scrolling. Date strip and all-day area stay fixed while the aligned timeline scrolls vertically. Day view reuses the fixed week strip with the selected date highlighted and its full-day timeline; dates remain selectable.

#### Scenario: User views the current week calendar
- **WHEN** a user opens the current week
- **THEN** the view shows the Monday–Sunday date strip, the current-week all-day unfinished-task bars, and the 00:00–23:00 timeline with date columns

#### Scenario: User changes the viewed day from a date strip
- **WHEN** a user selects a date in a week or day date strip
- **THEN** the selected date opens or becomes the active day view

#### Scenario: Week fits all seven dates and keeps its header fixed
- **WHEN** a user views or vertically scrolls the week calendar on a mobile screen
- **THEN** Monday through Sunday are all visible without horizontal scrolling, the date strip and all-day task area remain fixed, and each timeline event is aligned below its matching date

#### Scenario: Day keeps its date strip and all-day task area fixed
- **WHEN** a user vertically scrolls the selected day's timeline
- **THEN** the compact seven-day strip and today's all-day task area remain fixed and the selected date's events scroll only vertically

### Requirement: Work-view periods support direct date selection
The month period header SHALL open a quick year/month selector. The week and day period headers SHALL open a calendar that allows directly selecting a specific date. Selecting a date in week mode SHALL show the week containing that date and highlight the selected date; selecting a date in day mode SHALL show that exact date. The week and day date strips SHALL remain directly selectable as already specified above.

#### Scenario: User jumps to a month in month mode
- **WHEN** a user opens the quick year/month selector from month mode and chooses a year and month
- **THEN** the month view navigates to the selected month

#### Scenario: User directly selects a date in week or day mode
- **WHEN** a user opens the calendar from week/day mode and taps a specific date
- **THEN** week mode jumps to the containing week and highlights that date, while day mode opens the exact selected date

### Requirement: Work view minimizes redundant headers
The work-view page SHALL omit its redundant brand masthead and large page title. It SHALL keep object selection and Task Center access in a compact control row and reserve the remaining height for the schedule board. In week/day mode the date strip and all-day tasks SHALL remain pinned, and the timeline SHALL occupy the remaining viewport and scroll vertically. Other main-tab pages MAY retain their own branding and page headings.

#### Scenario: Work view prioritizes the timeline viewport
- **WHEN** a user opens week or day view on a phone
- **THEN** redundant brand and page-title headers are omitted, essential controls remain compact, the date strip and all-day task bars stay fixed, and the timeline receives the remaining visible height for vertical scrolling

### Requirement: Week and day views show timestamped task events as bars
Week/day views SHALL place actual task updates, completion submissions, and acceptance completion at their recorded local hour. Cards show time, title, type, and full summary; they wrap and hour rows expand as needed without overlap or misalignment. Events belong to the task assignee's view, not the actor's. Approval, return, cancellation, and ordinary task edits are excluded.

#### Scenario: Responsible person adds a task update
- **WHEN** the responsible person appends a textual progress update
- **THEN** the person's week and day views show a timestamped card with task title, “任务更新”, and the complete update text, wrapping without truncation as needed

#### Scenario: Dispatcher accepts a subordinate's task
- **WHEN** a dispatcher accepts a task owned by a subordinate
- **THEN** the acceptance-completion bar appears in that subordinate's view and is absent from the dispatcher's own view unless the dispatcher owns the task

#### Scenario: Responsible person is also task creator
- **WHEN** the responsible person marks their own created task complete
- **THEN** the view shows one combined “提交并完成” event rather than separate submission and acceptance events

#### Scenario: Crowded hour expands without clipping events
- **WHEN** multiple events with long titles or summaries occur within the same hour
- **THEN** the hour row expands to contain every full event card, cards remain ordered by exact time and do not cross into an adjacent hour, and all week date columns retain the same row height

#### Scenario: Day timeline expands for its event details
- **WHEN** multiple events or a long update occur within one hour of the selected day
- **THEN** the day hour row expands to show every full event card and its complete text without clipping or crossing the neighboring hour boundary

### Requirement: Current unfinished tasks appear in the all-day area
The current week SHALL show the selected person's active unfinished tasks as clickable “全天” bars from Monday through the deadline column, or through Sunday when due outside the week. Bar length only cues whether the deadline is in this week; it does not show duration. Today shows full-width bars. Past/future periods omit this area, and future events are not fabricated.

#### Scenario: Current week shows cross-day all-day task bars
- **WHEN** the selected responsible person has active unfinished tasks and views the current week
- **THEN** the “全天” area shows clickable task bars that can extend across multiple date columns; their length cues whether the deadline is in this week but does not represent task duration

#### Scenario: Today shows all-day task bars
- **WHEN** the selected responsible person has active unfinished tasks and views today
- **THEN** the “全天” area shows clickable task bars without extra status, deadline, or summary text above the complete 24-hour timeline

#### Scenario: Historical or future period omits the unfinished area
- **WHEN** the selected period is not the current week or today
- **THEN** the relevant unfinished-task area is omitted; only actual events are displayed and future events are not fabricated
