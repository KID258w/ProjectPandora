## ADDED Requirements

### Requirement: Users maintain at most one daily log per day
The system SHALL allow a user to create one log for the current natural day, save it as a private draft, and submit it. The log SHALL contain a required "Today's completed work" field for submission; task associations, issues/risks, and next-day plan are optional.

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

### Requirement: Logs associate optionally with the author's tasks
The system SHALL allow a log editor to associate zero or more tasks that the current user is authorized to execute. Creating or editing a log association SHALL NOT update task progress, and task screens SHALL NOT provide log-association editing.

#### Scenario: User associates a log with their tasks
- **WHEN** a user selects one or more of their own tasks while editing a log
- **THEN** the system stores those associations with the log and leaves task progress unchanged

#### Scenario: User attempts to associate another person's task
- **WHEN** a user submits a task association outside their own authorized execution scope
- **THEN** the system rejects that association without changing the log

### Requirement: Managers read only submitted subordinate logs
The system SHALL allow authorized managers to view submitted logs for direct and indirect subordinates within their organization scope. The system SHALL hide drafts and SHALL NOT reveal whether a subordinate has no log or an unsubmitted draft. Managers SHALL NOT create, edit, delete, comment on, or accept a subordinate's log.

#### Scenario: Manager filters to an authorized subordinate
- **WHEN** a manager selects a direct or indirect subordinate within their authorized scope
- **THEN** the system displays that person's submitted logs by date and no draft or missing-log status

#### Scenario: Manager requests a draft or out-of-scope log
- **WHEN** a manager requests a subordinate draft or a log outside their authorized organization scope
- **THEN** the system returns no such log content

#### Scenario: Manager attempts to edit a submitted log
- **WHEN** a manager attempts to modify, delete, comment on, or accept a subordinate log
- **THEN** the system rejects the operation and preserves the author's record

### Requirement: Log MVP excludes attachments, comments, and search
The system SHALL provide text-only log content and date-based history browsing. It SHALL NOT provide log attachments, comments, or search in the MVP.

#### Scenario: User opens log history
- **WHEN** a user opens their log history
- **THEN** the system allows date-based browsing of existing logs without attachment, comment, or search controls
