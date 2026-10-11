## Why

Departments need a controlled way to request work from one another without assigning directly to another department's employees. Product-owner review settled on treating cross-department work as a normal standalone task with a dedicated Department Manager entry point, not a separate request that the receiving manager must accept.

## What Changes

- Add a dedicated “创建跨部门任务” entry for Department Managers, assigning to a Department Manager in another department.
- Reuse the regular task fields, direct-supervisor approval, execution update, completion submission, and direct-dispatcher acceptance/return lifecycle.
- The creator's direct supervisor (Founder) approves the task; approval automatically places it in the target manager's “我的任务—跨部门工作”.
- The target manager may execute directly or create ordinary internal tasks for self/team leaders; internal tasks remain invisible to the requesting department.
- Keep the cross-department flag as a manager-facing filter; do not create a physical link to internal tasks.

## Capabilities

### New Capabilities
- `cross-department-coordination`: Dedicated cross-department task creation and its integration with the ordinary task lifecycle.

### Modified Capabilities
- None.

## Impact

- Source requirement: `docs/requirements/01-task-creation-and-acceptance.md`.
- Affects the Android Department Manager task center, task assignment validation, approval integration, organization-based visibility, and direct-dispatcher acceptance.
- Depends on `task-dispatch-and-acceptance`; it does not define a second task or acceptance model.
