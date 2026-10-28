## Context

The task is a standalone unit of assigned work. Business decomposition is a human process; the app records concrete assignments, not a parent/child task graph. A plain-text association note can preserve context without affecting authorization or lifecycle.

## Goals / Non-Goals

**Goals:** enforce role-bounded assignment (company-wide for Founder; self, same-department Team Leaders, or other-department Department Managers for Department Managers; team-scoped for Team Leaders; self-only for Employees), direct-supervisor approval, clear creator/assignee ownership, text-only execution history, separate completion acceptance, auditability, read-only hierarchical visibility, and efficient task-list filtering.

**Non-Goals:** task trees, a split-task operation, automatic or percentage progress, multi-person assignees, recipient acceptance, attachment uploads, general-purpose task search outside subordinate inspection, or cross-department workflow details in this change.

## Decisions

- A task has one creator and exactly one responsible assignee. Founder may assign to themself or any member of the same company, regardless of department or reporting level. A Department Manager may assign to themself, a Team Leader in their department, or a Department Manager in another department; a Team Leader may assign to themself or an Employee in their team; Employee may only assign to themself. These broader Founder assignment rights do not permit selecting a person outside the company. Department Manager assignments to another department's manager use the unified entry and are detailed in the companion change.
- Keep optional `association_note` as ordinary text. Do not add `parent_task_id`, task-tree traversal, roll-up, inherited permissions, or cascade behavior.
- Keep task completion status binary (`unfinished` / `completed`). Approval, returned-for-editing, waiting-for-acceptance, and cancellation are separate workflow/audit records, not additional completion states.
- Use a separate approval record and queues. If the creator has a direct supervisor, every creation requires that supervisor's approval, including self-assigned work. Founder is the top role and bypasses approval.
- Before approval, the creator and approver access the task only in their respective Approval Center queues; it does not appear in ordinary task lists or to the assignee. After approval, it appears in the creator's `Created by Me` and assignee's `My Tasks`. A self-assigned task is one record shown in both views.
- A returned approval requires a reason. The creator edits and resubmits. Changes to assignee, deadline, task details, or expected deliverables after approval require approval again.
- Generate an unread approval notification for the creator's direct supervisor when a subordinate task awaits approval. Generate an unread assignment notification for the assignee only after another user's task is approved and appears in their task list; self-assigned tasks do not create an incoming-assignment notice. Opening the notification list marks the currently displayed notices read; reading never changes task or approval state.
- Execution updates are append-only text entries with author and timestamp. Do not store percentage completion.
- Completion submission has its own form and record: completion explanation is required; deliverable information is required only when expected deliverables were specified at creation. MVP has no upload requirement.
- Only the direct dispatcher may accept or return submitted work. Return requires a reason. Self-assigned work is marked complete by its assignee without self-acceptance.
- Direct dispatcher may cancel their unfinished task with a reason. Cancellation archives only that task; there is no cascade because tasks are not physically linked.
- Task notifications follow workflow state as well as read state. Cancellation resolves outstanding approval, assignment, submission, and return notices, then sends one unread cancellation notice to the current pending approver or, for an approved assignment, the other-person assignee. Completion submission resolves the new-assignment notice and notifies the direct dispatcher; acceptance or return notifies the assignee. Return notices are resolved when a new submission is made. Self-assigned completion creates no self-notices. Resolved notices remain in history but are excluded from unread counts and unread-only results; resolving a notice does not mark it read.
- Supervisors may inspect descendant users' tasks read-only. Visibility does not grant approve, edit, accept, return, or cancel authority. Only the creator's direct supervisor approves; only the creator/direct dispatcher accepts.
- Ordinary task lists do not show permanent category/status tabs. One filter form handles the main list's task scope (`全部` / `我的任务` / `我创建的`), optional inclusive deadline range, and completion state (`全部` / `未完成` / `已完成`). `全部` is the de-duplicated union of the current user's approved assigned and created tasks. Defaults are `我的任务`, any deadline, and all completion states. The subordinate read-only list has the same date/status filters, a task-scope toggle (`承担的任务` / `创建的任务`, default assigned), and a name-search target selector defaulting to all authorized descendants. In assigned mode, filter tasks by the selected person's assignee identity; in created mode, filter tasks by their creator identity. Both modes show only approved tasks; pending/returned tasks stay in the Approval Center. The all-descendants option applies the selected mode across all visible descendants. Search and object selection operate only within the server-authorized visible set. The assignment-recipient set and the subordinate-visibility set are independently validated by their existing role rules: e.g. a Department Manager may assign to another department's manager, but that peer manager is not thereby a visible subordinate.
- Place Approval Center and subordinate-inspection shortcuts in the task-center top-right area, and expose task creation through a fixed bottom-right circular plus button on ordinary list screens. Do not show the creation FAB in approval, detail, or task-form screens. Approval queues remain separate and do not use ordinary-list filters.
- `Created by Me` replaces the former dispatched-work label. `My Tasks` is not divided into department-source categories; assignments from other Department Managers are ordinary tasks and have no separate marker or filter.

## Data and Authorization Boundaries

Implementation should represent task definition, approval history, text updates, completion submissions/decisions, and cancellation audit separately as needed. This design does not prescribe concrete database columns. The service must enforce authorization on every operation; UI hiding is not a security boundary.

## Risks / Trade-offs

- Approval adds a gate before assignment visibility; show the task in both creator and approver queues inside the separate Approval Center and notify both sides to make the wait explicit.
- Read-only hierarchical visibility is broader than direct assignment visibility; apply organization-scope filtering and keep workflow actions tied to creator/assignee relationships.
- Text association is less queryable than a foreign key, intentionally avoiding artificial task-tree coupling and complex cascading behavior.

## Migration Plan

This is an MVP greenfield workflow. Implement organization/supervisor lookup first, then task creation and approval, then execution and acceptance. Do not introduce task-tree migration logic. Retain audit history for all workflow decisions.

## Open Questions

No product-level questions remain for this change. API shape, database field names, and notification implementation are implementation details to settle during delivery.
