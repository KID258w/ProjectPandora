## Why

移动端首页需要在公司层面事项、用户当前任务、用户自行安排的重点任务和近期日志之间提供简洁概览。四个面板来源和排序规则不同，需统一规定数据边界并避免复制或误把个人榜单当公司全局榜单。

## What Changes

- 新增首页四面板聚合：公司十大重要事项、公司十大重要任务、个人十大重点工作、最近十篇日志摘要。
- 公司重要事项读取管理员发布内容；公司十大重要任务按当前用户任务来源优先级生成且排除已完成/已取消任务。
- 个人十大由用户从本人未完成任务中选择、排序；首次使用预填系统生成的公司十大，之后独立维护。
- 日志板块读取本人最近十篇已提交日志摘要；草稿不显示。首页仅摘要与跳转，不执行任务或日志完整操作。

## Capabilities

### New Capabilities
- `home-dashboard-panels`: 按当前用户权限聚合首页四个只读/轻交互信息面板。

### Modified Capabilities
- None.

## Impact

- 关联 `docs/requirements/02-home-four-panels.md` 及任务、日志、公司事项规格。
- 影响 Android 首页、聚合 API、用户个人重点任务排序存储，并依赖 `company-important-items`、`task-dispatch-and-acceptance`、`daily-logs`。不新增独立任务/日志记录。
