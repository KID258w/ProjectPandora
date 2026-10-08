## Why

Pandora 的主要业务闭环是按企业层级派发工作、由责任人执行并提交成果、再由直接派发者验收。需要把已确认的角色边界、任务树、状态流转与取消规则固化为可实施规格。

## What Changes

- 新增创始人、部门经理、团队长和普通员工的任务创建、分配、本人执行、进展更新、提交与直接派发者验收流程。
- 支持上级任务拆分为责任人明确的子任务；多个列表引用同一任务树，不复制任务。
- 支持任务定义修改、进度历史、退回原因、取消原因、未完成后代级联取消及站内红点提醒。
- 保持任务界面的可见范围：管理者仅查看自己直接派发及本人执行的任务，不在任务界面下钻查看普通员工任务。
- 不包含跨部门协作请求（独立 change）。

## Capabilities

### New Capabilities
- `task-dispatch-and-acceptance`: 层级任务创建、拆分、执行、进展更新、提交、验收与取消。

### Modified Capabilities
- None.

## Impact

- 关联 `docs/requirements/01-task-creation-and-acceptance.md`，并与 `docs/requirements/02-home-four-panels.md`、`03-daily-log-module.md`、`04-month-week-day-work-views.md` 集成。
- 影响 Android 任务页、任务/任务进度/验收历史数据、Django API/RBAC、红点提醒与跨模块任务查询。依赖用户/组织基础能力；跨部门请求保持独立边界。
