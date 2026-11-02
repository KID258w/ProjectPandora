## ADDED Requirements

### Requirement: Users maintain at most one daily log per day
The system SHALL enforce at most one log per author and natural date. The author MAY save the log as a private draft and submit it. Submission requires completed work for the log date; the next-day plan is optional. The log form SHALL NOT provide task-association or issues/risks fields.

#### Scenario: User saves a draft for a date in the three-date window
- **WHEN** a user saves an incomplete log for today, yesterday, or the day before yesterday as a draft
- **THEN** the system stores one draft for that user and date and makes it visible only to its author

#### Scenario: User submits a log without completed work
- **WHEN** a user attempts to submit a log with an empty "completed work for the log date" field
- **THEN** the system blocks submission and identifies the required field while allowing draft saving

#### Scenario: User attempts a duplicate daily log
- **WHEN** a user already has a log for a date in the current three-date window and attempts to create another
- **THEN** the system opens or updates the existing log instead of creating a duplicate

### Requirement: Authors may create and edit logs within the current three-date window
The system SHALL allow an author to create or edit a log dated today, yesterday, or the day before yesterday, according to the configured business timezone. This is a window of three natural dates, not rolling 72 hours. At the start of each new natural day, the window SHALL move forward; older dates SHALL become read-only and SHALL NOT be backfilled. Future dates SHALL reject writes. Editing a submitted log SHALL preserve submitted status and update its last-modified time.

#### Scenario: Author edits a submitted log within the three-date window
- **WHEN** the author edits a submitted log dated today, yesterday, or the day before yesterday
- **THEN** the system saves the latest content, retains submitted status, and updates the last-modified time

#### Scenario: Author backfills yesterday or the day before yesterday
- **WHEN** the author creates and submits a missing log for yesterday or the day before yesterday
- **THEN** the system saves the unique log for that date and allows authorized managers to view it after submission

#### Scenario: A date freezes as the window rolls forward
- **WHEN** a natural day begins and a log date becomes older than the three-date window
- **THEN** the author can still read it but cannot create, edit, or submit it

#### Scenario: User attempts to create a future log
- **WHEN** a user attempts to create a log for a future date
- **THEN** the system rejects the write and creates no log

### Requirement: The Log tab opens a calendar and selected-date detail
The system SHALL open the monthly calendar as the default Log tab screen and show the selected date's available log detail below it. The month heading SHALL open a year/month wheel selector with a vertically scrollable year wheel on the left and month wheel on the right. Authors MAY see their own drafts; managers SHALL see only submitted logs and SHALL NOT learn whether a subordinate has a draft.

#### Scenario: User opens the Log tab
- **WHEN** a user opens the Log tab
- **THEN** the system shows the monthly calendar and selected-date detail or empty state, rather than opening the editor by default

#### Scenario: User jumps to another year and month
- **WHEN** a user selects a year and month from the calendar's quick selector
- **THEN** the calendar displays that selected month and marks dates with submitted logs

#### Scenario: User scrolls the year/month wheels
- **WHEN** a user opens the quick selector and scrolls its left year wheel and right month wheel
- **THEN** the selected year and month update independently, and confirming navigates to that month

### Requirement: The personal calendar shows the appropriate log action
On the author's own calendar, the system SHALL show a circular plus button for today, yesterday, or the day before yesterday only when the date has no log or has a draft. It SHALL hide the plus button for submitted logs, future dates, frozen dates, and subordinate views.

#### Scenario: User creates or edits a selected date's log from the plus button
- **WHEN** a user viewing their own calendar selects today, yesterday, or the day before yesterday and taps the circular plus/edit button
- **THEN** the system opens a blank form or continues the existing draft for that date without creating a duplicate

#### Scenario: Author opens a submitted log inside the editable window
- **WHEN** the author selects a date in the three-date window that already has a submitted log
- **THEN** the plus button is hidden and the log area shows a clickable detail entry

#### Scenario: User selects a date outside the writable window
- **WHEN** a user selects a future or frozen past date in their own calendar
- **THEN** the system hides the plus button, shows a clickable detail entry for an existing log, opens it read-only, and does not allow writes

### Requirement: Existing log detail navigation respects access and date window
The system SHALL open an existing log in a detail page first. Comments SHALL appear in a section immediately after the log content. All viewers who can read the log SHALL see a fixed, input-style comment entry at the bottom of the detail page; selecting it SHALL open the comment form. The author SHALL see a fixed Modify action only while the log date is editable.

#### Scenario: Author opens an editable submitted log
- **WHEN** the author taps the detail entry for a submitted log dated today, yesterday, or the day before yesterday
- **THEN** the read-only detail shows the comments section after the log content, with a fixed input-style comment entry at the bottom and a fixed Modify action on the right; selecting Modify opens the editor and saving retains submitted status

#### Scenario: User opens a frozen or subordinate log
- **WHEN** a user taps the detail entry for a frozen personal log or a submitted subordinate log
- **THEN** a read-only detail page shows comments after the log content and the fixed input-style comment entry, with no Modify action

### Requirement: Log editor and detail hide the main bottom navigation
The system SHALL hide the App's main bottom navigation while a log editor or read-only log detail page is open, and SHALL restore it when the user returns to the log calendar.

#### Scenario: User opens a log editor or detail page
- **WHEN** the user navigates from the log calendar into an editor or read-only detail
- **THEN** the main bottom navigation is hidden for the full screen, including while the soft keyboard is visible

#### Scenario: User returns to the log calendar
- **WHEN** the user exits the editor or detail page
- **THEN** the log calendar is shown and the main bottom navigation is restored

### Requirement: Managers read only submitted subordinate logs
The system SHALL allow authorized managers to view submitted logs for direct and indirect subordinates within their organization scope. The system SHALL hide drafts and SHALL NOT reveal whether a subordinate has no log or an unsubmitted draft. Managers SHALL NOT create, edit, delete, or accept a subordinate's log, but MAY comment on logs they can read.

#### Scenario: Manager filters to an authorized subordinate
- **WHEN** a manager selects a direct or indirect subordinate within their authorized scope
- **THEN** the system displays that person's submitted logs by date and no draft or missing-log status

#### Scenario: Manager searches for a subordinate
- **WHEN** a manager enters a person's name, team, or department in the object-selection window
- **THEN** the system filters only people within that manager's existing organization scope and does not search log text

#### Scenario: Manager requests a draft or out-of-scope log
- **WHEN** a manager requests a subordinate draft or a log outside their authorized organization scope
- **THEN** the system returns no such log content

#### Scenario: Manager comments on a submitted log
- **WHEN** a manager comments on a submitted log within their authorized subordinate scope
- **THEN** the system stores the comment separately without changing the log content or status

#### Scenario: Manager attempts to modify a submitted log
- **WHEN** a manager attempts to modify, delete, or accept a subordinate log
- **THEN** the system rejects the operation and preserves the author's record

### Requirement: Log MVP excludes attachments and log-content search
The system SHALL provide text-only log content and date-based browsing with separate comments. It SHALL NOT provide log attachments or search over log text, summaries, tags, or historical records. Keyword filtering of authorized people in the manager's object selector is permitted and SHALL NOT search log contents.

#### Scenario: User browses logs without content search
- **WHEN** a user browses their log calendar or a manager's authorized subordinate log calendar
- **THEN** the system allows date-based browsing and comments without attachment or log-content search controls

### Requirement: Comment access follows log visibility
The system SHALL allow any authenticated App user who can read a log to list and add comments. Comment access SHALL follow the log's visibility scope. Managers SHALL only comment on submitted logs within their existing subordinate scope.

#### Scenario: Author comments on their own log
- **WHEN** the author submits a comment on a log they can read
- **THEN** the comment is stored separately and displayed with the author's name and role and the “日志本人” marker

#### Scenario: Authorized manager comments on a subordinate log
- **WHEN** a manager reads and comments on a submitted log in their existing scope
- **THEN** the comment is visible to other readers of that log and does not alter the log content, status, or update time

#### Scenario: User attempts to comment on an unreadable log
- **WHEN** an App user lists or posts comments for a log outside their existing visibility scope
- **THEN** the server rejects access and does not expose or create comments

### Requirement: Comments preserve the log record
Comments SHALL be separate immutable records. Adding a comment SHALL NOT modify the log content, status, or last-updated time. Comment content SHALL contain 1 to 2000 characters.

#### Scenario: User adds a valid comment
- **WHEN** a user submits a non-blank comment of at most 2000 characters
- **THEN** the system stores it separately without changing the log record

#### Scenario: User submits invalid comment content
- **WHEN** a user submits a blank comment or content longer than 2000 characters
- **THEN** the server rejects the request and creates no comment

### Requirement: Comments identify their authors
Each comment SHALL show its author's name, role, department/team when available, and creation time. A comment written by the log author SHALL be marked as “日志本人”.

#### Scenario: Log author writes a comment
- **WHEN** the log author posts a comment
- **THEN** the comment displays the author's name and role and is marked “日志本人”
