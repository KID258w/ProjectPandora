## ADDED Requirements

### Requirement: Home shows four role-appropriate panels
The system SHALL provide four mobile home panels: Company Important Items, Company Top Ten Tasks, Personal Top Ten Work, and Recent Logs. Each Home card SHALL preview no more than five rows and use its source module's authorized data; personal task and log cards SHALL contain only the current user's records. Company-item and Top Ten secondary lists SHALL continue to show up to ten items; the Home log preview remains limited to five.

#### Scenario: User opens the home page
- **WHEN** an authenticated mobile business user opens Home
- **THEN** the system displays the four panels using company-wide published items and that user's task and submitted-log data

#### Scenario: Home cards preview at most five rows
- **WHEN** a user has more than five eligible rows for a Home panel
- **THEN** that card displays only the first five in its defined order and provides an entry to the corresponding secondary view where applicable

### Requirement: Company important items open a full secondary list
The system SHALL show at most five company-important-item summaries on Home. Its secondary list SHALL show up to ten published items in administrator-defined order with their complete item information.

#### Scenario: User opens all company-important items
- **WHEN** the user selects “View all” from the Home company-items card
- **THEN** the system opens the secondary list and displays up to ten published items with complete information

#### Scenario: Manager views Home
- **WHEN** a Founder, Department Manager, or Team Leader opens Home
- **THEN** the task and log panels show the manager's own records rather than replacing them with subordinate records

### Requirement: Company Top Ten Tasks is generated per user
The system SHALL select up to ten unfinished tasks that the current user personally owns or executes. Task completion status SHALL be limited to “未完成” and “已完成”; start, progress, acceptance-submission, return, and cancellation are represented by separate process records/metadata. The panel SHALL order tasks first by source category (upstream-assigned, current-level/self-created, cross-department), then by earliest deadline and earliest creation time. Completed and cancelled/archived tasks SHALL be excluded. This panel SHALL NOT be user-editable and SHALL NOT show progress percentages.

#### Scenario: User has tasks from several sources
- **WHEN** the system builds the Company Top Ten Tasks panel
- **THEN** it combines the current user's eligible unfinished tasks in source-category order, applies deadline/creation ordering within each source, and returns at most ten

#### Scenario: User opens the Company Top Ten secondary page
- **WHEN** the user selects “View all” from the Home Company Top Ten card
- **THEN** the system shows up to ten eligible tasks in the generated order, and selecting a task opens that task's detail

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
- **WHEN** the user selects eligible own tasks, long-presses the dedicated drag handle to reorder them, and saves a list of no more than ten
- **THEN** the system stores that selection/order for this user only without changing task records or the company panel order; ordinary taps do not reorder items

#### Scenario: User opens a selected personal task
- **WHEN** the user opens the Personal Top Ten secondary page and taps a task
- **THEN** the system opens the original task detail

#### Scenario: A personal task becomes ineligible
- **WHEN** a selected task is completed, cancelled, or removed from the user's task list
- **THEN** the system removes it from the personal panel and preserves the relative order of remaining selections

### Requirement: Home displays recent submitted-log summaries
The system SHALL show up to five of the current user's submitted log summaries on Home ordered by log date descending. Drafts and dates without a log SHALL NOT be shown. The home panel SHALL not replace the dedicated log editor/history.

#### Scenario: User has recent submitted logs
- **WHEN** the home page loads the Recent Logs panel
- **THEN** it displays up to five submitted logs with their date and summary, newest first

#### Scenario: User has fewer than five submitted logs
- **WHEN** fewer than five submitted logs exist
- **THEN** the panel displays only existing submitted logs and does not insert blank dates or drafts

### Requirement: Home panels provide summaries, not full workflows
The system SHALL show concise summaries and provide navigation to the source module/detail. It SHALL NOT perform full task execution, acceptance, log editing, or company-item administration within Home.

#### Scenario: User follows a Home card entry
- **WHEN** the user taps a Home card or its “View all” action
- **THEN** the system opens the corresponding authorized secondary list or module without modifying the source record from Home
