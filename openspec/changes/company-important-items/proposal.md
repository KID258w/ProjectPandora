## Why

移动端需要向所有用户展示一致的公司级重要信息，但业务管理员不应因此获得业务任务或日志访问权。将公司重要事项作为独立管理能力，明确其内容、发布和排序边界。

## What Changes

- 新增 Web 管理端公司重要事项管理：新增、编辑、草稿、发布、下架、手动排序、预览及可选附件。
- 移动端所有业务用户读取相同的已发布事项，最多显示 10 条；草稿不可见，达到上限前须先下架已有事项。
- 明确该内容是公司信息，不包含任务负责人、进度、父子任务或验收流程；不提供置顶。
- 与管理员身份/组织管理、移动端首页面板分别集成，不开放 Web 任务或日志管理。

## Capabilities

### New Capabilities
- `company-important-items`: 管理员维护并向全体移动端用户发布公司重要事项。

### Modified Capabilities
- None.

## Impact

- 关联 `docs/requirements/02-home-four-panels.md`、`docs/requirements/05-web-admin-console.md`。
- 影响管理员 Web 表单/API、事项数据与排序、移动端只读列表/详情及管理员操作审计。依赖管理员认证与 RBAC；附件格式/大小在实现设计阶段确定。
