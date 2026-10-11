## Context

Cross-department work is a standalone task created by one Department Manager for another Department Manager through a dedicated entry point. It reuses the ordinary task, approval, execution, and acceptance lifecycle. No independent accept/reject request state machine is needed.

## Goals / Non-Goals

**Goals:** restrict cross-department creation to Department Managers, require Founder approval, expose approved tasks to the target manager, protect target-department internal tasks, and return the final result to the original direct dispatcher for acceptance.

**Non-Goals:** direct assignment to another department's Team Leader or Employee, target-side acceptance, physical association between the cross-department task and internal tasks, task-tree roll-up, cancellation cascade, or Founder exclusion from approval/read-only visibility.

## Decisions

- Department Manager A uses a separate “创建跨部门任务” entry and selects Department Manager B in another department as the sole assignee. The task has the ordinary task fields plus a cross-department flag.
- Because A has a direct supervisor, the task first appears in A's Approval Center “待上级审批” and Founder's Approval Center “待你审批”. It does not appear in A's “我创建的” before approval. Founder approval is required before B sees or executes it. B does not accept it a second time.
- After approval the same task appears in A's “我创建的” and B's “我的任务—跨部门工作”.
- B may execute it and submit the completion description/material information directly to A, or create new standalone local tasks using the normal task-creation flow. B-created work must receive its own normal supervisor approval. Use optional text association notes if context is useful; no foreign-key task tree is created.
- A can inspect the cross-department task A created and B's submitted result, but cannot view B's internal tasks. Founder has read-only hierarchical visibility and approves direct reports' creations; that does not make Founder the assignee or acceptance actor.
- Only direct dispatcher A may accept or return B's submission; return requires a reason. Task completion remains binary and execution updates remain text-only.
- The cross-department flag is a filter for Department Managers, not an alternate task status or linkage.

## Authorization and Data Boundaries

Validate that creator and assignee are Department Managers in different departments. Use the ordinary task's creator, assignee, supervisor, approval, and submission relationships. Do not expose target department internal tasks to A through queries, UI, or APIs.

## Risks / Trade-offs

- A dedicated entry can be mistaken for a separate approval flow; keep its form and state labels aligned with ordinary task creation.
- B may need multiple local assignments to complete the requested work; optional text notes preserve context without imposing artificial linkage or cancellation semantics.
- Founder approval can add latency; explicit pending queues and reminders make the gate visible.

## Migration Plan

Implement after the regular task and approval foundation. No separate request tables or conversion migration are required for the MVP. Preserve ordinary task audit history.

## Open Questions

No product-level questions remain. Concrete API and database design are implementation details.
