## ADDED Requirements

### Requirement: Users create standalone tasks within their assignment scope
The system SHALL allow a Founder to create a task for themself or a Department Manager, a Department Manager to create for themself or a Team Leader in their department, a Team Leader to create for themself or an Employee in their team, and an Employee to create only for themself. Every task SHALL have one creator, one responsible assignee, a title, task details, and a deadline. Expected deliverables and a plain-text association note MAY be provided. The association note SHALL NOT create a task relationship or affect authorization, progress, or cancellation. The system SHALL NOT model task decomposition as a parent/child task tree.

#### Scenario: Manager assigns a concrete task to a direct report
- **WHEN** a Department Manager creates a task for a Team Leader in the same department
- **THEN** the system accepts the permitted assignee and creates one standalone task record

#### Scenario: Employee creates a task for themself
- **WHEN** an Employee submits a valid task assigned to themself
- **THEN** the system proceeds with the required supervisor-approval workflow

#### Scenario: User attempts an out-of-scope assignment
- **WHEN** a user assigns a task to someone other than themself or an allowed direct report
- **THEN** the system rejects the operation and creates no task

#### Scenario: Association note is supplied
- **WHEN** a creator adds a note such as “来源于季度规划会议”
- **THEN** the system stores and displays it as explanatory text only, without creating a parent relation or cascading behavior

### Requirement: Tasks require direct-supervisor approval before assignment visibility
The system SHALL require approval from the creator's direct supervisor for every task created by a user who has a supervisor, regardless of the selected assignee. Founder-created tasks SHALL bypass approval. Pending tasks SHALL appear in the creator's Approval Center “待上级审批” queue and approver's Approval Center “待你审批” queue, but not in “我创建的” or the assignee's task list. Approval rejection SHALL include a reason visible to the creator. The creator MAY edit and resubmit a returned task. After approval, key changes to assignee, deadline, task details, or expected deliverables SHALL require approval again.

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

#### Scenario: Founder creates work
- **WHEN** the Founder creates a valid task for themself or a Department Manager
- **THEN** it bypasses supervisor approval and is visible to its creator and assignee as appropriate

### Requirement: Assignees record text updates and submit work for direct-dispatcher acceptance
An approved task SHALL have only “未完成” or “已完成” as its completion status. The system SHALL support append-only text updates with author and timestamp but no percentage progress. A completion submission SHALL use a form distinct from task creation, require a completion explanation, and require deliverable information only when expected deliverables were specified. Submission SHALL leave the task unfinished until the direct dispatcher accepts it. A return SHALL require a reason and allow the assignee to revise and resubmit. A self-assigned task SHALL be completed by its assignee without self-acceptance.

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

### Requirement: Task lists support completion filtering and scoped subordinate search
The execution and inspection lists “我的任务”, “我创建的”, and “下属任务” SHALL provide a completion-status filter with “全部”, “未完成”, and “已完成” options. Filtering SHALL only affect displayed results and SHALL NOT change task data or workflow state. Approval Center queues SHALL NOT use completion-status filters; they SHALL be organized by approval workflow, independently from task completion state. A Department Manager's department-scope filter MAY be combined with the completion filter. The read-only subordinate-task list SHALL provide an object selector defaulting to “全部下属”. Its selection dialog SHALL allow a supervisor to search authorized descendants by employee name only; it SHALL NOT search task titles, details, creator names, or assignee names. Selectable objects SHALL be limited to users the current supervisor is authorized to inspect, including indirect descendants where applicable. Selecting a specific person SHALL show approved tasks assigned to that person only; “全部下属” SHALL show approved tasks assigned to any authorized descendant. Creator information SHALL remain visible as a task attribute where task details are shown and SHALL NOT create a separate creator-based inspection list. These controls SHALL NOT grant workflow permissions or expand data visibility.

#### Scenario: User filters an execution task list by completion status
- **WHEN** a user selects “已完成” in “我的任务”, “我创建的”, or “下属任务”
- **THEN** only completed tasks are displayed and no task state is modified

#### Scenario: Approval queue is not filtered by completion status
- **WHEN** a user opens an Approval Center queue
- **THEN** approval entries are organized by approval workflow state and no “未完成 / 已完成” task-completion filter is displayed

#### Scenario: Supervisor searches and selects a subordinate by name
- **WHEN** a supervisor enters an employee name in the “查看对象” selector or selects an authorized descendant in “下属任务”
- **THEN** name search filters the authorized people in the selector, and the read-only list shows approved tasks assigned to the selected descendant only, or tasks assigned to any authorized descendant when “全部下属” is selected

#### Scenario: Supervisor attempts to select an out-of-scope person
- **WHEN** a person outside the supervisor's authorized hierarchy is requested as the viewing object
- **THEN** the system rejects or omits that person and returns no task data for them

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
