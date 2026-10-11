## ADDED Requirements

### Requirement: Work views are responsibility-scoped and read-only
The system SHALL provide month, week, and day views of formal tasks for the selected responsible person. Every user SHALL default to their own view. Authorized managers SHALL be able to select a subordinate or any permitted lower-level subordinate and view that person's tasks without combining other people's tasks into the selected view. The view surface SHALL NOT mutate tasks. Every task bar in month, week, unfinished-task, and event areas SHALL open its task detail; details reached through a subordinate view SHALL be read-only. Work views SHALL NOT reveal DailyLog data.

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
The system SHALL position a task bar from the task's actual creation date to its deadline. The creation-date position SHALL NOT imply that work started on that date. Each bar SHALL display only task title and binary completion status (“未完成” or “已完成”), with a clearly legible status label and no leading dot. A date SHALL show at most three task bars and an exact date-specific “+N项” overflow indicator; selecting a bar opens task detail and selecting the date or overflow opens that date's day view.

#### Scenario: Month view shows creation date and deadline
- **WHEN** a task is created on one date and has a later deadline in the selected month
- **THEN** its bar starts at its actual creation date and ends at its deadline without implying a planned start date

#### Scenario: Month view caps visible bars
- **WHEN** more than three task bars overlap a calendar date
- **THEN** no more than three bars are shown directly and the date displays an exact “+N项” count for the remaining bars

### Requirement: Week and day views use a date strip and full-day time axis
The week view SHALL display a compact date strip for Monday through Sunday, an all-day task area, and a complete 24-hour timeline in that order. All seven date columns SHALL fit simultaneously within the available mobile viewport without horizontal scrolling. The date strip and all-day task area SHALL remain fixed above the timeline; only the timeline SHALL scroll vertically. The date strip, all-day task bars, and event columns SHALL share the same seven-column widths and spacing so each event aligns with its corresponding date. The timeline SHALL use dates as horizontal columns and 00:00–23:00 as vertical hour marks. Selecting a date from the week strip SHALL open that date in day view.

The day view SHALL use the same fixed compact seven-day strip for the selected date's week with the selected day highlighted, an all-day task area when viewing today, and the selected day's complete 24-hour timeline. The seven dates SHALL fit without horizontal scrolling; the date strip and all-day task area SHALL remain fixed while only the selected day's timeline scrolls vertically. Selecting another date in the strip SHALL change the day being viewed.

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

### Requirement: Week and day views show timestamped task events as bars
The system SHALL display actual task-operation events in the hour row corresponding to their recorded local date and hour; each event card represents a point in time, not a duration. Within each hour row, cards SHALL be ordered by their full recorded local timestamp and SHALL show the exact event time, task title, event type, and complete summary without truncation; text SHALL wrap as needed and card height SHALL grow to fit. Each hour row SHALL have a baseline height and SHALL expand to fit all cards in that hour, using the greatest stacked-card height required by any date column in that hour; all date columns in the same row SHALL remain aligned at that expanded height. No event card SHALL cross its hour-row boundary. Event types SHALL be limited to responsible-person task update, completion submission, acceptance completion, and the combined “提交并完成” event when the responsible person is also the creator. Approval, return, cancellation, and ordinary task-field edits SHALL NOT appear as view events.

Events SHALL be scoped to the responsible person's task view, not to the event actor's view. An acceptance by an upper-level task dispatcher SHALL appear in the responsible person's view and SHALL NOT appear in the dispatcher's own view unless the dispatcher is also the responsible person.

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
The current week SHALL show all active unfinished tasks for the selected responsible person as clickable task bars in an “全天” area above the timeline. Week bars SHALL start at the first day of the week and extend to the deadline's day column when the deadline falls within the week; tasks whose deadline is outside the week SHALL extend to the last day column. This length SHALL only cue whether the deadline falls within the current week, and SHALL NOT communicate creation date, start date, or actual task duration. Bars SHALL show the task title only.

Today SHALL show that person's active unfinished tasks as full-width task bars in the day view's “全天” area, without status, deadline, or summary text. Past/future weeks and past/future days SHALL NOT show these current unfinished-task areas. Future dates SHALL NOT fabricate task-operation events.

#### Scenario: Current week shows cross-day all-day task bars
- **WHEN** the selected responsible person has active unfinished tasks and views the current week
- **THEN** the “全天” area shows clickable task bars that can extend across multiple date columns; their length cues whether the deadline is in this week but does not represent task duration

#### Scenario: Today shows all-day task bars
- **WHEN** the selected responsible person has active unfinished tasks and views today
- **THEN** the “全天” area shows clickable task bars without extra status, deadline, or summary text above the complete 24-hour timeline

#### Scenario: Historical or future period omits the unfinished area
- **WHEN** the selected period is not the current week or today
- **THEN** the relevant unfinished-task area is omitted; only actual events are displayed and future events are not fabricated

### Requirement: Historical completion state freezes at natural-day end
The system SHALL derive a historical day's binary completion state from events through the end of that natural day in the configured business time zone. Later task events SHALL NOT rewrite that historical state.

#### Scenario: Task completes after a historical day
- **WHEN** a task is completed after a previously viewed day ended
- **THEN** that past day retains its end-of-day completion state while the completion event appears on its actual date
