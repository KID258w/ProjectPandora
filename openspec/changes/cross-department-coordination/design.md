## Context

Cross-department collaboration is represented by an ordinary standalone task assigned from one Department Manager to another. The common task model, endpoint, form, approval center, execution, and acceptance workflow remain authoritative. No separate “cross-department task” type is stored or shown.

## Goals / Non-Goals

**Goals:** permit controlled Department Manager-to-Department Manager assignment across departments; require the creator's direct-supervisor approval; expose the approved task to the assignee; protect the target department's internal tasks; and return completion to the direct dispatcher for acceptance.

**Non-Goals:** direct assignment to another department's Team Leader or Employee by a Department Manager; target-side acceptance; a cross-department boolean/type/filter/entry/API; physical association between the assigned task and internal tasks; task-tree roll-up; cancellation cascade; or Founder exclusion from approval/read-only visibility.

## Decisions

- Department Manager A uses the ordinary “创建任务” form and may select themself, a Team Leader in A's department, or a Department Manager in another department. A may not assign to a Team Leader or Employee in another department. Other roles keep their existing assignment scopes; the Founder can assign to any company member.
- A task assigned to another Department Manager has only the ordinary task attributes. The API and database do not store or accept a cross-department flag, and the client does not offer a special entry or filter. Creator, assignee, and their organization information provide the necessary context in task details.
- Because A has a direct supervisor, the task appears in A's “待上级审批” and the Founder's “待你审批”. Before approval it is not in A's “我创建的” or B's “我的任务”. After approval, the same task appears in A's “我创建的” and B's “我的任务”; B does not accept it a second time.
- B may execute the assigned task directly or create new standalone local tasks for themself or eligible direct reports using the ordinary form and approval flow. Optional association notes may explain context; there is no foreign-key task tree or cascade. A cannot inspect B's internal tasks.
- Only direct dispatcher A may accept or return B's submission; return requires a reason. Completion remains binary and execution updates remain text-only.
- Home ordering treats tasks created by someone else and assigned to the current user as “他人派发”, without a separate cross-department category. Month-view colors distinguish “他人派发” from “本人创建” only.

## Authorization and Data Boundaries

Validate both role and organization at the service layer: a Department Manager may target a manager in a different department; the target must belong to the same company and be active. Keep ordinary creator, assignee, approval, submission, and audit relationships as the source of truth. Do not expose target-department internal tasks to A through queries, UI, or APIs. Organization snapshots, if stored for historical audit, are not task-type markers and must not drive a separate category filter.

## Risks / Trade-offs

- Without a special label, users rely on creator/assignee details to understand where a task came from; show those fields consistently in task details.
- Founder approval can add latency; the common approval center and reminders make the gate visible.
- A may see only the task A directly assigned and B's submitted result, not B's internal execution breakdown; this is intentional and avoids artificial task-tree coupling.

## Migration Plan

Use the existing task and approval model. New assignments use the ordinary task creation endpoint. If an implementation already has a cross-department flag or endpoint, stop reading/writing the flag and route new creation through the common endpoint; removing legacy storage can be handled with the normal database migration. No separate request table or conversion state is required.

## Open Questions

No product-level questions remain. Existing organization data and deployment constraints determine concrete implementation details.
