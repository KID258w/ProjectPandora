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

#### Scenario: Administrator replaces a Department Manager
- **WHEN** an administrator assigns a new Department Manager to a department
- **THEN** the new manager's organization scope takes effect immediately, the former manager loses that scope, and historical tasks and logs remain unchanged

### Requirement: Administrators manage teams within departments
The system SHALL allow an administrator to create, view, edit, move, activate, and deactivate teams and to set or replace a Team Leader. A team SHALL belong to a department, and deactivation SHALL preserve historical records.

#### Scenario: Administrator creates or moves a team
- **WHEN** an administrator submits valid team details and an active parent department
- **THEN** the system saves the team under the selected department

#### Scenario: Administrator deactivates a team with active members
- **WHEN** an administrator tries to deactivate a team that still has active members
- **THEN** the system rejects the operation until those members are reassigned or deactivated

#### Scenario: Administrator replaces a Team Leader
- **WHEN** an administrator assigns a new Team Leader to a team
- **THEN** the new leader's organization scope takes effect immediately and the former leader loses that scope without altering historical records

### Requirement: Administrators manage business accounts and roles
The system SHALL allow an administrator to create and edit business accounts, assign one primary business role and an organization placement, activate or deactivate an account, and reset its password. Business roles SHALL be Founder, Department Manager, Team Leader, or Employee. An account SHALL NOT simultaneously be a System Administrator and a mobile business account.

#### Scenario: Administrator creates a business account
- **WHEN** an administrator submits a unique login name, name, password, primary business role, organization placement, and account status
- **THEN** the system creates the account with the selected role and organization scope

#### Scenario: Administrator assigns an incompatible or duplicate role
- **WHEN** an administrator tries to assign multiple primary business roles or combine the administrator role with a business account
- **THEN** the system rejects the assignment and preserves the existing account configuration

#### Scenario: Administrator deactivates a current organization leader
- **WHEN** an administrator tries to deactivate an account that is still the assigned Department Manager or Team Leader
- **THEN** the system rejects the operation until a replacement leader is assigned

#### Scenario: Administrator deactivates a business account
- **WHEN** an administrator deactivates an account that is not an unresolved organization leader
- **THEN** the account can no longer authenticate, while its historical records remain available to authorized business workflows

### Requirement: The admin workbench contains only system-management information
The system SHALL provide an administrator workbench with organization, account, company-important-item, and administrator-operation summaries, and SHALL exclude task and employee-log counts, status, content, and analytics.

#### Scenario: Administrator opens the workbench
- **WHEN** an active administrator opens the Web workbench
- **THEN** the system displays only system-management summaries and no task or employee-log information

### Requirement: Administrator operations are auditable
The system SHALL record successful administrator changes to departments, teams, accounts, roles, and organization responsibilities, including administrator, time, module, action, target, result, and applicable before-and-after values. Audit records SHALL NOT contain plaintext passwords or employee-log content.

#### Scenario: Administrator successfully changes an account or organization
- **WHEN** an administrator successfully creates, edits, activates, deactivates, or reassigns an account or organization record
- **THEN** the system stores an audit record with the applicable actor, time, target, action, result, and change details

#### Scenario: Administrator filters audit records
- **WHEN** an administrator filters the audit list by time range, administrator, or module
- **THEN** the system returns matching management audit records without exposing task or employee-log content
