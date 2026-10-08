## Context

跨部门工作以请求记录跨越组织边界；目标部门经理接受后自行组织本部门执行。发起方只掌握请求与最终交付，不得访问目标部门内部员工任务明细。

## Goals / Non-Goals

**Goals:** 经理间发送/接收请求、接受/拒绝/撤回、部门内执行、结果提交、发起方验收/退回、权限隔离与提醒。

**Non-Goals:** 员工或团队长发起请求、创始人协调/查看、请求接受后取消、跨部门直接分派员工、请求内透视他部门任务。

## Decisions

- 将 CrossDepartmentRequest 与普通 Task 分开建模；请求接受后建立目标部门的跨部门工作根任务关联，不把请求本身伪装为员工任务。
- 请求状态为待对方接受、办理中、待发起方验收、已关闭、已拒绝、已撤回。拒绝、撤回、结果退回均要求原因。
- 发起后请求内容不可编辑；目标经理接受前发起人可填原因撤回。接受后不提供撤回/取消；新增范围新建请求。
- 目标经理只在本部门层级内创建/分配正式任务；A 仅看到请求概要、状态和 B 提交的结果。
- 用服务端校验双方均为相应部门经理，并确保请求参与者隔离；关键状态变化发应用内红点。

## Alternatives Considered

- Directly assigning tasks to another department's employees was rejected because it bypasses that department's manager and hierarchy.
- Treating the request itself as an employee task was rejected; the request tracks interdepartmental agreement while normal tasks track target-department execution.
- Allowing cancellation after acceptance was rejected for MVP; changed scope requires a new request.

## Risks / Trade-offs

- [请求与普通任务重复/脱节] → 保留明确关联ID和责任边界，普通任务负责目标部门内的执行。
- [结果退回后任务状态不一致] → 将请求退回和目标部门补充状态作为事务化协调动作。
- [越权泄露目标部门工作细节] → A 只能读取请求和提交结果，不授权读取 B 的任务树。

## Migration Plan

新增协作请求及其状态历史，不迁移普通任务。先部署身份组织、任务基础，再启用请求入口；关闭入口可回滚界面而保留记录。

## Open Questions

- 协作请求附件能力及最大大小在实现设计阶段确认；请求优先级字段不纳入 MVP。
