## ADDED Requirements

### Requirement: Work views are responsibility-scoped and read-only
Month, week, and day views SHALL show task bars for one responsible person and task dynamics for one action actor. Users SHALL default to their own view. To view a descendant, an authorized manager SHALL open that member's profile through Organization Relationships and use its “查看视图” action; the Views page SHALL NOT provide a separate subordinate selector. Views SHALL be read-only, subordinate details SHALL remain read-only, and no DailyLog data SHALL be shown.

#### Scenario: Manager drills down to a subordinate's views
- **WHEN** a manager opens an authorized subordinate's profile through Organization Relationships, taps “查看视图”, and switches between month, week, and day
- **THEN** each view shows only tasks for that responsible person, preserves that person across view changes, provides a route back to their profile, and remains read-only

#### Scenario: Views do not provide a subordinate selector
- **WHEN** a manager is viewing their own or an authorized subordinate's work view
- **THEN** the page has no independent control for selecting another subordinate; another member can be selected only through Organization Relationships and the member profile's permission-aware action

#### Scenario: User opens a task from any view bar
- **WHEN** a user selects any task bar in a month, week, or day view
- **THEN** the system opens that task's detail and applies the task module's permissions; details for a selected subordinate remain read-only

#### Scenario: View contains no log information
- **WHEN** a user views any month, week, or day
- **THEN** the system displays no log content, summary, draft state, or task-log association

### Requirement: Month task bars use planned start or original creation date
For each approved task, month view SHALL start the bar on the business-local date of `estimated_start_at` when present, otherwise on the business-local date of original `created_at`, and end it on the deadline. The fallback is for display only and SHALL NOT imply actual work started. Approval and resubmission SHALL preserve the original creation time.

#### Scenario: Month view uses the estimated start date
- **WHEN** an approved task has an estimated start and a later deadline in the selected month
- **THEN** its bar starts on the estimated-start date and ends on its deadline without implying actual execution

#### Scenario: Month view falls back to original creation date
- **WHEN** an approved task has no estimated start and has been returned and resubmitted before approval
- **THEN** its bar starts on the task's original creation date and ends on its deadline, regardless of the later approval date

### Requirement: Month task bars show title and tag color
Month task bars SHALL show only the task title and use the first associated tag's color, or neutral gray when no tag is associated. Approved, non-cancelled tasks SHALL be included when their inclusive date spans overlap the selected month, regardless of completion or overdue status. Each date SHALL show at most three bars plus an exact “+N项” overflow. Bars SHALL open task detail; dates and overflow SHALL open day view.

#### Scenario: Month bars show title and tag color only
- **WHEN** a user views tasks in the month calendar
- **THEN** each bar displays its title only and uses the first associated tag's color, with neutral gray for tasks without tags; color does not indicate completion status or assignment source

#### Scenario: Month view caps visible bars
- **WHEN** more than three task bars overlap a calendar date
- **THEN** no more than three bars are shown directly and the date displays an exact “+N项” count for the remaining bars

### Requirement: Week and day views use a date strip and full-day time axis
Week view SHALL show a Monday–Sunday date strip, all-day task area, and full 24-hour timeline in that order; seven columns fit without horizontal scrolling. Date strip and all-day area stay fixed while the aligned timeline scrolls vertically. Day view reuses the fixed week strip with the selected date highlighted and its full-day timeline; dates remain selectable.

#### Scenario: User views any week calendar
- **WHEN** a user opens a past, current, or future week
- **THEN** the view shows the Monday–Sunday date strip, the selected person's approved, non-cancelled task bars whose date spans overlap that week regardless of completion or overdue status, and the 00:00–23:00 timeline with date columns

#### Scenario: User changes the viewed day from a date strip
- **WHEN** a user selects a date in a week or day date strip
- **THEN** the selected date opens or becomes the active day view

#### Scenario: Week fits all seven dates and keeps its header fixed
- **WHEN** a user views or vertically scrolls the week calendar on a mobile screen
- **THEN** Monday through Sunday are all visible without horizontal scrolling, the date strip and all-day task area remain fixed, and each timeline event is aligned below its matching date

#### Scenario: Day keeps its date strip and all-day task area fixed
- **WHEN** a user vertically scrolls the selected day's timeline
- **THEN** the compact seven-day strip and the selected date's all-day task area remain fixed and the selected date's events scroll only vertically

### Requirement: Work-view periods support direct date selection
The month header SHALL open a year/month selector. Week/day headers SHALL open a calendar with year/month navigation and selectable dates. Confirmation SHALL select that date's week or exact day and highlight the chosen date. Cancellation SHALL leave the date unchanged. Date strips SHALL remain selectable.

#### Scenario: User jumps to a month in month mode
- **WHEN** a user opens the quick year/month selector from month mode and chooses a year and month
- **THEN** the month view navigates to the selected month

#### Scenario: User directly selects a date in week or day mode
- **WHEN** a user opens the calendar from week/day mode, selects a date, and confirms
- **THEN** week mode jumps to the containing week and highlights that date, while day mode opens the exact selected date

### Requirement: Calendar selection preserves the business date
The calendar SHALL offer valid dates for each displayed month. Its selected year, month, and day SHALL be converted to the business-time-zone date without using the device's local date interpretation of the picker's UTC timestamp.

#### Scenario: Calendar selection is independent of the device time zone
- **WHEN** a user selects a date such as February 29 in a leap year while the device is in another time zone
- **THEN** the work-view request uses that exact calendar date in the business time zone

### Requirement: Work view has compact controls without a subordinate selector
The work-view page SHALL omit its redundant brand masthead and large page title. It SHALL show the selected member and provide compact Task Center and Organization Relationships buttons at the top right. It SHALL NOT provide a subordinate selector; another member can be selected only through the authorized member profile in Organization Relationships.

#### Scenario: Work view prioritizes the timeline viewport
- **WHEN** a user opens week or day view on a phone
- **THEN** redundant brand and page-title headers are omitted, and the selected member and two navigation buttons remain in a compact row

### Requirement: Week and day dynamics belong to the action actor
Week/day views SHALL place task creation/submission, edits/resubmission, approval decisions, cancellation, progress updates, completion submissions, and acceptance decisions at their recorded local time. Each event SHALL appear in the view of the member who performed that action. Pending tasks SHALL NOT appear as task bars, but creation and approval events MAY appear in the views of their respective actors.

#### Scenario: A member's task actions appear in that member's view
- **WHEN** a member creates or edits a task, submits or resubmits it for approval, approves or returns a task, cancels a task, adds progress, submits completion, or accepts/returns completion
- **THEN** the event appears in the view of the member who performed the action, at its actual recorded time, and does not appear in another member's view merely because that member is assigned the task

#### Scenario: Pending task actions appear without creating task bars
- **WHEN** a task is awaiting approval
- **THEN** its creation/submission event may appear in the creator's view and the approval event may appear in the approver's view, while the task itself remains absent from all task-bar areas until approved

### Requirement: Week and day event cards use the same compact information
Week and day event cards SHALL use the task's first associated tag color, or neutral gray when no tag exists. Both SHALL show only time, task title, and event type, without a summary.

#### Scenario: Day events show their compact fields with tag color
- **WHEN** a user opens a day containing task events
- **THEN** each event card uses the task's first associated tag color (neutral gray when there is no tag) and displays only the time, task title, and event type

#### Scenario: Week events use the same compact fields
- **WHEN** a user opens a week containing task events
- **THEN** each event card uses the task's first associated tag color and displays only the time, task title, and event type

#### Scenario: Responsible person is also task creator
- **WHEN** the responsible person marks their own created task complete
- **THEN** the view shows one combined “提交并完成” event rather than separate submission and acceptance events

#### Scenario: Crowded hour expands without clipping events
- **WHEN** multiple events with long titles occur within the same hour
- **THEN** the hour row expands to contain every event card at its mode-specific information density, cards remain ordered by exact time and do not cross into an adjacent hour, and all week date columns retain the same row height

#### Scenario: Day timeline expands for its event cards
- **WHEN** multiple events occur within one hour of the selected day
- **THEN** the day hour row expands to show every compact event card without clipping or crossing the neighboring hour boundary

### Requirement: Task bars are selected by date span regardless of completion or overdue status
Month, week, and day views SHALL include the selected person's approved, non-cancelled tasks whose inclusive business-local date spans overlap the selected period. Completion and overdue status SHALL NOT independently filter task bars. Pending and cancelled tasks SHALL remain absent. Events SHALL remain limited to the selected period; future events SHALL NOT be fabricated.

#### Scenario: Completed and unfinished tasks appear when dates overlap
- **WHEN** approved, non-cancelled tasks with completed and unfinished statuses have date spans overlapping the selected month, week, or day
- **THEN** both tasks appear in that view, while tasks whose date spans do not overlap remain absent

#### Scenario: Overdue status does not hide an overlapping task
- **WHEN** an approved, non-cancelled unfinished task is overdue but its date span overlaps the selected month, week, or day
- **THEN** its task bar remains visible for the overlapping dates

#### Scenario: Historical or future period uses its own task spans
- **WHEN** the selected period is a past or future week or date
- **THEN** approved, non-cancelled tasks whose date spans overlap that selected period appear regardless of completion or overdue status, actual events are limited to the selected period, and future events are not fabricated

#### Scenario: Day task bars appear only inside their inclusive task span
- **WHEN** a task starts on November 10 and has a November 12 deadline
- **THEN** its all-day bar appears on November 10, 11, and 12 and does not appear on November 9 or 13, provided it is approved and not cancelled

### Requirement: All-day task bars use the selected period for their span
Week bars SHALL use the intersection of the task's date span and the selected Monday–Sunday week. Their left position SHALL match the clipped start date and their width SHALL end at the clipped deadline date. Non-overlapping tasks SHALL be omitted. Eligible day bars SHALL span the selected day's full width. The date span SHALL NOT imply actual execution time or work hours.

#### Scenario: Any selected week shows cross-day all-day task bars
- **WHEN** an eligible task starts on Thursday and ends on Friday in the selected week
- **THEN** its clickable all-day bar starts in Thursday's column and ends in Friday's column, leaving Monday through Wednesday unoccupied by that task

#### Scenario: Cross-week task bars are clipped at week boundaries
- **WHEN** an eligible task's date span overlaps more than one week
- **THEN** each selected week shows only the intersecting portion, and weeks before the start or after the deadline omit that task

#### Scenario: Any selected day shows all-day task bars
- **WHEN** the selected responsible person views a date covered by an approved, non-cancelled task's date span
- **THEN** the “全天” area shows clickable task bars without extra status, deadline, or summary text above the complete 24-hour timeline
