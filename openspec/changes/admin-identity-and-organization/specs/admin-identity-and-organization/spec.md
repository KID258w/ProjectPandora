## Purpose

Define Issue2 behavior for Web department, team, and employee maintenance and Android employee self-service, using the existing administrator/business account separation.

## ADDED Requirements

### Requirement: Administrators inspect and edit department and team details
The system SHALL provide department and team details with read-only leader and member information. Resource updates SHALL accept name/description changes or a standalone activation change; a team's department SHALL NOT change after creation.

#### Scenario: Administrator views organization details
- **WHEN** an administrator opens a department or team detail
- **THEN** the response includes its name, description, activation state, leader or null, and members; department details also include subordinate teams and team details include the parent department

#### Scenario: Administrator edits organization information
- **WHEN** an administrator updates a department or team
- **THEN** the system accepts name and description changes or a standalone `is_active` change, and rejects direct leader, member, or team-parent changes

### Requirement: Organization activation preserves active descendants
The system SHALL let administrators activate and deactivate departments and teams while retaining their records. Disabled organizations SHALL remain visible in management lists and SHALL NOT be assignment candidates.

#### Scenario: Administrator deactivates an organization with active descendants
- **WHEN** a department has an active subordinate team or member, or a team has an active member
- **THEN** the system rejects deactivation and returns a user-readable validation error

#### Scenario: Administrator selects an organization for employee placement
- **WHEN** the system provides department or team choices for employee placement
- **THEN** it excludes disabled organizations while retaining them in management lists

### Requirement: Organizations have at most one active leader and may have none
The system SHALL allow zero or one active Department Manager per department and zero or one active Team Leader per team. Leader changes SHALL be made through employee role and placement management; the system SHALL NOT automatically select a successor.

#### Scenario: Administrator assigns an available leader role
- **WHEN** an enabled employee receives a leader role and valid organization placement for an organization with no active leader
- **THEN** the system assigns that employee as the active leader

#### Scenario: Administrator assigns a second active leader
- **WHEN** an organization already has an active leader
- **THEN** the system rejects the second active leader assignment without changing the existing leader

#### Scenario: Leader is revoked or deactivated
- **WHEN** an administrator revokes or deactivates the active leader
- **THEN** the organization may remain without an active leader and affected direct-report links may be null until a valid successor is assigned

#### Scenario: A valid successor is enabled
- **WHEN** an administrator assigns and enables a valid successor through employee management
- **THEN** the backend recalculates the affected direct-report links

### Requirement: Administrators manage employee placement without editing personal data
The system SHALL let administrators search and view employees and update only business role, department/team placement, and activation state after account creation. It SHALL reject administrator writes to employee name, phone, identity number, or password.

#### Scenario: Administrator filters employees
- **WHEN** an administrator filters employees by name or phone, business role, department, team, or activation state
- **THEN** the system returns matching employees, including disabled employees when requested for management, with identity numbers masked

#### Scenario: Administrator updates employee placement or role
- **WHEN** an administrator submits a valid role, organization placement, or activation change
- **THEN** the system validates role-placement compatibility and applies only the permitted changes

#### Scenario: Administrator updates personal information
- **WHEN** an administrator submits employee name, phone, identity number, or password through Web management
- **THEN** the system rejects the prohibited fields and leaves those values unchanged

#### Scenario: Disabled records are offered as placement candidates
- **WHEN** an administrator opens an employee role or organization placement selector
- **THEN** disabled employees, departments, and teams are excluded from candidate choices

### Requirement: App employees update their own profile atomically
The system SHALL let an authenticated employee update only their own name, phone, and identity number in the App. Name and identity number SHALL be editable in one profile form, may be submitted together or separately, and SHALL not be automatically synchronized. A failed multi-field update SHALL leave all submitted values unchanged.

#### Scenario: Employee updates name and identity number together
- **WHEN** an employee submits a valid `display_name` and `id_number` in one profile update
- **THEN** the system saves both values in one transaction

#### Scenario: Employee updates only one personal field
- **WHEN** an employee submits only a name or only an identity number
- **THEN** the system updates that field without requiring the other field to change

#### Scenario: One submitted profile field is invalid
- **WHEN** a profile update contains multiple fields and any field fails validation
- **THEN** the system rejects the update and saves none of the submitted changes

#### Scenario: Employee changes phone number
- **WHEN** an employee submits an unused valid phone number
- **THEN** the system atomically updates the unique `username` login identifier and subsequent sign-in uses the new phone number

#### Scenario: Employee attempts to change administrator-managed fields
- **WHEN** an employee submits a business role, department, team, manager, or activation change from the App
- **THEN** the system rejects those fields

### Requirement: App employees change their own password
The system SHALL allow an authenticated employee to change only their own password after verifying the current password, matching confirmation, and configured password validators. It SHALL store only the password hash and SHALL NOT return or log password values.

#### Scenario: Employee changes password with valid credentials
- **WHEN** an employee submits the correct current password, a valid different new password, and a matching confirmation
- **THEN** the system changes only that employee's password hash and returns a success flag

#### Scenario: Employee submits invalid password-change data
- **WHEN** the current password is wrong, the confirmation differs, the new password is unchanged, or configured password validation fails
- **THEN** the system rejects the request and leaves the stored password unchanged

### Requirement: National IDs are unique
The system SHALL ensure each employee national ID is unique across accounts, while historical accounts may have no national ID recorded.

#### Scenario: Duplicate national ID on account creation
- **WHEN** an administrator tries to create an account with a national ID already used by another account
- **THEN** the system rejects the creation and records no account

#### Scenario: Duplicate national ID on profile update
- **WHEN** an employee tries to change their national ID to one already used by another account
- **THEN** the system rejects the change and preserves the existing value

### Requirement: Sensitive account data is protected in management responses and audit records
The system SHALL mask identity numbers in ordinary reads and exclude plaintext passwords, full identity numbers, and employee-log content from logs and administrator audit records.

#### Scenario: Administrator reads employee data
- **WHEN** an administrator reads an employee list or detail
- **THEN** the response includes only a masked identity number and never includes a password

#### Scenario: Administrator operation is audited
- **WHEN** an administrator successfully changes organization or employee-management data
- **THEN** the system records actor, time, module, action, target, result, and allowed before/after values while omitting excluded sensitive data

### Requirement: Administrator summaries and audit access remain within management scope
The system SHALL limit the administrator workbench and audit APIs to system-management data and SHALL NOT return business tasks or employee logs.

#### Scenario: Administrator opens the workbench or audit view
- **WHEN** an administrator requests management summaries or audit records
- **THEN** the system returns only permitted management data and excludes task and employee-log data
