## ADDED Requirements

### Requirement: Users create standalone tasks within their assignment scope
Assignee eligibility SHALL follow role: Founder—any company member; Department Manager—self, own-department Team Leaders, or other-department Department Managers; Team Leader—self or own-team Employees; Employee—self only. Each task has one creator and one assignee. No task tree or separate cross-department type, flag, entry, filter, or API is added.

#### Scenario: Manager assigns a concrete task to a direct report
- **WHEN** a Department Manager creates a task for a Team Leader in the same department
- **THEN** the system accepts the permitted assignee and creates one standalone task record

#### Scenario: Department Manager assigns a task to another department manager
- **WHEN** a Department Manager creates an ordinary task for a Department Manager in a different department
- **THEN** the system accepts the permitted assignee and routes the task through the creator's direct-supervisor approval flow

#### Scenario: Employee creates a task for themself
- **WHEN** an Employee submits a valid task assigned to themself
- **THEN** the system proceeds with the required supervisor-approval workflow

#### Scenario: Founder assigns work across organization levels
- **WHEN** the Founder assigns a task to any company member, including a Team Leader or Employee in another department
- **THEN** the system accepts that assignee without requiring a direct reporting relationship

#### Scenario: Non-Founder attempts an assignment outside their role scope
- **WHEN** a Department Manager, Team Leader, or Employee assigns a task outside the role-specific scope above
- **THEN** the system rejects the operation and creates no task

#### Scenario: Founder attempts to assign outside the company
- **WHEN** the Founder selects a person who is not a member of the same company
- **THEN** the system rejects the operation and creates no task

#### Scenario: Association note is supplied
- **WHEN** a creator adds a note such as “来源于季度规划会议”
- **THEN** the system stores and displays it as explanatory text only, without creating a parent relation or cascading behavior

### Requirement: Tasks require direct-supervisor approval before assignment visibility
Every task created by a user with a direct supervisor SHALL be approved by that supervisor before execution. Founder-created tasks bypass approval. Pending tasks appear only in the creator's and approver's queues; approved tasks appear in the creator's “我创建的” and assignee's “我的任务”. Returns require a reason and may be revised/resubmitted. Key definition changes after approval require reapproval.

#### Scenario: Self-assigned task waits for approval
- **WHEN** a Department Manager creates a task assigned to themself
- **THEN** the task is visible to the manager as awaiting approval and to their direct supervisor for review, but is not yet executable

#### Scenario: Approver approves a task
- **WHEN** the creator's direct supervisor approves a pending task
- **THEN** the task leaves both approval queues and appears in the creator's “我创建的” and the assignee's “我的任务”; if creator and assignee are the same person, both views reference the same task

#### Scenario: Pending task is not shown in ordinary task lists
- **WHEN** a user creates a task that requires supervisor approval
- **THEN** it appears in the creator's Approval Center “待上级审批” and approver's Approval Center “待你审批”, but not in “我创建的” or “我的任务” before approval

#### Scenario: Approver returns a task without a reason
- **WHEN** an approver attempts to return a pending task without a reason
- **THEN** the system rejects the decision and leaves the task pending

#### Scenario: Founder creates and dispatches work
- **WHEN** the Founder creates a valid task for themself or any member of the same company
- **THEN** it bypasses supervisor approval and is visible to its creator and assignee as appropriate

### Requirement: Approval and incoming assignments generate task notifications
The direct supervisor SHALL receive an unread notification for a task awaiting their approval. An assignee SHALL receive an unread assignment notification only after another user's task is approved and visible in the assignee's task list. Self-assigned work SHALL NOT create an incoming-assignment notification. Reading a notification SHALL NOT change task workflow state.

#### Scenario: Direct subordinate submits a task for approval
- **WHEN** a direct subordinate creates a task that requires the current user’s approval
- **THEN** the current user receives an unread approval notification, while higher-level read-only viewers do not receive an approval notice

#### Scenario: Assignment notification waits for approval
- **WHEN** another user creates a task assigned to a user and the task is still pending approval
- **THEN** the assignee receives no assignment notification until approval makes the task visible in “我的任务”

#### Scenario: Approved task is assigned to another user
- **WHEN** the task is approved and its creator is different from its assignee
- **THEN** the assignee receives an unread assignment notification that opens the task detail without changing its state

### Requirement: Assignees record text updates and submit work for direct-dispatcher acceptance
An approved task SHALL have only “未完成” or “已完成” completion status. The assignee SHALL record append-only text updates with author/time and submit completion details on a form separate from task creation. The task SHALL remain unfinished until the direct dispatcher accepts; return requires a reason. If creator and assignee are the same person, that person completes the task without self-acceptance. Percentage progress is not supported.

#### Scenario: Assignee records an update
- **WHEN** an assignee adds a text update to an unfinished approved task
- **THEN** the task remains unfinished and the system preserves the author, timestamp, and update text without a percentage value

#### Scenario: Assignee submits work
- **WHEN** an assignee submits a completion explanation and any required deliverable information
- **THEN** the task remains unfinished and a pending submission is shown to the direct dispatcher

#### Scenario: Required deliverable is missing
- **WHEN** the task specified expected deliverables and the assignee submits without describing the result
- **THEN** the system rejects the submission and explains which deliverable information is missing

#### Scenario: Direct dispatcher returns the submission
- **WHEN** the direct dispatcher returns submitted work with a reason
- **THEN** the task remains unfinished, the assignee can see the reason, and the assignee may update and resubmit

#### Scenario: Direct dispatcher accepts the submission
- **WHEN** the direct dispatcher accepts the submitted work
- **THEN** the task becomes completed and the system retains the completion and acceptance records

### Requirement: Creation, visibility, and workflow authority remain distinct
The system SHALL show approved work in “我的任务” to its assignee and in “我创建的” to its creator. Users with supervisory hierarchy MAY inspect tasks of users below them read-only. This visibility SHALL NOT grant task-edit, approval, completion-acceptance, return, or cancellation permissions. Only the creator's direct supervisor SHALL approve; only the direct dispatcher SHALL accept or return completion. The creator/assignee MAY mark a self-assigned task complete.

#### Scenario: Supervisor views a descendant task
- **WHEN** a supervisor opens a task assigned to a user below them in the organization hierarchy
- **THEN** the system displays task information read-only and does not expose workflow actions unless the supervisor is also the authorized actor for that action

#### Scenario: Higher-level manager attempts acceptance
- **WHEN** a higher-level manager who is not the direct dispatcher accepts or returns submitted work
- **THEN** the system rejects the operation without changing the task or submission

#### Scenario: User views task lists
- **WHEN** a user opens the task center
- **THEN** “我的任务” contains approved tasks assigned to them, “我创建的” contains only their approved or approval-exempt created tasks, and approval queues are accessed separately in the Approval Center

### Requirement: Task lists use a unified scoped filter form
The ordinary task-center list SHALL provide one filter-form entry for task scope, optional inclusive deadline range, and completion status. Defaults SHALL be “我的任务”, unrestricted deadline, and all statuses. “全部” SHALL de-duplicate the union of the current user's approved assigned and created tasks. Filtering SHALL NOT mutate tasks or grant workflow authority.

#### Scenario: User filters ordinary tasks by scope, deadline, and status
- **WHEN** a user applies one or more conditions in the ordinary task filter form
- **THEN** the task list contains only approved tasks matching the selected scope, inclusive deadline range, and completion status, without changing task state

#### Scenario: All scope de-duplicates a self-assigned task
- **WHEN** a task is both assigned to and created by the current user and the user selects “全部”
- **THEN** that task appears once in the results

#### Scenario: Approval queue remains separate from ordinary filters
- **WHEN** a user opens an Approval Center queue
- **THEN** approval entries are organized by approval workflow state and do not show the ordinary-task filter form or task-completion filter

### Requirement: Subordinate task inspection uses authorized employee filters
Only users with descendants SHALL see this read-only shortcut. Its filter SHALL offer “承担的任务” and “创建的任务” (default assigned), an optional inclusive deadline range, completion status, and an employee selector restricted to authorized descendants (default all). Both modes SHALL show only approved tasks, apply date/status filters, and grant no workflow actions or expanded permissions.

#### Scenario: Supervisor views an authorized descendant's assigned tasks by default
- **WHEN** a supervisor opens subordinate inspection with the default “承担的任务” scope and selects an authorized descendant
- **THEN** the read-only list shows approved tasks assigned to that descendant only, or tasks assigned to any authorized descendant when “全部下属” is selected

#### Scenario: Supervisor switches to an authorized descendant's created tasks
- **WHEN** a supervisor selects “创建的任务” for an authorized descendant
- **THEN** the read-only list shows approved tasks created by that descendant, regardless of which permitted user is assigned the work

#### Scenario: Supervisor searches for an authorized descendant by name
- **WHEN** a supervisor searches an employee name in the subordinate filter form
- **THEN** the picker filters only authorized descendants and does not search task titles, task details, or people outside the authorized hierarchy

#### Scenario: Supervisor attempts to select an out-of-scope person
- **WHEN** a person outside the supervisor's authorized hierarchy is requested as the viewing object
- **THEN** the system rejects or omits that person and returns no task data for them

### Requirement: Task-center shortcuts and creation entry remain available in context
The task center SHALL place the Approval Center and authorized subordinate-inspection shortcuts in the top-right area. The ordinary task list SHALL expose task creation through a fixed circular plus button at the bottom-right of the screen instead of a task-list footer button. The creation button SHALL be hidden in Approval Center, task detail, and task create/edit screens.

#### Scenario: User opens approval or subordinate task inspection
- **WHEN** a user with the corresponding permission opens the ordinary task list
- **THEN** the top-right area provides the Approval Center and/or subordinate-inspection shortcut appropriate to that user's role

#### Scenario: User opens task creation from the list
- **WHEN** a user taps the fixed bottom-right plus button on an ordinary task list
- **THEN** the unified task creation form opens and the list-footer creation button is absent

### Requirement: Direct dispatchers cancel only their own unfinished task without cascading
The system SHALL allow a direct dispatcher to cancel their own unfinished task with a required reason. The system SHALL preserve and archive the task record. Cancellation SHALL NOT alter any other task. MVP SHALL NOT add a separate recipient accept/reject step, completed-task reopening, submission withdrawal, or cascading cancellation.

#### Scenario: Direct dispatcher cancels an unfinished task
- **WHEN** the direct dispatcher cancels an unfinished task with a reason
- **THEN** the system records the reason and cancellation event and archives only that task

#### Scenario: Cancellation is attempted without a reason
- **WHEN** the direct dispatcher attempts cancellation without a reason
- **THEN** the system rejects the operation and leaves the task unchanged

#### Scenario: Non-dispatcher attempts cancellation
- **WHEN** a user who is not the direct dispatcher attempts to cancel a task
- **THEN** the system rejects the operation and returns no cancellation capability
