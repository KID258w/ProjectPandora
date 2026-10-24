## 1. 数据模型与迁移

- [x] 1.1 `Department`/`Team` 增加 `is_active`；`User` 增加 `id_number`（身份证号，可空）。
- [x] 1.2 新增 `AdminAuditLog` 模型（actor/module/action/object_type/object_id/result/before_data/after_data/created_at）。
- [x] 1.3 生成迁移并通过 `makemigrations --check --dry-run`。

## 2. 序列化器

- [x] 2.1 部门/团队详情序列化器：含启用状态、负责人与成员、下属团队/成员计数。
- [x] 2.2 员工列表筛选与详情序列化器：身份证号脱敏、只读组织信息。
- [x] 2.3 员工更新序列化器：仅接受 `business_role`/`department_id`/`team_id`/`is_active`。
- [x] 2.4 App `me` 序列化器：GET 脱敏；PATCH 接受姓名/手机号/身份证号。
- [x] 2.5 密码修改、管理概况、审计记录序列化器。

## 3. 视图与路由

- [x] 3.1 部门/团队详情 `GET/PATCH`，PATCH 启停前校验下属团队/成员。
- [x] 3.2 员工列表筛选（姓名/手机号、身份、部门、团队、启用状态）与详情 `GET/PATCH`。
- [x] 3.3 管理概况 `GET /admin/dashboard/` 与审计记录 `GET /admin/audit-logs/`。
- [x] 3.4 App `GET/PATCH /app/me/` 与 `POST /app/me/password/`。

## 4. 业务校验与审计

- [x] 4.1 身份/组织调整复用录入校验：负责人唯一、允许暂时空缺、停用对象不作为候选。
- [x] 4.2 停用校验：部门有启用下属团队/成员不可停用；团队有启用成员不可停用。
- [x] 4.3 App 个人资料多字段事务更新；密码修改校验当前密码与强度。
- [x] 4.4 管理变更写入审计记录并脱敏（不含明文密码、完整身份证号、日志内容）。

## 5. 测试

- [x] 5.1 身份与组织调整、负责人唯一性与空缺、停用候选排除测试。
- [x] 5.2 部门/团队停用校验、员工筛选测试。
- [x] 5.3 App 个人资料事务更新、密码修改、身份证脱敏测试。
- [x] 5.4 审计脱敏、管理员权限隔离测试。

## 6. 文档与验证

- [x] 6.1 更新 `docs/backend/api-design.md`、`docs/backend/database-design.md`。
- [x] 6.2 更新需求文档 `docs/requirements/05-web-admin-console.md`。
- [x] 6.3 全量测试通过、Swagger 契约一致、`openspec validate` 通过。
