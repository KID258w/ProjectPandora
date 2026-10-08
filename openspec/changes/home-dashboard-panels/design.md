## Context

四个面板各自读取其权威模块数据。首页按用户定制任务/日志摘要，但公司重要事项面向所有业务用户一致展示；任务榜单标题带“公司”但实际是当前用户任务子集。

## Goals / Non-Goals

**Goals:** 汇总四个面板、区分系统榜单与个人排序、显示最近日志摘要、支持摘要进入相应模块。

**Non-Goals:** 首页完整任务操作/日志编辑、管理员维护用户任务榜单、自动把日志关联到任务、任何新增的学习习惯或个人目标。

## Decisions

- 首页聚合 API 基于当前认证用户执行，返回不超过十条的四个面板数据；各项查询复用事项、Task、DailyLog 的权威存储。
- 公司十大重要任务候选是当前用户本人未完成任务，来源顺序为上级分配→本级任务→跨部门工作；同来源按截止时间升序，再按创建时间升序；排除已完成/已取消，最多十条。本人仅负责执行的记录入选，不含仅派发给下属的记录。
- 个人十大重点单独保存 user-task 顺序；首次打开时预填公司任务榜单候选。用户可从本人未完成“我的任务”中选/移出并排序，最多十项；首次预填后不自动随公司榜单更新。完成、取消或不再属于本人任务时移除。
- 公司事项列表读取所有角色一致的已发布内容；最近日志只取当前用户已提交日志按日期倒序前十，草稿和空日期不显示。
- 首页只显示任务标题/状态/截止时间，不显示进度百分比；事项显示序号/标题/发布时间/摘要，日志显示日期/摘要。点击只导航到详情/模块。

## Alternatives Considered

- A single global Company Top Ten task list was rejected because its candidates come from each user's own work.
- Making Personal Top Ten always equal the system list was rejected; after initial prefill, user selection/order is independent.
- Copying tasks and logs into dashboard-specific records was rejected; Home aggregates source-module data.

## Risks / Trade-offs

- [“公司十大任务”可能被误解为全公司统一榜单] → UI/文案说明按当前用户生成；API 按用户鉴权。
- [首次个人预填与后续自动刷新混淆] → 记录初始化状态，仅首次建立，之后只清理无效项。
- [多个数据服务不一致] → 聚合时依赖模块 API/查询服务，不复制核心业务实体。

## Migration Plan

新增每用户重点任务排序与初始化标记；其他面板从前置模块读取。不迁移既有首页数据。若依赖服务尚未上线，可保持首页模块关闭直到接口就绪。

## Open Questions

- 首页面板的实际卡片布局、分页/折叠交互属于低保真原型与 UI 设计阶段确定，不改变本规格数据规则。
