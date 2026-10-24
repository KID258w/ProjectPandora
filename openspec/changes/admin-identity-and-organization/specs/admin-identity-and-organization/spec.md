## ADDED Requirements

### Requirement: Admin console access is isolated from business roles
The system SHALL allow only an active System Administrator account to authenticate to the Web management console and its management APIs. The System Administrator SHALL remain separate from mobile business accounts and SHALL NOT read or modify business tasks or employee logs.

#### Scenario: Active administrator opens the console
- **WHEN** an active System Administrator authenticates with valid credentials
- **THEN** the system grants access to authorized Web management functions

#### Scenario: Business role attempts to use an admin endpoint
- **WHEN** a Founder, Department Manager, Team Leader, or Employee requests a Web administration route or API
- **THEN** the system rejects the request and returns no management data

#### Scenario: Administrator attempts to read tasks or employee logs
- **WHEN** a System Administrator requests task or employee-log data through a route or API
- **THEN** the system rejects the request and returns no task or log data

### Requirement: Administrators manage departments without deleting history
The system SHALL allow an administrator to create, view, edit, activate, and deactivate departments, and to set or replace a Department Manager. Deactivation SHALL preserve historical records and SHALL be blocked while active subordinate teams or accounts remain unresolved.

#### Scenario: Administrator creates or edits a department
- **WHEN** an administrator submits valid department details
- **THEN** the system saves the department and makes it available for authorized organization management

#### Scenario: Administrator deactivates a department with active descendants
- **WHEN** an administrator tries to deactivate a department that still has active subordinate teams or accounts
- **THEN** the system rejects the operation and identifies the descendants that must first be reassigned or deactivated

#### Scenario: Department detail shows responsible manager and members read-only
- **WHEN** an administrator views a department's detail
- **THEN** the system shows the responsible manager, subordinate teams, and members without allowing them to be edited from the detail view

### Requirement: Administrators manage teams within departments
The system SHALL allow an administrator to create, view, edit, activate, and deactivate teams and to set or replace a Team Leader. A team SHALL belong to a department, and deactivation SHALL preserve historical records.

#### Scenario: Administrator creates or edits a team
- **WHEN** an administrator submits valid team details and an active parent department
- **THEN** the system saves the team under the selected department

#### Scenario: Administrator deactivates a team with active members
- **WHEN** an administrator tries to deactivate a team that still has active members
- **THEN** the system rejects the operation until those members are reassigned or deactivated

### Requirement: Administrators filter and manage employees
The system SHALL allow an administrator to filter employees by name/phone, business role, department, team, and activation status, and to adjust an employee's business role, department/team placement, and activation status. Web editing SHALL NOT modify name, phone, national ID, or password.

#### Scenario: Administrator filters employees
- **WHEN** an administrator queries the employee list with name/phone, role, department, team, or activation filters
- **THEN** the system returns matching active and inactive employees without exposing plaintext passwords or full national IDs

#### Scenario: Administrator adjusts role and placement
- **WHEN** an administrator updates an employee's business role, department, or team
- **THEN** the system validates placement, leader uniqueness, and active candidates, and persists the change atomically

#### Scenario: Employee update rejects non-allowed fields
- **WHEN** an administrator submits name, phone, national ID, or password fields on the employee update endpoint
- **THEN** the system rejects those fields and preserves the current values

### Requirement: Employees maintain their own profile in the App
The system SHALL allow an employee to view their own profile and update their name, phone, and national ID. The national ID SHALL be returned masked. When multiple fields are submitted in one request, the update SHALL be atomic so any validation failure rolls back the whole request.

#### Scenario: Employee views their own profile
- **WHEN** an authenticated business user requests their own profile
- **THEN** the system returns name, phone, role, organization, and a masked national ID

#### Scenario: Employee updates name or national ID independently
- **WHEN** an employee submits only their name or only their national ID
- **THEN** the system validates and saves that single field without requiring the other

#### Scenario: Employee updates multiple profile fields atomically
- **WHEN** an employee submits several profile fields in one request and any field fails validation
- **THEN** the system rolls back the entire update and returns the validation error

#### Scenario: Phone change affects subsequent login
- **WHEN** an employee changes their phone number
- **THEN** the system stores the new value as the login username used for subsequent App sign-in

### Requirement: National IDs are unique
The system SHALL ensure each employee national ID is unique across accounts, while still allowing historical accounts to have no national ID recorded.

#### Scenario: Duplicate national ID on account creation
- **WHEN** an administrator tries to create an account with a national ID already used by another account
- **THEN** the system rejects the creation and records no account

#### Scenario: Duplicate national ID on profile update
- **WHEN** an employee tries to change their national ID to one already used by another account
- **THEN** the system rejects the change and preserves the existing value

### Requirement: Employees change their own password in the App
The system SHALL allow an employee to change their own password by providing the current password, a new password, and its confirmation. The new password SHALL be stored as a Django password hash.

#### Scenario: Employee changes password with correct current password
- **WHEN** an employee submits the correct current password and a valid, matching new password
- **THEN** the system updates the password hash and invalidates the old credential for future sign-in

#### Scenario: Employee submits wrong current password or mismatched confirmation
- **WHEN** an employee submits an incorrect current password or a confirmation that does not match the new password
- **THEN** the system rejects the request without changing the stored password

### Requirement: The admin workbench contains only system-management information
The system SHALL provide an administrator workbench with organization, account, and administrator-operation summaries, and SHALL exclude task and employee-log counts, status, content, and analytics.

#### Scenario: Administrator opens the workbench
- **WHEN** an active administrator opens the Web workbench
- **THEN** the system displays only system-management summaries and no task or employee-log information

### Requirement: Administrator operations are auditable
The system SHALL record successful administrator changes to departments, teams, accounts, roles, and organization responsibilities, including administrator, time, module, action, target, result, and applicable before-and-after values. Audit records SHALL NOT contain plaintext passwords, full national IDs, or employee-log content.

#### Scenario: Administrator successfully changes an account or organization
- **WHEN** an administrator successfully creates, edits, activates, deactivates, or reassigns an account or organization record
- **THEN** the system stores an audit record with the applicable actor, time, target, action, result, and redacted change details

#### Scenario: Administrator filters audit records
- **WHEN** an administrator filters the audit list by time range, administrator, or module
- **THEN** the system returns matching management audit records without exposing passwords, full national IDs, or employee-log content
