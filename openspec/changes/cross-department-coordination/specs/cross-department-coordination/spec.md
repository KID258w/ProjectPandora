## ADDED Requirements

### Requirement: Department Managers create cross-department tasks through a dedicated entry
The system SHALL provide a dedicated cross-department task-creation entry to Department Managers. A creator SHALL select one Department Manager in a different department as the assignee. The cross-department item SHALL be a standalone task using the regular task fields and lifecycle with a cross-department flag. It SHALL NOT be represented as a separate accept/reject request or as a physical parent/child task relation.

#### Scenario: Manager creates a cross-department task
- **WHEN** Department Manager A submits a valid task for Department Manager B in another department
- **THEN** the system creates one standalone cross-department task and sends it for approval by A's direct supervisor

#### Scenario: Manager selects a same-department recipient
- **WHEN** a Department Manager uses the cross-department entry but selects a manager in the same department
- **THEN** the system rejects the selection and does not create the task

#### Scenario: Non-manager uses the cross-department entry
- **WHEN** a Team Leader or Employee attempts to create a cross-department task
- **THEN** the system denies the operation and creates no task

### Requirement: Approved cross-department tasks enter the target manager's task list without a second acceptance
The system SHALL require the task creator's direct-supervisor approval before the target manager can see or execute the task. Before approval, the task SHALL appear only in the creator's and approver's respective Approval Center queues, not in the creator's “我创建的” or target manager's task list. Founder-created tasks bypass approval according to the common task rules. After approval, the same task SHALL appear in the creator's “我创建的” and target manager's “我的任务—跨部门工作”. The target manager SHALL NOT need to accept it again.

#### Scenario: Founder approves a manager's cross-department task
- **WHEN** Founder approves Department Manager A's pending cross-department task for B
- **THEN** it becomes visible to B in “我的任务—跨部门工作” and to A in “我创建的”

#### Scenario: Target manager attempts to view a pending task
- **WHEN** B requests the task before A's direct supervisor approves it
- **THEN** the system does not expose it in B's task list or allow execution

### Requirement: Cross-department execution uses the ordinary submission and acceptance workflow
The target manager MAY execute the task directly or create ordinary internal tasks for self or eligible direct reports. Internal tasks SHALL use their own ordinary approval workflow and SHALL NOT be visible to the requesting manager. The target manager SHALL submit the cross-department task's completion result to the original creator, who is its direct dispatcher. The creator MAY accept or return it with a required reason.

#### Scenario: Target manager delegates local work
- **WHEN** B creates an internal task for a Team Leader while carrying out the cross-department assignment
- **THEN** the internal task follows ordinary supervisor approval and is not disclosed to A

#### Scenario: Target manager submits result
- **WHEN** B submits the cross-department task with a completion explanation and any required deliverable details
- **THEN** the task remains unfinished while A's acceptance is pending and A is notified

#### Scenario: Requesting manager returns result
- **WHEN** A returns B's submission with a reason
- **THEN** the task remains unfinished, B can see the reason and revise/resubmit, and no internal task status is changed automatically

#### Scenario: Requesting manager accepts result
- **WHEN** A accepts B's submitted result
- **THEN** the cross-department task becomes completed and its submission/acceptance history is retained

### Requirement: Cross-department task visibility does not grant internal access or workflow authority
The system SHALL allow A to view the task A created and its submitted result, but SHALL NOT allow A to view B's department-internal tasks. The Founder MAY view subordinate tasks read-only and approve tasks created by direct reports, but SHALL NOT thereby become the assignee or acceptance actor. Only A as direct dispatcher may accept or return B's completion submission.

#### Scenario: Requester attempts to inspect target internal tasks
- **WHEN** A requests a task created by B inside the target department
- **THEN** the system denies access and returns no internal task details

#### Scenario: Founder attempts to accept the cross-department result
- **WHEN** Founder is not the task's direct dispatcher and attempts to accept B's result
- **THEN** the system rejects the operation without changing task or submission state
