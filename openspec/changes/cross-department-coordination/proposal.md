## Why

部门之间需要临时协作，但部门经理不能越过目标部门管理链路直接给外部门员工派任务。需要以经理对经理的协作请求完成授权、承接和结果验收闭环。

## What Changes

- 新增部门经理发起、目标部门经理接受/拒绝的跨部门请求流程。
- 接受后工作进入目标经理的“我的任务—跨部门工作”，由目标部门按普通层级任务链分配与执行。
- 由发起部门经理验收最终结果；支持有原因的退回补充、接受前有原因的撤回及站内提醒。
- 请求接受后 MVP 不支持取消/撤回；创始人不查看或处理跨部门请求；不开放对方部门员工任务明细。

## Capabilities

### New Capabilities
- `cross-department-coordination`: 部门经理之间请求、承接、执行与验收跨部门工作。

### Modified Capabilities
- None.

## Impact

- 关联 `docs/requirements/01-task-creation-and-acceptance.md`。
- 影响 Android 跨部门页面、协作请求状态记录/API、目标部门任务链集成和红点提醒。依赖用户/组织及任务管理基础；不得让创始人或其他角色取得请求访问权。
