## Context

The task is a standalone unit of assigned work. Business decomposition is a human process; the app records concrete assignments, not a parent/child task graph. A plain-text association note can preserve context without affecting authorization or lifecycle.

## Goals / Non-Goals

**Goals:** enforce role-bounded assignment, direct-supervisor approval, clear creator/assignee ownership, text-only execution history, separate completion acceptance, auditability, read-only hierarchical visibility, and efficient task-list filtering.

**Non-Goals:** task trees, a split-task operation, automatic or percentage progress, multi-person assignees, recipient acceptance, attachment uploads, general-purpose task search outside subordinate inspection, or cross-department workflow details in this change.

## Decisions

- A task has one creator and exactly one responsible assignee. The creator may assign to self or an allowed direct report; Employee may only assign to self. Cross-department work uses its dedicated entry point and is detailed in the companion change.
- Keep optional `association_note` as ordinary text. Do not add `parent_task_id`, task-tree traversal, roll-up, inherited permissions, or cascade behavior.
- Keep task completion status binary (`unfinished` / `completed`). Approval, returned-for-editing, waiting-for-acceptance, and cancellation are separate workflow/audit records, not additional completion states.
- Use a separate approval record and queues. If the creator has a direct supervisor, every creation requires that supervisor's approval, including self-assigned work. Founder is the top role and bypasses approval.
- Before approval, the creator and approver access the task only in their respective Approval Center queues; it does not appear in ordinary task lists or to the assignee. After approval, it appears in the creator's `Created by Me` and assignee's `My Tasks`. A self-assigned task is one record shown in both views.
- A returned approval requires a reason. The creator edits and resubmits. Changes to assignee, deadline, task details, or expected deliverables after approval require approval again.
- Execution updates are append-only text entries with author and timestamp. Do not store percentage completion.
- Completion submission has its own form and record: completion explanation is required; deliverable information is required only when expected deliverables were specified at creation. MVP has no upload requirement.
- Only the direct dispatcher may accept or return submitted work. Return requires a reason. Self-assigned work is marked complete by its assignee without self-acceptance.
- Direct dispatcher may cancel their unfinished task with a reason. Cancellation archives only that task; there is no cascade because tasks are not physically linked.
- Supervisors may inspect descendant users' tasks read-only. Visibility does not grant approve, edit, accept, return, or cancel authority. Only the creator's direct supervisor approves; only the creator/direct dispatcher accepts.
- Execution/inspection lists (`My Tasks`, `Created by Me`, and subordinate tasks) have a non-mutating completion filter (`all` / `unfinished` / `completed`). Approval queues live in a dedicated Approval Center and are never filtered by task-completion status. The Department Manager's department-scope filter remains independently combinable. The subordinate list's “查看对象” selector defaults to all visible descendants and contains a name-only search over authorized descendants. Selecting one person shows approved tasks assigned to that person; the all-descendants option shows approved tasks assigned to any visible descendant. Creator remains task metadata and does not form a separate inspection list or filter. Search and object selection operate only within the server-authorized visible set.
- `Created by Me` replaces the former dispatched-work label. `My Tasks` is not divided into source categories; cross-department status is a filter for managers.

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
