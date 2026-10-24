## Why

Issue #6 已落地账号录入与两端登录；Issue #15（功能02）需要在此基础上补齐 Web 管理端的部门、团队与员工管理，以及员工在 Android App 中维护本人资料与密码的能力，并沉淀管理操作审计。管理员仍不得查看或操作业务任务与员工日志。

## What Changes

- 部门/团队：新增详情查看与更新（名称、说明、启用状态）；启停前校验无启用的下属团队或成员。
- 员工：支持按姓名/手机号、身份、部门、团队和启用状态筛选；详情返回脱敏身份证号；仅允许调整业务身份、部门/团队归属和启用状态（`business_role`、`department_id`、`team_id`、`is_active`）。
- App 个人资料：`GET /app/me/` 返回脱敏资料；`PATCH /app/me/` 员工本人修改姓名、手机号和身份证号，多字段提交须在同一事务中保存，任一字段校验失败整次回滚。
- App 密码：`POST /app/me/password/` 员工本人凭当前密码修改密码，新密码以 Django 哈希保存。
- 管理概况与操作审计：新增管理概况接口与管理员操作记录接口（按时间、管理员、模块筛选），审计记录不保存明文密码、完整身份证号或员工日志内容。
- 数据模型：`User` 增加 `id_number`；`Department`/`Team` 增加 `is_active`；新增 `AdminAuditLog`。
- 同步更新后端 API/数据库文档、需求文档与 OpenSpec change。

## Capabilities

### New Capabilities
- `admin-identity-and-organization`: 管理员身份、部门团队、业务账号与角色、组织范围、App 个人资料与密码、管理操作审计。

### Modified Capabilities
- None.

## Non-goals

- 公司十大重要事项管理（由 `company-important-items` change 负责）。
- Web 管理端修改或重置员工密码（密码修改仅在 App 端由员工本人发起）。
- 查看或操作业务任务、员工日志、AI 会话。
- 组织调动历史表、密码复杂度/锁定策略等实现阶段未确认项。

## Impact

- Web 管理端组织与人员管理页面、员工筛选与启停、管理概况与审计页及对应服务端接口。
- App 个人资料与密码接口。
- 用户、部门、团队和管理员操作记录的数据模型与迁移。
- JWT/RBAC 授权及停用对象候选排除、负责人唯一与空缺、审计脱敏等后端校验。
- 依据 `docs/requirements/05-web-admin-console.md` 与 Issue #15；公司重要事项、任务和员工日志查看不属于本 change。
