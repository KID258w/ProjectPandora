## ADDED Requirements

### Requirement: Home shows four role-appropriate panels
The system SHALL provide four mobile home panels: Company Important Items, Company Top Ten Tasks, Personal Top Ten Work, and Recent Ten Logs. Each panel SHALL use its source module's authorized data; personal task and log panels SHALL contain only the current user's records.

#### Scenario: User opens the home page
- **WHEN** an authenticated mobile business user opens Home
- **THEN** the system displays the four panels using company-wide published items and that user's task and submitted-log data

#### Scenario: Manager views Home
- **WHEN** a Founder, Department Manager, or Team Leader opens Home
- **THEN** the task and log panels show the manager's own records rather than replacing them with subordinate records

### Requirement: Company Top Ten Tasks is generated per user
The system SHALL select up to ten unfinished tasks that the current user personally owns or executes, ordered by source category (upstream-assigned, current-level/self-created, cross-department) and within each category by earliest deadline then earliest creation time. Completed and cancelled tasks SHALL be excluded. This panel SHALL NOT be user-editable and SHALL NOT show progress percentages.

#### Scenario: User has tasks from several sources
- **WHEN** the system builds the Company Top Ten Tasks panel
- **THEN** it combines the current user's eligible tasks in source-category order, applies the within-category deadline/creation ordering, and returns at most ten

#### Scenario: User only dispatches a child task
- **WHEN** a manager has dispatched a task to a subordinate but is not responsible for executing it
- **THEN** that task is not included in the manager's personal Company Top Ten Tasks panel

#### Scenario: Task is completed or cancelled
- **WHEN** a listed task becomes completed or cancelled
- **THEN** it is excluded from the generated panel on the next refresh

### Requirement: Personal Top Ten Work is independently selected by the user
The system SHALL allow the user to select and order up to ten unfinished tasks from their own task list. On first use, the system SHALL prefill the list from the generated Company Top Ten Tasks; afterward it SHALL preserve user selection/order independently of that generated list. Completed, cancelled, or no-longer-owned tasks SHALL be removed.

#### Scenario: User first opens Personal Top Ten Work
- **WHEN** no personal ordering has been initialized for the user
- **THEN** the system initializes it from eligible tasks in the generated Company Top Ten Tasks panel

#### Scenario: User selects and reorders personal tasks
- **WHEN** the user selects eligible own tasks and saves a new order of no more than ten
- **THEN** the system stores that selection/order for this user only without changing task records or the company panel order

#### Scenario: A personal task becomes ineligible
- **WHEN** a selected task is completed, cancelled, or removed from the user's task list
- **THEN** the system removes it from the personal panel and preserves the relative order of remaining selections

### Requirement: Home displays recent submitted-log summaries
The system SHALL show up to ten of the current user's submitted log summaries ordered by log date descending. Drafts and dates without a log SHALL NOT be shown. The home panel SHALL not replace the dedicated log editor/history.

#### Scenario: User has recent submitted logs
- **WHEN** the home page loads the Recent Logs panel
- **THEN** it displays up to ten submitted logs with their date and summary, newest first

#### Scenario: User has fewer than ten submitted logs
- **WHEN** fewer than ten submitted logs exist
- **THEN** the panel displays only existing submitted logs and does not insert blank dates or drafts

### Requirement: Home panels provide summaries, not full workflows
The system SHALL show concise summaries and provide navigation to the source module/detail. It SHALL NOT perform full task execution, acceptance, log editing, or company-item administration within Home.

#### Scenario: User selects a panel item
- **WHEN** the user taps a task, log, or company important item summary
- **THEN** the system opens the corresponding authorized detail or module without modifying the record from Home
