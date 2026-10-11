## Why

The task workflow has been simplified after product-owner review. Work decomposition happens in meetings and should not create a physical task tree in the app. The product needs one consistent task-creation flow, explicit supervisor approval, clear ownership, text-only execution updates, and direct-dispatcher acceptance.

## What Changes

- Define task creation for Founder, Department Manager, Team Leader, and Employee with role-bounded assignees.
- Require approval from the creator's direct supervisor for every task they create; Founder-created tasks bypass approval.
- Add a dedicated Approval Center for creator/approver queues; keep only approved tasks in `Created by Me` and `My Tasks`.
- Add non-mutating unfinished/completed filters to execution/task-inspection lists only; subordinate inspection additionally supports choosing a visible descendant through an object selector with scoped employee-name search, and lists only tasks assigned to the selected person.
- Replace task percentage progress with timestamped text updates; keep completion status binary.
- Keep association to prior work as optional explanatory text, not a task relationship.
- Retain direct-dispatcher completion acceptance/return, required return reasons, and non-cascading cancellation.
- Allow authorized leaders to inspect subordinate task records read-only without inheriting workflow powers.
- Keep cross-department task creation in its own entry point; its lifecycle is specified by `cross-department-coordination`.

## Capabilities

### New Capabilities
- `task-dispatch-and-acceptance`: Unified task creation, approval, assignment, execution updates, completion submission, acceptance, and cancellation.

### Modified Capabilities
- None.

## Impact

- Source requirement: `docs/requirements/01-task-creation-and-acceptance.md`.
- Affects Django task and approval APIs, authorization, task history, reminders, and Android task-center screens.
- Depends on identity and organization relationships to resolve the creator's direct supervisor and permitted assignees.
- Cross-department tasks use the same task and approval lifecycle with a dedicated entry point; see `cross-department-coordination`.
