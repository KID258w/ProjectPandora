## ADDED Requirements

### Requirement: Users maintain at most one daily log per day
The system SHALL allow a user to create one log for the current natural day, save it as a private draft, and submit it. The log SHALL contain a required "Today's completed work" field for submission and an optional next-day plan. The log form SHALL NOT provide task-association or issues/risks fields.

#### Scenario: User saves a draft
- **WHEN** a user saves an incomplete current-day log as a draft
- **THEN** the system stores one draft for that user and date and makes it visible only to its author

#### Scenario: User submits a log without completed work
- **WHEN** a user attempts to submit a log with an empty "Today's completed work" field
- **THEN** the system blocks submission and identifies the required field while allowing draft saving

#### Scenario: User attempts a duplicate daily log
- **WHEN** a user already has a log for the current natural day and attempts to create another
- **THEN** the system opens or updates the existing log instead of creating a duplicate

### Requirement: Authors may edit only current-day logs
The system SHALL allow an author to edit their draft or submitted log until the end of its natural day. After that day ends, the log SHALL be read-only and SHALL NOT be backfilled. Editing a submitted log SHALL preserve submitted status and update its last-modified time.

#### Scenario: Author edits a submitted log on the same day
- **WHEN** the author edits their submitted log before the current day ends
- **THEN** the system saves the latest content, retains submitted status, and updates the last-modified time

#### Scenario: Author attempts to edit or create a past-date log
- **WHEN** a user attempts to change a past log or create a log for a past date
- **THEN** the system rejects the write and leaves the historical record unchanged

### Requirement: The Log tab opens a calendar and selected-date detail
The system SHALL open the monthly log calendar as the default Log tab screen, with a selected date's submitted log detail displayed below it when one exists. The month heading SHALL provide quick year/month selection. Dates without a submitted log SHALL show an empty state without revealing whether a draft exists. In the personal view, a circular plus button SHALL open the current-day log editor; it SHALL NOT appear while viewing a subordinate.

#### Scenario: User opens the Log tab
- **WHEN** a user opens the Log tab
- **THEN** the system shows the monthly calendar and selected-date detail or empty state, rather than opening the editor by default

#### Scenario: User jumps to another year and month
- **WHEN** a user selects a year and month from the calendar's quick selector
- **THEN** the calendar displays that selected month and marks dates with submitted logs

#### Scenario: User creates or edits today's log from the plus button
- **WHEN** a user viewing their own log calendar taps the circular plus button
- **THEN** the system opens that user's current-day log editor and reuses the existing daily record if one already exists

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
