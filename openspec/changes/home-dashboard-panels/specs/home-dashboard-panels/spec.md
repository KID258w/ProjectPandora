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
The panel SHALL list up to ten unfinished tasks assigned to the current user, ordered first by other-created assignments, then by self-created tasks, then by earliest deadline and creation time. Assignments from another Department Manager use the ordinary “他人派发” category, without a separate department marker. Completed and cancelled tasks SHALL be excluded. The list is not user-editable and shows no percentage progress; completion status remains “未完成” or “已完成”.

#### Scenario: User has tasks from other creators and self-created tasks
- **WHEN** the system builds the Company Top Ten Tasks panel
- **THEN** it lists tasks created by others before self-created tasks, applies deadline/creation ordering within each source, treats assignments from another Department Manager as ordinary tasks created by someone else, and returns at most ten

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

### Requirement: Home exposes unread task notifications
Home SHALL show a task-notification bell with a red dot when unread notifications exist. Opening its list SHALL mark the notifications currently displayed as read; new notifications SHALL remain unread. Notification rows SHALL navigate to the corresponding task without changing task state.

#### Scenario: User has an unread approval or assignment notification
- **WHEN** the current user has an unread notice for a direct subordinate's pending approval or an approved task assigned by another user
- **THEN** the Home bell displays a red dot

#### Scenario: User opens the notification list
- **WHEN** the user taps the Home bell
- **THEN** the notification list opens and its currently displayed unread notices are marked read, clearing the red dot unless a newer unread notice exists

#### Scenario: User follows a task notification
- **WHEN** the user taps an approval or new-assignment notification
- **THEN** the corresponding task detail opens with only the user's existing task permissions and no workflow state changes caused by viewing
