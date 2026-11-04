## Context

公司重要事项由管理员在 Web 端维护，所有移动端用户看到相同的已发布内容。它与按用户生成的“公司十大重要任务”是两类数据，管理员不可查看或维护后者。

## Goals / Non-Goals

**Goals:** 支持事项草稿、发布、编辑、下架、人工排序和移动端只读展示；限制已发布项最多 10 条；审计管理变更。

**Non-Goals:** 置顶、任务派发/进度、Web 任务或日志查看、复杂内容管理或数据导出。

## Decisions

- 以独立公司事项记录存储标题、摘要、正文、状态、发布时间/发布人和排序值；不复用 Task。后端已保留附件引用及管理端上传/删除接口，但当前产品流程不要求客户端使用附件。
- 仅管理员 API 可变更；移动端 API 只返回已发布记录，按管理员排序值升序提供最多 10 条供二级列表使用，首页卡片仅展示前 5 条。发布前校验必填字段与上限。
- 公司事项二级界面用列表折叠展示事项，展开后在同一屏显示正文；当前不导航到独立详情页，也不展示或下载附件。未来增加详情页或附件上传/下载时另行确认并更新契约。
- 发布、下架、编辑和排序操作写入管理员审计；草稿不进入移动端列表。
- 管理员预览复用移动端展示字段/布局数据，避免预览口径与线上展示不一致。

## Alternatives Considered

- Reusing Task records was rejected because company-important items have no assignee, progress, hierarchy, or acceptance lifecycle.
- Sorting only by publication time was rejected because administrators manually control display order.

## Risks / Trade-offs

- [并发发布可能突破 10 条上限] → 在服务端事务内校验并更新发布状态。
- [排序编辑误操作可能造成顺序跳变] → 保存整体有序列表并服务端校验无重复/遗漏。
- [现有附件接口超出现行客户端需求] → 保留现有后端模型和接口，但不把附件展示/下载纳入当前移动端或 Web 需求验收；后续通过单独变更决定详情页和附件访问规则。

## Migration Plan

新增事项表与附件引用字段；初始内容由管理员录入。无需迁移既有任务数据。回滚时隐藏移动端入口/API，保留记录和审计数据。

## Open Questions

- 后续如启用附件工作流，须另行确定详情页、文件格式/大小和下载权限；任务材料附件应归入任务模块需求。
