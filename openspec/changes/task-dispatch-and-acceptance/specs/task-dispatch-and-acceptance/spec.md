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

### Requirement: Estimated task start is optional and does not exceed the deadline
The task form SHALL allow an optional `estimated_start_at` and require `due_at`. If an estimated start is supplied, it SHALL NOT be later than the deadline.

#### Scenario: Creator provides an estimated start time
- **WHEN** a creator sets an estimated start time no later than the required deadline
- **THEN** the task stores and displays that planned time

#### Scenario: Creator omits an estimated start time
- **WHEN** a creator leaves the estimated start time empty
- **THEN** the task is created with no estimated start time

#### Scenario: Estimated start is later than the deadline
- **WHEN** a creator submits an estimated start time later than the deadline
- **THEN** the system rejects the task definition and creates no task

### Requirement: Task date and time selectors match their precision
The creation and edit form SHALL use year-month-day and hour-minute wheels to select estimated start and deadline to minute precision. Ordinary and subordinate task-list estimated-start and deadline filters SHALL use year-month-day wheels only and SHALL remain precise to the day.

#### Scenario: Creator selects a task schedule
- **WHEN** a creator selects an estimated start or deadline in the task form
- **THEN** the date and time wheels allow selection to the minute

#### Scenario: User selects a task date filter
- **WHEN** a user selects either boundary of an estimated-start or deadline range
- **THEN** the date wheel selects a calendar day without hour or minute controls

#### Scenario: Estimated-start range excludes tasks with no estimate
- **WHEN** a user applies either estimated-start date boundary
- **THEN** tasks whose `estimated_start_at` is null are excluded from the filtered results

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
The direct supervisor SHALL receive an unread notification for a task awaiting their approval. An assignee SHALL receive an unread assignment notification only after another user's task is approved and visible in the assignee's task list. Self-assigned work SHALL NOT create an incoming-assignment notification. Browsing the notification list SHALL NOT mark notices read; opening one notice SHALL mark only that notice read and SHALL NOT change task workflow state.

#### Scenario: Direct subordinate submits a task for approval
- **WHEN** a direct subordinate creates a task that requires the current user’s approval
- **THEN** the current user receives an unread approval notification, while higher-level read-only viewers do not receive an approval notice

#### Scenario: Assignment notification waits for approval
- **WHEN** another user creates a task assigned to a user and the task is still pending approval
- **THEN** the assignee receives no assignment notification until approval makes the task visible in “我的任务”

#### Scenario: Approved task is assigned to another user
- **WHEN** the task is approved and its creator is different from its assignee
- **THEN** the assignee receives an unread assignment notification that opens the task detail without changing its state

### Requirement: Approval notifications represent individual rounds
Each approval notification SHALL identify its approval round and show that round's decision. Resubmitting a returned task SHALL create a distinct unread approval notification for the current direct supervisor; older notifications SHALL retain their own round decision.

#### Scenario: A returned task is resubmitted for approval
- **WHEN** the creator edits a returned task and submits it again
- **THEN** a new pending approval notification is created for the current direct supervisor, while the earlier notification retains its returned decision

### Requirement: Task creators receive approval outcome notifications
After each approval decision, the system SHALL create an unread result notification for the task creator: `approval_approved` or `approval_returned`. Each notice SHALL retain its approval round and decision comment after later resubmission; a return notice SHALL include the return reason and open the creator's task detail.

#### Scenario: Creator is notified when a task is approved
- **WHEN** the direct supervisor approves a task
- **THEN** the creator receives an unread approval-approved notification for that round, and the assignee receives the existing assignment notification when applicable

#### Scenario: Creator is notified when a task is returned
- **WHEN** the direct supervisor returns a task with a reason
- **THEN** the creator receives an unread approval-returned notification with that reason, which opens the creator's task detail

#### Scenario: Approval result remains attached to its round
- **WHEN** the creator resubmits a returned task for another approval round
- **THEN** the earlier creator result notice retains its original decision and reason, and the new round has its own pending approval notice

### Requirement: Task details show information appropriate to the viewer
The Android task detail SHALL select status/history and execution sections using the viewer's creator, approver, assignee, and read-only relationships. It SHALL display the planned estimated start when present and the deadline. Every action SHALL remain subject to server-side authorization. The MVP SHALL NOT expose a separate actual task-start state, timestamp, or action.

#### Scenario: Creator views a pending or returned task
- **WHEN** the task creator opens a task awaiting approval or returned for revision
- **THEN** the detail shows approval state, approval rounds, and decision comments without completion state or execution sections

#### Scenario: Approver views a pending task
- **WHEN** the designated direct supervisor opens a task awaiting their approval
- **THEN** the detail shows approval state/history and offers approval actions only while the current round is pending

#### Scenario: Assignee views an approved task
- **WHEN** the assignee who is not the creator opens an approved task
- **THEN** the detail shows the planned estimated start when present, deadline, execution, and completion information but omits approval state, approval history, and the standalone task-start action

#### Scenario: Read-only supervisor views an approved descendant task
- **WHEN** a supervisor who is neither creator nor designated approver opens an authorized descendant's approved task
- **THEN** the detail shows authorized task/execution information without approval records or workflow actions

#### Scenario: Founder-exempt task has no approval record
- **WHEN** the Founder or assignee opens a Founder-created task that bypassed approval
- **THEN** the detail omits the approval-status badge and empty approval-history section

#### Scenario: Assignee starts working on an approved task
- **WHEN** the assignee opens an approved task to record progress or submit work
- **THEN** the detail offers the relevant execution actions without a separate “开始任务” button or start timestamp

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
The ordinary task-center list SHALL provide one filter-form entry for task scope, optional inclusive estimated-start and deadline ranges, and completion status. Defaults SHALL be “我的任务”, unrestricted date ranges, and all statuses. A set estimated-start boundary SHALL exclude tasks without `estimated_start_at`. “全部” SHALL de-duplicate the union of the current user's approved assigned and created tasks. Filtering SHALL NOT mutate tasks or grant workflow authority.

#### Scenario: User filters ordinary tasks by scope, deadline, and status
- **WHEN** a user applies one or more conditions in the ordinary task filter form
- **THEN** the task list contains only approved tasks matching the selected scope, any inclusive estimated-start and deadline ranges, and completion status, without changing task state

#### Scenario: All scope de-duplicates a self-assigned task
- **WHEN** a task is both assigned to and created by the current user and the user selects “全部”
- **THEN** that task appears once in the results

#### Scenario: Approval queue remains separate from ordinary filters
- **WHEN** a user opens an Approval Center queue
- **THEN** approval entries are organized by approval workflow state and do not show the ordinary-task filter form or task-completion filter

### Requirement: Subordinate task inspection uses authorized employee filters
Only users with descendants SHALL see this read-only shortcut. Its filter SHALL offer “承担的任务” and “创建的任务” (default assigned), optional inclusive estimated-start and deadline ranges, completion status, and an employee selector restricted to authorized descendants (default all). A set estimated-start boundary SHALL exclude tasks without `estimated_start_at`. Both modes SHALL show only approved tasks, apply date/status filters, and grant no workflow actions or expanded permissions.

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

### Requirement: Ordinary and subordinate task lists use a horizontally scrollable table
The ordinary task-center list and read-only subordinate task list SHALL render tasks in rows with title, creator, assignee, estimated-start, deadline, and completion-status columns. The header and rows SHALL scroll horizontally as one aligned table. Selecting a row SHALL open the existing role-aware task detail. The Approval Center SHALL retain its dedicated layout.

#### Scenario: User views an ordinary task list on a narrow screen
- **WHEN** the user opens an ordinary task list on a screen narrower than the table
- **THEN** the user can horizontally scroll the aligned header and task rows to inspect all six columns

#### Scenario: Supervisor views subordinate tasks
- **WHEN** the supervisor opens an authorized subordinate task list
- **THEN** each approved task uses the same table columns and remains read-only

#### Scenario: Task has no estimated start time
- **WHEN** an ordinary or subordinate task has no `estimated_start_at`
- **THEN** its estimated-start cell displays “未设置”

#### Scenario: Cancelled task remains identifiable
- **WHEN** a listed task has been cancelled
- **THEN** the row retains its completion status and also indicates “已取消”

#### Scenario: User opens a task from the table
- **WHEN** the user selects a task row
- **THEN** the app opens the existing task detail and preserves the detail's current role-based content and actions

#### Scenario: User opens Approval Center
- **WHEN** the user opens an approval queue
- **THEN** the app retains the dedicated approval-list presentation instead of rendering the ordinary task table

### Requirement: Task-center shortcuts and creation entry remain available in context
The task center SHALL place the Approval Center and authorized subordinate-inspection shortcuts in the top-right area. The ordinary task list SHALL expose task creation through a fixed circular plus button at the bottom-right of the screen instead of a task-list footer button. The creation button SHALL be hidden in Approval Center, task detail, and task create/edit screens.

#### Scenario: User opens approval or subordinate task inspection
- **WHEN** a user with the corresponding permission opens the ordinary task list
- **THEN** the top-right area provides the Approval Center and/or subordinate-inspection shortcut appropriate to that user's role

#### Scenario: User opens task creation from the list
- **WHEN** a user taps the fixed bottom-right plus button on an ordinary task list
- **THEN** the unified task creation form opens and the list-footer creation button is absent

### Requirement: Task notifications follow the task workflow lifecycle
The system SHALL create notifications for applicable task lifecycle events. Superseded notifications SHALL be resolved without changing their read state, remain in history, and be excluded from unread counts and unread-only results.

#### Scenario: A pending task is cancelled
- **WHEN** the creator cancels a task that is waiting for direct-supervisor approval
- **THEN** the pending approval notification is resolved and the pending approver receives an unread cancellation notification with the reason and no approval action

#### Scenario: An approved assignment is cancelled
- **WHEN** the creator cancels an approved task assigned to another user
- **THEN** outstanding assignment or submission reminders are resolved and the assignee receives an unread cancellation notification with the reason

#### Scenario: A returned task is cancelled before approval
- **WHEN** the creator cancels a task returned for editing before it is approved
- **THEN** the prior approval notice remains resolved and no cancellation notice is sent to the assignee, who has not yet received the task

#### Scenario: An assignee submits a result
- **WHEN** an assignee submits a result for direct-dispatcher acceptance
- **THEN** the direct dispatcher receives an unread notification linked to that submission

#### Scenario: A submission is accepted or returned
- **WHEN** the direct dispatcher accepts or returns a submitted result
- **THEN** the assignee receives an unread notification for that decision, and a return notification includes the return reason

#### Scenario: A returned result is resubmitted
- **WHEN** the assignee submits a new result after a return
- **THEN** the previous return notification is resolved and a new pending-acceptance notification is created

#### Scenario: A self-assigned task is completed
- **WHEN** the assignee submits their own task for self-completion
- **THEN** the system completes the task without sending a result or acceptance notification to that same user

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
