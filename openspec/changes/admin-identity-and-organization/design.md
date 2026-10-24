## Context

Issue #6 已落地账号录入、两端认证与部门/团队/用户列表及创建。Issue #15（功能02）在此基础上补齐管理端详情与启停、员工筛选与身份/组织调整、App 个人资料与密码，以及管理操作审计。产品需求明确 Web 管理端不修改/重置员工密码、不查看业务任务与员工日志。

## Goals / Non-Goals

**Goals:**
- 部门/团队详情查看与更新（名称、说明、启用状态），启停前校验下级对象。
- 员工按姓名/手机号、身份、部门、团队、启用状态筛选；仅调整 `business_role`、`department_id`、`team_id`、`is_active`。
- App 员工本人查看脱敏资料、修改姓名/手机号/身份证号（事务）、修改密码（哈希保存）。
- 管理概况与审计记录接口；审计不保存明文密码、完整身份证号或日志内容。

**Non-Goals:**
- 公司重要事项（`company-important-items` change）。
- Web 端修改/重置员工密码。
- 业务任务、员工日志、AI 会话的查看或操作。
- 组织调动历史表、密码复杂度策略等未确认项。

## Decisions

- `User` 沿用 `username` 作为手机号；`id_number` 存身份证号（Fernet 加密、展示一律脱敏），并加确定性哈希 `id_number_hash` 唯一约束保证身份证号唯一。密码仍用 `User.password` 的 Django 哈希。
- `Department`/`Team` 新增 `is_active`，与 `User.is_active` 一致，采用停用保留历史而非物理删除。
- 负责人唯一性沿用数据库条件唯一约束（仅对 `is_active=True` 生效）；允许组织暂时无负责人，负责人调整不自动指定接任者。
- 员工更新与录入共用同一套 placement 校验（角色→组织形状、负责人唯一、停用对象排除），避免 create/update 规则漂移。
- App 个人资料多字段提交包在 `transaction.atomic` 中，任一字段校验失败整次回滚；手机号修改即修改 `username`。
- 审计只记录管理端成功变更，`before_data`/`after_data` 使用字段白名单并脱敏，绝不写入 password、完整 id_number 或日志内容。

## Data model

- `accounts_user` 增列 `id_number`（`CharField(18, blank=True, default="")`）。
- `accounts_department`、`accounts_team` 各增列 `is_active`（`BooleanField(default=True)`）。
- 新增 `accounts_adminauditlog`：`actor`（FK User，PROTECT）、`module`（users/departments/teams）、`action`（create/update/deactivate/activate）、`object_type`、`object_id`、`result`（success/failure）、`before_data`/`after_data`（JSON，可空，已脱敏）、`created_at`。

## API surface

管理端（`IsAdministrator`）：`GET/PATCH /api/v1/admin/departments/{id}/`、`GET/PATCH /api/v1/admin/teams/{id}/`、`GET /api/v1/admin/users/`（筛选 keyword/business_role/department_id/team_id/is_active）、`GET/PATCH /api/v1/admin/users/{id}/`、`GET /api/v1/admin/dashboard/`、`GET /api/v1/admin/audit-logs/`（start_date/end_date/actor_id/module）。

App 端（`IsBusinessUser`）：`GET/PATCH /api/v1/app/me/`、`POST /api/v1/app/me/password/`。

## Risks / Trade-offs

- [停用负责人导致组织空缺可能违背早期需求场景7] → 以 Issue #15「允许暂时没有负责人」为准，允许空缺；已记录 Open Question 供产品确认。
- [审计快照泄露敏感数据] → 字段白名单 + 脱敏，单元测试覆盖。
- [筛选/更新规则与录入规则漂移] → create/update 复用同一 placement 校验函数。

## Migration Plan

在现有 0003 迁移之后新增一列迁移：`id_number`、`Department/Team.is_active`、`AdminAuditLog`。均为可空或带默认值，向后兼容。回滚方式为反向迁移；不删除历史记录。

## Open Questions

- 需求文档 05 场景7「停用负责人须先更换」与 Issue #15「允许暂时没有负责人」的冲突，需产品最终确认（当前实现遵循 Issue #15）。
