## Why

The task workflow has been simplified after product-owner review. Work decomposition happens in meetings and should not create a physical task tree in the app. The product needs one consistent task-creation flow, explicit supervisor approval, clear ownership, text-only execution updates, and direct-dispatcher acceptance.

## What Changes

- Define task creation for Founder, Department Manager, Team Leader, and Employee with role-bounded assignees; Founder may select any member of the company, and a Department Manager may also select a Department Manager from another department.
- Require approval from the creator's direct supervisor for every task they create; Founder-created tasks bypass approval.
- Define task notifications for direct-subordinate approvals and newly approved work assigned to another user; keep notification read state separate from task workflow state.
- Add a dedicated Approval Center for creator/approver queues; keep only approved tasks in `Created by Me` and `My Tasks`.
- Use one filter form for ordinary task scope, deadline range, and completion state; subordinate inspection adds a task scope toggle (`承担的任务` / `创建的任务`, default assigned) and employee-name selection restricted to authorized descendants. Keep approval queues outside ordinary task filters.
- Place Approval Center and subordinate-inspection shortcuts in the task-center top-right area, with a fixed bottom-right plus button for task creation on ordinary lists.
- Replace task percentage progress with timestamped text updates; keep completion status binary.
- Present task details according to the viewer's relationship to the task; remove the standalone start-time action because task execution has no separate start state in the MVP.
- Keep association to prior work as optional explanatory text, not a task relationship.
- Retain direct-dispatcher completion acceptance/return, required return reasons, and non-cascading cancellation.
- Allow authorized leaders to inspect subordinate task records read-only without inheriting workflow powers.
- Keep assignments between Department Managers in different departments within the unified task-creation flow; permission and visibility details are specified by `cross-department-coordination`.

## Capabilities

### New Capabilities
- `task-dispatch-and-acceptance`: Unified task creation, approval, assignment, execution updates, completion submission, acceptance, and cancellation.

### Modified Capabilities
- None.

## Impact

- Source requirement: `docs/requirements/01-task-creation-and-acceptance.md`.
- Affects Django task and approval APIs, authorization, task history, notification read state, and Android task-center/Home notification screens.
- Depends on identity and organization relationships to resolve the creator's direct supervisor and permitted assignees.
- Department Manager assignments to another department's manager use the same endpoint, form, approval lifecycle, task lists, and completion handling; see `cross-department-coordination`.
