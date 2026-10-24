## Purpose

Define the administrator and employee account-management boundaries, organization maintenance rules, and App self-service behavior needed by Issue2 while preserving the separate authentication and data-access scopes established by Issue #6.

## ADDED Requirements

### Requirement: Admin console access is isolated from business roles
The system SHALL allow only an active System Administrator to use Web management APIs. Administrators SHALL NOT read or modify business tasks or employee logs.

#### Scenario: Active administrator opens the console
- **WHEN** an active System Administrator authenticates with valid credentials
- **THEN** the system grants access to authorized management functions

#### Scenario: Business role attempts to use an admin endpoint
- **WHEN** a business account requests a Web administration route or API
- **THEN** the system rejects the request without returning management data

#### Scenario: Administrator attempts to read tasks or employee logs
- **WHEN** an administrator requests business task or employee-log data
- **THEN** the system rejects the request without returning that data

### Requirement: Administrators inspect and maintain departments and teams
The system SHALL provide department and team details with read-only leader and member information. Organization detail edits SHALL be limited to name and description; a team's department SHALL be selected at creation and SHALL NOT be changed later.

#### Scenario: Administrator views a department
- **WHEN** an administrator opens a department detail
- **THEN** the system shows its name, description, activation state, manager or no manager, subordinate teams, and members

#### Scenario: Administrator views a team
- **WHEN** an administrator opens a team detail
- **THEN** the system shows its name, description, activation state, parent department, leader or no leader, and members

#### Scenario: Administrator edits organization details
- **WHEN** an administrator edits a department or team detail
- **THEN** the system accepts name and description changes and rejects direct leader, member, or team-parent changes

### Requirement: Organization activation preserves active descendants
The system SHALL allow administrators to activate and deactivate departments and teams while retaining their records. Disabled organizations SHALL remain visible in management lists and SHALL NOT be assignment candidates.

#### Scenario: Administrator deactivates a department with active descendants
- **WHEN** a department still has an active subordinate team or active member
- **THEN** the system rejects department deactivation

#### Scenario: Administrator deactivates a team with active members
- **WHEN** a team still has an active member
- **THEN** the system rejects team deactivation

#### Scenario: Administrator chooses an organization for an employee
- **WHEN** the system provides department or team choices for a new or reassigned employee
- **THEN** it excludes disabled organizations while retaining them in management lists

### Requirement: Organizations have at most one active leader and may have none
The system SHALL allow zero or one active Department Manager per department and zero or one active Team Leader per team. Leader changes SHALL be made through employee role and placement management; the system SHALL NOT automatically select a successor.

#### Scenario: Administrator assigns an available leader role
- **WHEN** an enabled employee receives a leader role and valid organization placement for an organization with no active leader
- **THEN** the system assigns that employee as the active leader

#### Scenario: Administrator assigns a second active leader
- **WHEN** an organization already has an active leader
- **THEN** the system rejects another active leader assignment

#### Scenario: Leader is revoked or deactivated
- **WHEN** an administrator revokes or deactivates the active leader
- **THEN** the organization may remain without an active leader and the system does not appoint a successor

#### Scenario: Direct reports are affected by leader vacancy
- **WHEN** a leader vacancy begins or a valid active successor is assigned
- **THEN** the backend clears affected direct-report links during the vacancy and recalculates them for the successor

### Requirement: Business account creation uses phone login and one-time credentials
The system SHALL require a unique phone number, identity number, name, business role, and valid organization placement when creating a business account. It SHALL enable the account, generate a strong random initial password on the backend, and return that password only in the successful creation response.

#### Scenario: Administrator creates a valid business account
- **WHEN** the administrator submits all required account data with an unused phone number and valid active placement
- **THEN** the system creates an enabled account, stores only the password hash, and returns the phone and initial password once

#### Scenario: Required identity number or phone is missing or duplicated
- **WHEN** account creation omits a required value or uses a phone number already assigned to another account
- **THEN** the system rejects creation without creating an account

#### Scenario: Client supplies a password or activation state
- **WHEN** an account-creation request includes a password or activation-state field
- **THEN** the system rejects those fields and applies its generated-password and enabled-account behavior

### Requirement: Employee administration separates personal data from business placement
The system SHALL let administrators search and view employees and update only business role, department/team placement, and activation state after creation. It SHALL reject administrator writes to employee name, phone, identity number, or password.

#### Scenario: Administrator filters employees
- **WHEN** an administrator filters employees by name or phone, business role, department, team, or activation state
- **THEN** the system returns matching employees, including disabled employees when requested for management

#### Scenario: Administrator updates employee placement or role
- **WHEN** an administrator submits a valid role, organization placement, or activation change
- **THEN** the system validates role-placement compatibility and applies the permitted change

#### Scenario: Administrator edits personal credentials or profile
- **WHEN** an administrator submits an employee name, phone, identity number, or password change through Web management
- **THEN** the system rejects the prohibited field and leaves that personal value unchanged

### Requirement: Sensitive account data is protected
The system SHALL encrypt identity numbers at rest and mask them in ordinary management reads. It SHALL NOT return plaintext passwords or full identity numbers from later reads or include them in logs or audit records.

#### Scenario: Administrator views an employee
- **WHEN** an administrator reads an employee list or detail
- **THEN** the response shows a masked identity number and never returns a password

#### Scenario: User reads their App profile
- **WHEN** a business user reads `/api/v1/app/me/`
- **THEN** the response does not contain the full identity number or a password

#### Scenario: A password or identity number is written to an operational record
- **WHEN** the system records a create, update, error, or audit event
- **THEN** it excludes plaintext passwords and full identity numbers

### Requirement: Employees maintain their own personal profile in the App
The system SHALL allow an authenticated employee to update their own name, phone, identity number, and password in the App. A phone change SHALL update the unique business login identifier; self-service SHALL NOT change business role, organization placement, manager, or activation state.

#### Scenario: Employee changes their phone number
- **WHEN** an employee submits an unused valid phone number in their App profile
- **THEN** the system updates the account's phone-based login identifier and later sign-in uses the new phone

#### Scenario: Employee changes a phone number already in use
- **WHEN** an employee submits a phone number assigned to another account
- **THEN** the system rejects the change and preserves the current phone and login identifier

#### Scenario: Employee attempts to change administrator-managed fields
- **WHEN** an employee submits a business role, department, team, manager, or activation change from the App
- **THEN** the system rejects those fields

#### Scenario: Employee changes their password
- **WHEN** an authenticated employee completes the App password-change flow with valid credentials
- **THEN** the system changes only that employee's password and stores only its hash

### Requirement: Android login identifies the account as a phone number
The Android login interface SHALL label and describe the business login identifier as a phone number. The App SHALL continue to send the phone value in the existing `username` request field.

#### Scenario: Employee signs in with a phone number
- **WHEN** an employee enters their current phone number and password
- **THEN** the App submits the phone number in `username` and presents phone-number wording in the login interface

### Requirement: The admin workbench excludes business task and log information
The system SHALL provide system-management summaries in the administrator workbench and SHALL exclude business task and employee-log counts, content, status, and analytics.

#### Scenario: Administrator opens the workbench
- **WHEN** an active administrator opens the Web workbench
- **THEN** the system displays management summaries without task or employee-log information

### Requirement: Administrator operations are auditable
The system SHALL record successful administrator changes to organizations and employee business placement or activation. Audit records SHALL omit plaintext passwords, full identity numbers, and employee-log content.

#### Scenario: Administrator successfully changes organization or employee management data
- **WHEN** an administrator successfully creates, edits, activates, deactivates, or changes employee placement
- **THEN** the system records the actor, time, module, action, target, result, and allowed before-and-after values

#### Scenario: Administrator filters audit records
- **WHEN** an administrator filters records by time range, administrator, or module
- **THEN** the system returns matching management records without exposing excluded sensitive or business data

### Requirement: Business users authenticate through the Android App session
The Android client SHALL authenticate business users through App-scoped token and current-user endpoints, protect persisted tokens, and clear local authentication state on logout or rejected refresh.

#### Scenario: Active business user signs in to the App
- **WHEN** a business user submits valid credentials to the App login endpoint
- **THEN** the App stores returned tokens securely, fetches the current user, and uses that profile in the main interface

#### Scenario: Invalid credentials or a disabled account are rejected
- **WHEN** a business user submits invalid credentials or an inactive account
- **THEN** the App does not retain the submitted password or rejected tokens and shows actionable feedback

#### Scenario: Session validation fails temporarily during startup
- **WHEN** a saved session cannot be validated because of a temporary network or server failure
- **THEN** the App retains the saved tokens and offers a retry action

#### Scenario: Access token expires
- **WHEN** a protected App request fails because its access token expired
- **THEN** the App attempts one refresh and one retry, then clears authentication state if refresh is rejected

#### Scenario: Business user logs out
- **WHEN** a signed-in user confirms logout
- **THEN** the App removes local access and refresh tokens and returns to the signed-out flow
