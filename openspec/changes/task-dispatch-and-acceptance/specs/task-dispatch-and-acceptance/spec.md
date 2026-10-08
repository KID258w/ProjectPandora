## ADDED Requirements

### Requirement: Authorized managers create and dispatch hierarchical tasks
The system SHALL allow a Founder to assign work to Department Managers, a Department Manager to assign work to Team Leaders, and a Team Leader to assign work to Employees. Authorized managers MAY create work within their own scope and split work into child tasks with clear individual responsibility. Employees SHALL NOT create or dispatch tasks. Lists SHALL reference the same task records rather than duplicate them.

#### Scenario: Founder assigns a department task
- **WHEN** a Founder creates a valid task for one or more Department Managers
- **THEN** the system creates the task or department child tasks under the Founder-owned root and exposes each only to authorized participants

#### Scenario: Department Manager attempts to bypass Team Leaders
- **WHEN** a Department Manager attempts to assign a task directly to an Employee
- **THEN** the system rejects the assignment and creates no child task

#### Scenario: Employee attempts to create a task
- **WHEN** an Employee submits a task-creation request
- **THEN** the system rejects it without creating a task

### Requirement: Task assignees execute and submit their work
The system SHALL place directly assigned work in the assignee's task list as not started. Employees SHALL explicitly start assigned work; a Team Leader or Department Manager SHALL transition an upstream assignment to in progress on first opening its detail. An assignee SHALL maintain progress history and submit a completion description/result for direct-dispatcher acceptance. A manager's own child task SHALL use a simple incomplete/complete marker and SHALL NOT require progress percentage or self-acceptance.

#### Scenario: Employee starts and updates assigned work
- **WHEN** an Employee starts a not-started task and records progress with an update description
- **THEN** the task becomes in progress and the system retains the progress update as history

#### Scenario: Manager first opens an upstream assignment
- **WHEN** an assigned Team Leader or Department Manager opens a not-started task detail for the first time
- **THEN** the task becomes in progress and its actual start time is recorded

#### Scenario: Assignee submits work for acceptance
- **WHEN** an eligible assignee submits a completion description and result
- **THEN** the system sets progress to 100 percent, changes the state to awaiting acceptance, and prevents further progress updates until returned or accepted

#### Scenario: Direct dispatcher returns a submission
- **WHEN** the direct dispatcher returns submitted work with a reason
- **THEN** the task enters needs revision and the assignee can view the reason and resume work

### Requirement: Direct dispatchers control acceptance and task definitions
The system SHALL allow only the direct dispatcher to accept or return a submitted task and to cancel an unfinished task they directly dispatched. Returning or cancelling SHALL require a reason. The task creator or direct dispatcher MAY edit task definition except while awaiting acceptance or after completion. The system SHALL retain the actor, time, and change/decision history.

#### Scenario: Direct dispatcher accepts submitted work
- **WHEN** the direct dispatcher accepts a task awaiting acceptance
- **THEN** the task becomes completed and the acceptance decision is recorded

#### Scenario: Non-dispatcher attempts acceptance or cancellation
- **WHEN** a user other than the direct dispatcher attempts to accept, return, or cancel the task
- **THEN** the system rejects the operation without changing state

#### Scenario: Cancellation cascades only downward
- **WHEN** a direct dispatcher cancels an unfinished parent task with a reason
- **THEN** the system cancels its unfinished descendants with the inherited reason, preserves completed descendants, and leaves tasks outside that task tree unchanged

#### Scenario: A child task is cancelled
- **WHEN** a direct dispatcher cancels an unfinished child task with a reason
- **THEN** the system may cascade to that child's unfinished descendants but SHALL leave the parent and siblings unchanged

### Requirement: Task screens enforce role and relationship visibility
The system SHALL show users tasks they execute or directly dispatch. A manager's task screen SHALL NOT expose descendants dispatched by their subordinate managers or permit operating on those descendant tasks. Parent progress SHALL be maintained manually and SHALL NOT be automatically calculated from child percentages.

#### Scenario: Manager reviews a directly dispatched task
- **WHEN** a Department Manager or Founder opens the dispatched-work list
- **THEN** the system shows the direct assignee's task state and acceptance submission but not lower-level task details

#### Scenario: Manager attempts to access a subordinate's child task
- **WHEN** a manager requests a task record outside their direct task scope through UI or API
- **THEN** the system denies access and returns no child-task data

#### Scenario: Parent task remains manually tracked
- **WHEN** child-task progress or completion changes
- **THEN** the system does not automatically recalculate the parent task percentage
