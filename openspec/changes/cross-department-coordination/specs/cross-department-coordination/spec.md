## ADDED Requirements

### Requirement: Department Managers may assign ordinary tasks to managers in other departments
Using ordinary task creation, a Department Manager SHALL be able to assign a standalone task to themself, a Team Leader in their department, or a Department Manager in another department. They SHALL NOT assign directly to another department's Team Leader or Employee, or to a manager in their own department. The system SHALL NOT add a dedicated task type, flag, entry, filter, API, or parent/child relation for this assignment.

#### Scenario: Department Manager assigns a task to another department's manager
- **WHEN** Department Manager A submits a valid ordinary task with Department Manager B from another department as the assignee
- **THEN** the system creates one ordinary standalone task and routes it to A's direct supervisor for approval

#### Scenario: Department Manager selects an out-of-scope employee
- **WHEN** Department Manager A attempts to assign directly to a Team Leader or Employee in another department
- **THEN** the system rejects the assignment and creates no task

#### Scenario: Department Manager selects a same-department peer
- **WHEN** Department Manager A attempts to assign to another Department Manager in the same department
- **THEN** the system rejects the assignment and creates no task

#### Scenario: Other task roles attempt cross-department manager assignment
- **WHEN** a Team Leader or Employee attempts to assign a task to a manager in another department
- **THEN** the system rejects the assignment and creates no task

### Requirement: Approved assignments use standard task lists and direct-dispatcher acceptance
The system SHALL apply the creator's direct-supervisor approval before an assigned Department Manager can see or execute the task. After approval, the same task SHALL appear in the creator's “我创建的” and assignee's “我的任务”. No second assignee acceptance or dedicated task category SHALL be required.

#### Scenario: Founder approves a Department Manager's task for another manager
- **WHEN** the Founder approves Department Manager A's pending task assigned to B
- **THEN** it leaves the approval queues and appears in A's “我创建的” and B's “我的任务”

#### Scenario: Assignee requests a task before approval
- **WHEN** B requests the task before A's direct supervisor approves it
- **THEN** the system does not expose it in B's task list or allow execution

### Requirement: Cross-department task execution preserves ordinary task boundaries
The assignee MAY execute the task directly or create separate ordinary tasks for eligible people. The requesting manager SHALL NOT see those internal tasks. The assignee SHALL submit completion to the original creator, who SHALL accept or return it with a reason. Creator and assignee details SHALL provide context without a separate task marker.

#### Scenario: Assignee delegates internal work
- **WHEN** B creates an internal task for a Team Leader while carrying out A's assignment
- **THEN** the internal task follows the ordinary approval flow and its details are not disclosed to A

#### Scenario: Assignee submits the result
- **WHEN** B submits completion details and any required deliverable information
- **THEN** the task remains unfinished while A's acceptance is pending and A is notified

#### Scenario: Creator returns the result
- **WHEN** A returns B's submission with a reason
- **THEN** the task remains unfinished, B can see the reason and revise/resubmit, and no internal task status changes automatically

#### Scenario: Creator accepts the result
- **WHEN** A accepts B's submitted result
- **THEN** the task becomes completed and its submission/acceptance history is retained

### Requirement: Assignment visibility does not grant access to another department's internal tasks
The system SHALL allow A to view the task A created and its submitted result, but SHALL NOT allow A to view B's department-internal tasks. The Founder MAY view subordinate tasks read-only and approve tasks created by direct reports, but SHALL NOT thereby become the assignee or acceptance actor. Only A as direct dispatcher may accept or return B's completion submission.

#### Scenario: Requester attempts to inspect assignee's internal task
- **WHEN** A requests a task created by B inside the target department
- **THEN** the system denies access and returns no internal task details

#### Scenario: Founder attempts to accept the result
- **WHEN** the Founder is not the task's direct dispatcher and attempts to accept B's result
- **THEN** the system rejects the operation without changing task or submission state
