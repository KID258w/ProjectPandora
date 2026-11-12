## Why

首页原“个人十大重点工作”把个人安排混在公司任务中。员工需要一个可以自己创建、跨多日、通过独立月历管理的备忘录模块，并在登录同一账号的设备间同步。当前 Demo 已验证主要交互；本 change 现在覆盖生产后端持久化和 Android App 联调。

## What Changes

- 首页个人面板改为“每日事项”，只预览当前账号日期范围覆盖今天的事项，最多五条；首页不提供创建入口。
- 首页保留四个面板，将“最近日志”卡片改为只展示内容的“每日寄语”，显示按日期稳定轮换的 App 内置短句与颜文字，不响应点击。
- 新增独立的个人事项数据表和 App API，支持本人事项的日期范围查询、创建、详情、编辑、拖动和删除。
- Android App 接入每日事项 API，实现首页预览、月历、日期列表、详情浮窗、表单、确认删除及拖动保存。
- 每日事项只归属当前账号，不复用公司任务、状态、标签关联、日志或操作审计记录。
- 保留原个人重点数据和接口供旧客户端兼容；新 App 不再将其显示为首页面板，不自动转换或删除旧数据。
- 同步 API、数据库、需求和 OpenSpec 文档；Swagger 说明与实际接口一致。

## Non-goals

- 不实现 Web 管理功能、公司任务、日志或公司工作视图功能。
- 不提供自动重复事项、完成状态、优先级、审批、评论、标签关联或共享。
- 不提供个人事项恢复、审计或管理端查询功能。
- 不删除旧个人重点表和接口，不迁移旧个人重点数据到每日事项。

## Impact

- 后端：`backend/apps/home/` 的模型、迁移、服务、序列化、视图、路由及测试；扩展 `GET /api/v1/app/home/`。
- Android：`android/app/src/main/java/com/projectpandora/app/feature/home/`、新增的每日事项页面，以及现有首页导航接入。
- 日历 UI：沿用现有公司月视图的七列日期网格、日期单元格尺寸和样式基线；事项数据和交互仍使用个人事项专属接口与状态。
- 文档：`docs/backend/api-design.md`、`docs/backend/database-design.md`、`docs/requirements/02-home-four-panels.md`、`docs/requirements/09-personal-daily-items.md`。
- OpenSpec：本 change 覆盖需求、Demo 原型以及生产后端和 Android 实现。
