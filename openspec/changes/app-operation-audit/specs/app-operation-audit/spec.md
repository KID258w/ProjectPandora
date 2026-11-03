## ADDED Requirements

### Requirement: Record successful employee App operations
The system SHALL record successful App logins and successful state-changing core operations, including personal profile, avatar and password changes; task creation, editing, resubmission, approval, return, updates, completion submission, review and cancellation; daily-log creation, saving, submission, editing and comments; and founder tag creation, editing and deactivation. Records SHALL be written only after the operation succeeds. Read-only access, failed login or write attempts, personal-focus ordering and notification read status SHALL NOT create records.

#### Scenario: Successful App write
- **WHEN** an employee completes a supported state-changing App operation
- **THEN** the system records its actor, time, module, action, target and changed fields with the business values before and after the operation

#### Scenario: Failed or excluded operation
- **WHEN** a supported write fails, a user performs a read-only request, or a user changes personal-focus order or notification read status
- **THEN** the system creates no employee App operation record for that request

### Requirement: Preserve auditable historical values with privacy safeguards
Each record SHALL preserve the actor's name, business role and department/team display information as they were at operation time, plus the target's display name or date where applicable. For task, daily-log and related business-content changes, the record SHALL preserve changed field names and allowlisted before/after values; creation SHALL use an empty before value. Password values, full identity numbers, authentication tokens and avatar binary data SHALL NOT be stored in operation snapshots. Audit data SHALL be append-only through application APIs.

#### Scenario: Employee later changes profile or organization
- **WHEN** an administrator views an older operation after its actor has changed name, role or organization
- **THEN** the record still shows the actor information captured at operation time

#### Scenario: Task or log content changes
- **WHEN** a task, daily log, update or comment is created or changed
- **THEN** the record detail can show the allowlisted changed business values before and after the operation, while omitting prohibited sensitive values

#### Scenario: Password or identity number changes
- **WHEN** an employee changes a password or identity number
- **THEN** the record may identify the changed field but SHALL NOT contain the password or full identity number value

### Requirement: Provide an administrator-only operation history page
The Web management console SHALL provide a separate read-only employee App operation history page. It SHALL show global records newest first by default, support filtering by employee, show object name or date and changed-field summaries in the list, and show before/after values only after an administrator opens a record. It SHALL NOT provide one-click restoration or general task/log browsing, editing, or export.

#### Scenario: Administrator searches recent records
- **WHEN** an administrator opens the employee App operation history page
- **THEN** the page shows global records newest first and allows filtering by employee

#### Scenario: Administrator inspects a record
- **WHEN** an administrator opens a record from the list
- **THEN** the page displays its operation metadata and changed-field values before and after the operation without offering a restore action

#### Scenario: Non-administrator requests operation history
- **WHEN** a non-administrator requests the employee App operation history page or API
- **THEN** the system denies access and returns no audit data
