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
The system SHALL open the monthly calendar as the default Log tab screen and show the selected date's available log detail below it. The month heading SHALL provide quick year/month selection. Authors MAY see their own drafts; managers SHALL see only submitted logs and SHALL NOT learn whether a subordinate has a draft.

#### Scenario: User opens the Log tab
- **WHEN** a user opens the Log tab
- **THEN** the system shows the monthly calendar and selected-date detail or empty state, rather than opening the editor by default

#### Scenario: User jumps to another year and month
- **WHEN** a user selects a year and month from the calendar's quick selector
- **THEN** the calendar displays that selected month and marks dates with submitted logs

### Requirement: The personal calendar offers writes only for eligible dates
The system SHALL show an author-only circular create/edit button for the selected date when it is today, yesterday, or the day before yesterday. It SHALL hide this button for future/frozen dates and while viewing a subordinate. Existing logs outside the window SHALL remain readable.

#### Scenario: User creates or edits a selected date's log from the plus button
- **WHEN** a user viewing their own calendar selects today, yesterday, or the day before yesterday and taps the circular plus/edit button
- **THEN** the system opens that selected-date editor and reuses the existing record if one already exists

#### Scenario: User selects a date outside the writable window
- **WHEN** a user selects a future or frozen past date in their own calendar
- **THEN** the system shows any existing log read-only and does not show a create/edit button

### Requirement: Managers read only submitted subordinate logs
The system SHALL allow authorized managers to view submitted logs for direct and indirect subordinates within their organization scope. The system SHALL hide drafts and SHALL NOT reveal whether a subordinate has no log or an unsubmitted draft. Managers SHALL NOT create, edit, delete, comment on, or accept a subordinate's log.

#### Scenario: Manager filters to an authorized subordinate
- **WHEN** a manager selects a direct or indirect subordinate within their authorized scope
- **THEN** the system displays that person's submitted logs by date and no draft or missing-log status

#### Scenario: Manager searches for a subordinate
- **WHEN** a manager enters a person's name, team, or department in the object-selection window
- **THEN** the system filters only people within that manager's existing organization scope and does not search log text

#### Scenario: Manager requests a draft or out-of-scope log
- **WHEN** a manager requests a subordinate draft or a log outside their authorized organization scope
- **THEN** the system returns no such log content

#### Scenario: Manager attempts to edit a submitted log
- **WHEN** a manager attempts to modify, delete, comment on, or accept a subordinate log
- **THEN** the system rejects the operation and preserves the author's record

### Requirement: Log MVP excludes attachments, comments, and log-content search
The system SHALL provide text-only log content and date-based browsing. It SHALL NOT provide log attachments, comments, or search over log text, summaries, tags, or historical records. Keyword filtering of authorized people in the manager's object selector is permitted and SHALL NOT search log contents.

#### Scenario: User browses logs without content search
- **WHEN** a user browses their log calendar or a manager's authorized subordinate log calendar
- **THEN** the system allows date-based browsing without attachment, comment, or log-content search controls
