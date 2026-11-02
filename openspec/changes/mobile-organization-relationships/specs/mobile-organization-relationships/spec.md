## ADDED Requirements

### Requirement: The App exposes company organization relationships to all users

The system SHALL provide an Organization Relationships entry beside Task Center at the top right of the Views page for every authenticated App user. The organization tree SHALL show the enabled company organization and be expandable. The active Founder and active departments SHALL appear as sibling root nodes. Visible member nodes SHALL show their stored avatar or a name initial. Searching by member name or business role SHALL replace the tree with a flat list of matching members; each row SHALL show the avatar, name, department/team below the name except for the Founder, and the business role on the right. Clearing the search SHALL restore the tree.

#### Scenario: Founder browses the company tree
- **WHEN** an authenticated Founder opens Organization Relationships
- **THEN** the initial tree shows the Founder beside all active departments, with department members and teams revealed by expansion

#### Scenario: Employee browses the organization
- **WHEN** an authenticated Employee opens Organization Relationships
- **THEN** the system shows the same enabled company organization tree available to other App roles

#### Scenario: Web administrator requests the tree
- **WHEN** a Web administrator who is not authenticated as a business App user requests the App organization tree
- **THEN** the server denies the request

#### Scenario: User searches by name or role
- **WHEN** an App user enters a member name or business role in the organization search
- **THEN** matching members are shown as a flat list without department/team nodes, with avatar, name, department/team (except Founder), and role

### Requirement: App members have a read-only profile with permitted work shortcuts

The system SHALL allow every App user to open any active business member's profile. The page SHALL show the stored avatar when available, name, phone, role, department, and team, without edit controls. The profile SHALL NOT display or return national ID, supervisor, or account status. It SHALL provide task and log shortcuts only when the current user has existing permission to access that member's work records.

#### Scenario: App user opens a member profile
- **WHEN** an App user taps a member within the company organization tree
- **THEN** the App displays the member's avatar or a name initial, name, phone, role, department, and team, and shows each work shortcut only when its corresponding existing permission allows access

#### Scenario: App user opens a member without work access
- **WHEN** an App user opens a member profile whose tasks and logs are outside their existing subordinate scope
- **THEN** the server returns the read-only profile with work shortcuts disabled, and existing task/log APIs continue to deny out-of-scope requests

#### Scenario: Manager follows the task shortcut
- **WHEN** a manager taps “查看任务” on an authorized subordinate's profile
- **THEN** the App opens that member's approved subordinate task list, defaults to assigned tasks, allows the existing assigned/created task scope switch, and keeps tasks read-only

#### Scenario: Manager follows the log shortcut
- **WHEN** a manager taps “查看日志” on an authorized subordinate's profile
- **THEN** the App opens the member's log calendar and exposes only submitted logs in read-only mode

### Requirement: Task Center and Approval Center actions have fixed top-right positions

The Views page SHALL place Task Center at the top right, with Organization Relationships beside it for every App role. Task Center SHALL place Approval Center at the top right only for users who have an approval queue. Task Center SHALL NOT expose a separate subordinate-task shortcut; subordinate tasks SHALL be reached from a member profile when existing task permissions allow access.

#### Scenario: User opens Views
- **WHEN** a user opens the Views page
- **THEN** the Task Center action and Organization Relationships action appear at the top right

#### Scenario: User opens Task Center
- **WHEN** a user enters Task Center and has an approval queue
- **THEN** Approval Center appears at the top right; roles without approval queue permission do not see it

#### Scenario: Manager views a subordinate's tasks
- **WHEN** a manager needs to view a subordinate's tasks
- **THEN** the manager selects that member through Organization Relationships and enters tasks from the member profile instead of a Task Center subordinate shortcut

### Requirement: Logs use organization relationships to select subordinate records

The Logs tab SHALL default to the current user's own log calendar and SHALL NOT provide a separate subordinate-person selector. A manager SHALL enter a subordinate's logs through the authorized member profile, and the log screen SHALL show the selected member and provide a route back to their profile.

#### Scenario: Manager views a subordinate's logs
- **WHEN** a manager selects “查看日志” for an authorized member
- **THEN** the App displays that member's submitted logs only, with no create, edit, submit, or delete action

#### Scenario: User opens the regular Logs tab
- **WHEN** any user opens the regular Logs tab
- **THEN** the tab opens the current user's own calendar and does not show a subordinate selector
